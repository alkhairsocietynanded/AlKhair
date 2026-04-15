package com.aewsn.alkhair.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {

    private const val TAG = "ImageCompressor"
    private const val MAX_DIMENSION = 1280

    /**
     * Compresses the image from the given Uri to WebP format.
     * Calculates appropriate inSampleSize to prevent OutOfMemoryError.
     * @return ByteArray containing the compressed WebP image or null if failed.
     */
    suspend fun compressImageFromUri(
        context: Context,
        uri: Uri,
        quality: Int = 75
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 1. Decode bounds only to calculate sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION)

            // 3. Decode with inSampleSize
            options.inJustDecodeBounds = false
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: return@withContext null

            // 4. Compress to WebP
            val outputStream = ByteArrayOutputStream()
            // using WEBP which is universally available since Android 4.0; lossless/lossy splits were in API 30+ 
            // but WEBP covers default lossy compression standard in minimum sdk (26)
            @Suppress("DEPRECATION")
            val compressed = bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
            
            bitmap.recycle() // free memory immediately
            
            if (compressed) {
                val bytes = outputStream.toByteArray()
                Log.d(TAG, "Compressed image to WebP, new size: ${bytes.size} bytes")
                return@withContext bytes
            } else {
                Log.e(TAG, "Failed to compress bitmap")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}", e)
            return@withContext null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
