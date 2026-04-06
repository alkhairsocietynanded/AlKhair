import { createClient } from 'npm:@supabase/supabase-js@2'
import { JWT } from 'npm:google-auth-library@9'

interface WebhookPayload {
  type: 'INSERT' | 'UPDATE' | 'DELETE'
  table: string
  record: Record<string, any>
  schema: 'public'
  old_record: null | Record<string, any>
}

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
)

/**
 * Generate OAuth2 Access Token from individual Firebase credentials.
 * Uses 3 separate secrets (FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY, FIREBASE_PROJECT_ID)
 * instead of a single SERVICE_ACCOUNT JSON blob to avoid JSON parsing issues.
 */
const getAccessToken = ({
  clientEmail,
  privateKey,
}: {
  clientEmail: string
  privateKey: string
}): Promise<string> => {
  return new Promise((resolve, reject) => {
    const jwtClient = new JWT({
      email: clientEmail,
      key: privateKey,
      scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
    })
    jwtClient.authorize((err: any, tokens: any) => {
      if (err) {
        reject(err)
        return
      }
      resolve(tokens!.access_token!)
    })
  })
}

/**
 * Send FCM notification to a single token via HTTP v1 API
 */
async function sendFcmNotification(
  accessToken: string,
  fcmToken: string,
  title: string,
  body: string,
  dataPayload: Record<string, string>,
  projectId: string
): Promise<any> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      message: {
        token: fcmToken,
        notification: { title, body },
        data: dataPayload,
        android: {
          priority: 'high',
          notification: {
            channel_id: dataPayload.channel_id || 'alkhair_general',
            sound: 'default',
          },
        },
      },
    }),
  })

  const responseJson = await response.json()
  console.log(`FCM Response for ...${fcmToken.slice(-10)}:`, JSON.stringify(responseJson))
  return responseJson
}

/**
 * Get target user IDs based on event type
 */
async function getTargetUserIds(
  table: string,
  record: Record<string, any>
): Promise<string[]> {
  let userIds: string[] = []

  if (table === 'homework') {
    const classId = record.class_id
    const shift = record.shift || 'General'
    let query = supabase
      .from('users')
      .select('id')
      .eq('role', 'student')
      .eq('class_id', classId)
      .eq('is_active', true)
    if (shift && shift !== 'All') {
      query = query.eq('shift', shift)
    }
    const { data, error } = await query
    if (error) { console.error('Error fetching target users:', error); return [] }
    userIds = (data || []).map((u: any) => u.id)

  } else if (table === 'announcements') {
    const targetId = record.target_id || 'All'
    if (targetId === 'All') {
      const { data } = await supabase.from('users').select('id').eq('role', 'student').eq('is_active', true)
      userIds = (data || []).map((u: any) => u.id)
    } else {
      const { data } = await supabase.from('users').select('id').eq('role', 'student').eq('class_id', targetId).eq('is_active', true)
      userIds = (data || []).map((u: any) => u.id)
    }

  } else if (table === 'fees') {
    if (record.user_id) userIds = [record.user_id]

  } else if (table === 'chat_messages') {
    const groupType = record.group_type
    const groupId = record.group_id
    const senderId = record.sender_id
    const { data, error } = await supabase.from('users').select('id, role, class_id').eq('is_active', true)
    if (!error && data) {
      if (groupType === 'teachers') {
        userIds = data.filter((u: any) => ['admin', 'teacher'].includes(u.role)).map((u: any) => u.id)
      } else if (groupType === 'class') {
        userIds = data.filter((u: any) => {
          if (u.role === 'admin') return true
          if (u.role === 'teacher' && u.class_id === groupId) return true
          if (u.role === 'student' && u.class_id === groupId) return true
          return false
        }).map((u: any) => u.id)
      }
    }
    // Exclude sender
    userIds = userIds.filter(id => id !== senderId)
    console.log(`Chat targets (excl. sender): ${userIds.length} users`)
  }

  return userIds
}

async function buildNotification(
  table: string,
  record: Record<string, any>
): Promise<{ title: string; body: string; data: Record<string, string> }> {
  switch (table) {
    case 'homework':
      return {
        title: `📚 New Homework: ${record.subject || 'General'}`,
        body: `${record.title || 'New homework assigned'} — Due: ${record.due_date || 'N/A'}`,
        data: {
          type: 'HOMEWORK', click_action: 'HOMEWORK', channel_id: 'alkhair_homework',
          homework_id: record.id || '', class_id: record.class_id || '',
        },
      }
    case 'announcements':
      const content = record.content || ''
      return {
        title: `📢 ${record.title || 'New Announcement'}`,
        body: content.length > 100 ? content.substring(0, 100) + '...' : content,
        data: {
          type: 'ANNOUNCEMENT', click_action: 'ANNOUNCEMENT', channel_id: 'alkhair_announcements',
          announcement_id: record.id || '',
        },
      }
    case 'fees':
      return {
        title: '💰 Fee Update',
        body: `Amount: ₹${record.net_fees || record.base_amount || '0'} — Status: ${record.payment_status || 'Pending'}`,
        data: {
          type: 'FEES', click_action: 'FEES', channel_id: 'alkhair_fees',
          fee_id: record.id || '',
        },
      }
    case 'chat_messages':
      let senderName = 'Someone'
      if (record.sender_id) {
        const { data: senderData } = await supabase.from('users').select('name').eq('id', record.sender_id).single()
        if (senderData?.name) senderName = senderData.name
      }
      const msgText = record.message_text || (record.media_url ? 'Sent an attachment' : 'New message')
      const chatTitle = record.group_type === 'teachers' ? 'Teachers Chat' : 'Class Chat'
      return {
        title: `💬 ${chatTitle}`,
        body: `${senderName}: ${msgText}`,
        data: {
          type: 'CHAT', click_action: 'CHAT', channel_id: 'alkhair_chat',
          group_id: record.group_id || '', group_type: record.group_type || '',
        },
      }
    default:
      return {
        title: 'AlKhair Update',
        body: 'You have a new update.',
        data: { type: 'GENERAL', click_action: 'GENERAL', channel_id: 'alkhair_general' },
      }
  }
}

// ========== MAIN HANDLER ==========
Deno.serve(async (req: Request) => {
  try {
    if (req.method === 'OPTIONS') {
      return new Response('ok', {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
        },
      })
    }

    // ✅ Read individual secrets — NO JSON parsing needed!
    // Required secrets: FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY, FIREBASE_PROJECT_ID
    const clientEmail = Deno.env.get('FIREBASE_CLIENT_EMAIL')
    const privateKeyRaw = Deno.env.get('FIREBASE_PRIVATE_KEY')
    const projectId = Deno.env.get('FIREBASE_PROJECT_ID')

    if (!clientEmail || !privateKeyRaw || !projectId) {
      console.error('Missing Firebase secrets. Need: FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY, FIREBASE_PROJECT_ID')
      return new Response(JSON.stringify({ error: 'Missing Firebase configuration secrets' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }

    // Restore newlines in private key
    // Supabase secret storage may escape \n as literal \\n
    const privateKey = privateKeyRaw.replace(/\\n/g, '\n')
    console.log(`Firebase config OK. email: ${clientEmail}, project: ${projectId}, key: ${privateKey.substring(0, 27)}...`)

    const payload: WebhookPayload = await req.json()
    console.log(`Webhook: ${payload.type} on ${payload.table}`)

    if (payload.type !== 'INSERT') {
      return new Response(
        JSON.stringify({ message: 'Skipped: Not an INSERT event' }),
        { headers: { 'Content-Type': 'application/json' } }
      )
    }

    const userIds = await getTargetUserIds(payload.table, payload.record)
    console.log(`Target users: ${userIds.length}`)

    if (userIds.length === 0) {
      return new Response(JSON.stringify({ message: 'No target users found' }), { headers: { 'Content-Type': 'application/json' } })
    }

    const { data: tokenRows, error: tokenError } = await supabase
      .from('user_fcm_tokens')
      .select('fcm_token')
      .in('user_id', userIds)

    if (tokenError) {
      console.error('Error fetching FCM tokens:', tokenError)
      return new Response(JSON.stringify({ error: 'Failed to fetch FCM tokens' }), { status: 500, headers: { 'Content-Type': 'application/json' } })
    }

    const tokens = (tokenRows || []).map((row: any) => row.fcm_token)
    console.log(`FCM tokens: ${tokens.length}`)

    if (tokens.length === 0) {
      return new Response(JSON.stringify({ message: 'No FCM tokens found' }), { headers: { 'Content-Type': 'application/json' } })
    }

    const { title, body, data } = await buildNotification(payload.table, payload.record)
    console.log(`Sending: "${title}" to ${tokens.length} devices`)

    // Get OAuth2 access token
    let accessToken: string
    try {
      accessToken = await getAccessToken({ clientEmail, privateKey })
      console.log('OAuth2 token obtained ✅')
    } catch (authErr) {
      console.error('OAuth2 token failed:', authErr)
      return new Response(
        JSON.stringify({ error: 'OAuth2 token generation failed', detail: String(authErr) }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const results = await Promise.allSettled(
      tokens.map((token: string) =>
        sendFcmNotification(accessToken, token, title, body, data, projectId)
      )
    )

    const successCount = results.filter((r) => r.status === 'fulfilled').length
    const failCount = results.filter((r) => r.status === 'rejected').length
    console.log(`Done: ${successCount} success, ${failCount} failed`)

    return new Response(
      JSON.stringify({
        message: `Sent ${successCount}/${tokens.length} notifications`,
        results: results.map((r, i) => ({
          token: tokens[i]?.substring(0, 10) + '...',
          status: r.status,
          value: r.status === 'fulfilled' ? (r as PromiseFulfilledResult<any>).value : undefined,
          reason: r.status === 'rejected' ? String((r as PromiseRejectedResult).reason) : undefined,
        })),
      }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    console.error('Edge Function Error:', error)
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    })
  }
})
