package com.heh.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

class IpMjpegStreamReader {
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()

    suspend fun start(url: String, onFrame: suspend (Bitmap) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to open stream: HTTP ${response.code}")
        }

        response.body?.byteStream()?.use { input ->
            val buffer = ByteArray(8192)
            val frameBytes = ByteArrayOutputStream()
            var inFrame = false
            var prev = -1

            while (isActive) {
                val read = input.read(buffer)
                if (read == -1) break

                for (i in 0 until read) {
                    val cur = buffer[i].toInt() and 0xFF

                    if (!inFrame && prev == 0xFF && cur == 0xD8) {
                        inFrame = true
                        frameBytes.reset()
                        frameBytes.write(0xFF)
                        frameBytes.write(0xD8)
                    } else if (inFrame) {
                        frameBytes.write(cur)
                        if (prev == 0xFF && cur == 0xD9) {
                            val bytes = frameBytes.toByteArray()
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { onFrame(it) }
                            inFrame = false
                            frameBytes.reset()
                        }
                    }

                    prev = cur
                }
            }
        }
    }
}
