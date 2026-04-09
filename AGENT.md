# 🏛 AlKhair Master Guide (AGENT.md)

This document is the **Definitive Technical Reference** for the AlKhair Android project. It provides exhaustive detail on every architectural component, synchronization logic, and implementation pattern, enabling developers and AI agents to understand the entire project context from a single file.

---

## 🏗 High-Level Architecture (MVVM + Clean)

The project follows a strict **Offline-First** architecture with **Room** as the Single Source of Truth (SSOT).

### Structural Layers:
1.  **UI Layer**: Uses **ViewBinding** in Fragments. Fragments observe data streams (`Flow`/`StateFlow`) from ViewModels.
2.  **ViewModel Layer**: Manages UI state using a sealed `UiState<T>`. Leverages Hilt for dependency injection.
3.  **Manager Layer (RepoManagers)**: Singletons that orchestrate data flow between Room (Local) and Supabase (Remote).
4.  **Network/Remote Layer**: Uses **Supabase SDK** for Auth, Database (PostgREST), Storage, and Realtime. **Retrofit** is used for legacy or third-party n8n APIs.
5.  **Synchronization Layer**: `AppDataSyncManager` handles bulk periodic syncs, while `WorkManager` handles background uploads of local changes.

---

## 🔁 Synchronization Engine (`AppDataSyncManager`)

The sync engine uses a multi-stage approach to ensure data consistency and referential integrity.

### Detailed Sync Protocol (5 Steps):
1.  **Metadata (Sequential)**: 
    - Syncs `Classes` and `Divisions`. 
    - *Why?* Most other tables (Users, Homework, etc.) have foreign keys or logical dependencies on specific Class/Division IDs.
2.  **Global Level-1 Parallel Jobs**: 
    - Syncs `Subjects`, `Exams`, `AppConfig`, and `Timetable`. 
    - These are lightweight metadata tables needed for rendering lists.
3.  **Role-Based Deep Sync**:
    - **ADMIN Strategy**: 
        - Parallel sync of ALL tables: `Users`, `Fees`, `Salary`, `Homework`, `Announcements`, `Attendance`, `Leaves`, `Syllabus`, `StudyMaterials`.
    - **TEACHER Strategy**: 
        - Personal `Salary` sync.
        - `syncClassStudents` (awaiting result) → followed by `syncLeavesForClass`.
        - Parallel sync of class-specific `Homework`, `Attendance`, `Fees`, `Syllabus`, and `StudyMaterials` (filtered by `classId` and `shift`).
    - **STUDENT Strategy**: 
        - `syncUserProfile` (awaiting result).
        - Parallel sync of personal `Fees`, `Attendance`, `Leaves`, and class-specific `Homework`/`Announcements`.
4.  **Tombstone Delete Sync**: 
    - Fetches records from the `deleted_records` table in Supabase updated after `last_sync_timestamp`.
    - Purges local Room records corresponding to the returned `record_id` and `type`.
5.  **Integrity Sync (Results)**: 
    - Syncs `Results` only after all dependencies (Users, Subjects, Exams) are local.

### Key Logic:
- **Mutex Guard**: Uses `kotlinx.coroutines.sync.Mutex` to ensure `syncAllData` is never re-entered while running.
- **Wipe Detection**: If `currentUser` profile is missing locally during sync, it resets the timestamp to `0`, forcing a full re-sync (handles post-migration or clearing app data).

---

## 🧩 Key Implementation Patterns

### 1. Repository Manager Blueprint (`*RepoManager`)
Standardizes the "Local-First" data flow:
```kotlin
// Example Pattern
fun observeLocal(): Flow<List<T>> = localRepo.getAll() // Direct from Room

suspend fun create(item: T) {
    localRepo.insert(item.copy(isSynced = false)) // UI updates via Flow
    enqueueWorker() // WorkManager handles remote sync
}

suspend fun sync(after: Long) {
    val remoteData = remoteRepo.fetch(after)
    localRepo.bulkInsert(remoteData.map { it.copy(isSynced = true) })
}
```

### 2. WhatsApp-style Chat System
Optimized for real-time performance and data efficiency.
-   **Real-time Handlers**:
    -   `INSERT`: Automatically inserts incoming remote message into Room.
    -   `DELETE`: Purges local message entirely to support "Delete for Everyone".
-   **Media Download-on-Demand**:
    -   Remote `media_url` is stored, but file is not downloaded automatically.
    -   User clicks download → `ChatRepoManager.downloadMedia(message)` → `StorageManager.downloadPublicFile` saves to `cacheDir/chat_media/`.
    -   Room `local_uri` is updated.
-   **Tombstone Catch-up**: `syncGroupMessages` fetches missed deletions (`fetchDeletedMessageIds`) for devices that were offline during realtime events.

### 3. Storage Management (`StorageManager`)
-   **Byte-based Upload**: Encodes URIs to bytes in the UI thread/Activity result and passes them to `uploadBytes()` for maximum reliability.
-   **Caching**: Uses `context.cacheDir` for all chat media to allow OS-managed cleanup if space is low, but persists links in Room for fast retrieval.

---

## ⚙️ Background Synchronization (`WorkManager`)

Workers ensure that no offline mutation is lost.
-   **Unique Work Policies**: Uses `ExistingWorkPolicy.APPEND_OR_REPLACE` to queue updates.
-   **Retry Strategy**: `Result.retry()` is returned on network failure with an `EXPONENTIAL` backoff.
-   **Specific Work Names**:
    -   `"ChatUploadWork"`: For messages.
    -   `"HomeworkUploadWork"`: For assignments.
    -   `"AttendanceUploadWork"`: For staff/admin tagging.

---

## 🔐 Auth & Security Protocols

### Session Persistence:
-   Supabase Auth handles the network session.
-   `current_user_uid` is mirrored in `AppDataStore` (Jetpack DataStore) for synchronous checks during app launch before Supabase init completes.

### Cloud Security & FCM:
-   **Edge Functions**: `send-notification` triggers on database webhooks.
-   **Secrets**: Firebase Service Account is NOT a JSON string in secrets. It is split into:
    -   `FIREBASE_PROJECT_ID`
    -   `FIREBASE_CLIENT_EMAIL`
    -   `FIREBASE_PRIVATE_KEY`
-   **Reasoning**: Prevents JSON parsing errors in the Deno environment during edge function execution.

---

## 📂 Project Structure Details

-   `com.aewsn.alkhair`
    -   `di`: Hilt-based dependency injection.
        -   `AppModule`: Room DB, DAOs, DataStore, Gson.
        -   `SupabaseModule`: Supabase Clients (Auth, Storage, Realtime, Functions).
    -   `data/local`: Room `Entity` (SQL schemas) and `Dao` (Queries).
    -   `data/remote`: SDK-level interaction logic.
    -   `data/manager`: High-level orchestration (RepoManagers).
    -   `ui`: XML-based layouts, ViewBinding, and Fragment navigation.
    -   `utils**: Shared constants (e.g., `Roles.ADMIN`, `Roles.TEACHER`) and common extensions.

---

## 🚀 Workflows for AI Agents

### Common Scenario: "Add a New Field to X"
1.  **Room Entity**: Update the class in `data/local/entities`.
2.  **Supabase Table**: Update the table schema via SQL.
3.  **Migration**: Increment `AppDatabase` version. (Destructive migration `true` for dev phase).
4.  **Sync**: Ensure the `RepoManager.sync()` mapping correctly hydates the new field.

### Common Scenario: "Add a New Syncable Feature"
1.  Create `Entity`, `Dao`, `RemoteRepository`, `RepoManager`, and `UploadWorker`.
2.  Register the `Dao` in `AppModule`.
3.  Add the mapping in `AppDataSyncManager.syncDeletions` for the Tombstone pattern.
4.  Add the role-based sync call in `AppDataSyncManager.performSync`.

---

## 📝 Database Schema Blueprints (Logical)

### `chat_messages`
-   `id` (PK), `sender_id`, `group_id`, `message_text`, `media_url`, `media_type`, `updated_at`, `is_synced`, `local_uri`.

### `deleted_records` (Tombstone)
-   `id` (PK), `record_id`, `type` (e.g., 'homework', 'attendance'), `deleted_at` (Timestamp).

---

## 🎨 UI/UX Standards

-   **State Management**: Use `UiState.Loading`, `UiState.Success<T>`, and `UiState.Error`.
-   **Binding Lifecycle**: Always set `_binding = null` in `onDestroyView()` to prevent memory leaks.
-   **Loading Overlays**: Use common XML layouts for consistent loading states.
