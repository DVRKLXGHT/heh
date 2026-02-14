package com.heh.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max

class YoloV10Detector(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val labels = loadLabels(context)

    init {
        val modelBytes = context.assets.open(MODEL_FILE).readBytes()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(4)
        }
        session = env.createSession(modelBytes, options)
        inputName = session.inputNames.first()
    }

    suspend fun detect(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputTensor = preprocess(scaled)
        val result = session.run(mapOf(inputName to inputTensor))

        val detections = parseOutput(
            result[0].value,
            bitmap.width.toFloat(),
            bitmap.height.toFloat()
        )

        inputTensor.close()
        result.close()
        detections
    }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatData = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val px = pixels[y * INPUT_SIZE + x]
                val r = ((px shr 16) and 0xFF) / 255f
                val g = ((px shr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f

                val idx = y * INPUT_SIZE + x
                floatData[idx] = r
                floatData[INPUT_SIZE * INPUT_SIZE + idx] = g
                floatData[2 * INPUT_SIZE * INPUT_SIZE + idx] = b
            }
        }

        return OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOutput(output: Any?, originalW: Float, originalH: Float): List<Detection> {
        val out = output ?: return emptyList()

        val detections = mutableListOf<Detection>()

        when (out) {
            is Array<*> -> {
                val first = out.firstOrNull()
                if (first is Array<*>) {
                    val row0 = first.firstOrNull()
                    if (row0 is FloatArray && row0.size >= 6) {
                        // Expected YOLOv10 export: [1, N, 6] -> x1,y1,x2,y2,score,class
                        first.filterIsInstance<FloatArray>().forEach { row ->
                            val score = row[4]
                            if (score >= CONFIDENCE_THRESHOLD) {
                                detections += Detection(
                                    label = labels.getOrElse(row[5].toInt()) { "obj" },
                                    score = score,
                                    box = RectF(
                                        row[0] * originalW / INPUT_SIZE,
                                        row[1] * originalH / INPUT_SIZE,
                                        row[2] * originalW / INPUT_SIZE,
                                        row[3] * originalH / INPUT_SIZE
                                    )
                                )
                            }
                        }
                    } else if (row0 is FloatArray) {
                        // Fallback for [1,84,8400] (YOLOv8-style) after conversion.
                        val channels = first.filterIsInstance<FloatArray>()
                        if (channels.size >= 6) {
                            val candidates = channels[0].size
                            for (i in 0 until candidates) {
                                val cx = channels[0][i]
                                val cy = channels[1][i]
                                val w = channels[2][i]
                                val h = channels[3][i]
                                var bestCls = 0
                                var bestScore = 0f
                                for (c in 4 until channels.size) {
                                    if (channels[c][i] > bestScore) {
                                        bestScore = channels[c][i]
                                        bestCls = c - 4
                                    }
                                }
                                if (bestScore >= CONFIDENCE_THRESHOLD) {
                                    val left = (cx - w / 2f) * originalW / INPUT_SIZE
                                    val top = (cy - h / 2f) * originalH / INPUT_SIZE
                                    val right = (cx + w / 2f) * originalW / INPUT_SIZE
                                    val bottom = (cy + h / 2f) * originalH / INPUT_SIZE
                                    detections += Detection(
                                        labels.getOrElse(bestCls) { "obj" },
                                        bestScore,
                                        RectF(left, top, right, bottom)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        return nonMaxSuppression(detections, IOU_THRESHOLD).sortedByDescending { it.score }.take(MAX_RESULTS)
    }

    private fun nonMaxSuppression(dets: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = dets.sortedByDescending { it.score }.toMutableList()
        val keep = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            keep += current
            sorted.removeAll { iou(current.box, it.box) > iouThreshold }
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val xA = max(a.left, b.left)
        val yA = max(a.top, b.top)
        val xB = minOf(a.right, b.right)
        val yB = minOf(a.bottom, b.bottom)
        val inter = max(0f, xB - xA) * max(0f, yB - yA)
        val union = (a.width() * a.height()) + (b.width() * b.height()) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun loadLabels(context: Context): List<String> = try {
        context.assets.open(LABELS_FILE).bufferedReader().readLines().filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    data class Detection(val label: String, val score: Float, val box: RectF)

    companion object {
        private const val MODEL_FILE = "yolov10n.onnx"
        private const val LABELS_FILE = "coco.txt"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.5f
        private const val MAX_RESULTS = 25
    }
}
