package com.heh.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.heh.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val streamReader = IpMjpegStreamReader()
    private var detector: YoloV10Detector? = null
    private var streamJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.connectButton.setOnClickListener {
            if (streamJob?.isActive == true) {
                stopStream()
            } else {
                startStream()
            }
        }
    }

    private fun startStream() {
        val url = binding.urlEditText.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            binding.statusText.text = "Enter stream URL"
            return
        }

        binding.connectButton.text = "Stop"
        binding.statusText.text = "Loading YOLOv10 model..."

        streamJob = lifecycleScope.launch {
            try {
                detector = detector ?: withContext(Dispatchers.Default) { YoloV10Detector(this@MainActivity) }
                binding.statusText.text = "Running detection..."

                var frameCount = 0
                var windowStart = System.currentTimeMillis()

                streamReader.start(url) { bitmap ->
                    val activeDetector = detector ?: return@start
                    val detections = activeDetector.detect(bitmap)
                    val annotated = drawDetections(bitmap, detections)

                    frameCount++
                    val elapsed = System.currentTimeMillis() - windowStart
                    if (elapsed >= 1000) {
                        val fps = frameCount * 1000f / elapsed
                        withContext(Dispatchers.Main) {
                            binding.statusText.text = String.format(Locale.US, "Running %.1f FPS | %d objects", fps, detections.size)
                        }
                        frameCount = 0
                        windowStart = System.currentTimeMillis()
                    }

                    withContext(Dispatchers.Main) {
                        binding.frameView.setImageBitmap(annotated)
                    }
                }
            } catch (e: Exception) {
                binding.statusText.text = "Error: ${e.message}"
                stopStream()
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        binding.connectButton.text = "Start"
        if (!isFinishing) {
            binding.statusText.text = "Stopped"
        }
    }

    private fun drawDetections(
        bitmap: Bitmap,
        detections: List<YoloV10Detector.Detection>
    ): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            style = Paint.Style.FILL
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }

        detections.forEach { det ->
            canvas.drawRect(det.box, boxPaint)
            canvas.drawText("${det.label} ${(det.score * 100).toInt()}%", det.box.left, det.box.top - 8f, textPaint)
        }

        return mutable
    }

    override fun onDestroy() {
        stopStream()
        super.onDestroy()
    }
}
