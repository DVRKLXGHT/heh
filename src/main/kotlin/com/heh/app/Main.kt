package com.heh.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val MODEL_DIR = ".ip-stream-ai-vision"
private const val PROTOTXT_FILE = "MobileNetSSD_deploy.prototxt"
private const val MODEL_FILE = "MobileNetSSD_deploy.caffemodel"

private val classNames = listOf(
    "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat",
    "chair", "cow", "diningtable", "dog", "horse", "motorbike", "person", "pottedplant",
    "sheep", "sofa", "train", "tvmonitor"
)

fun main() {
    nu.pattern.OpenCV.loadLocally()
    SwingUtilities.invokeLater { VisionFrame().isVisible = true }
}

class VisionFrame : JFrame("IP Stream AI Vision (Kotlin)") {
    private val imageLabel = JLabel("Enter an IP stream URL and press Start", SwingConstants.CENTER)
    private val urlField = JTextField("http://192.168.1.2:8080/video")
    private val statusLabel = JLabel("Idle")

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(980, 700)
        layout = BorderLayout(10, 10)

        val controls = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        val buttonsPanel = JPanel().apply {
            val startButton = JButton("Start")
            val stopButton = JButton("Stop")

            startButton.addActionListener { startProcessing() }
            stopButton.addActionListener { stopProcessing() }

            add(startButton)
            add(stopButton)
        }

        controls.add(urlField, BorderLayout.CENTER)
        controls.add(buttonsPanel, BorderLayout.EAST)

        imageLabel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        statusLabel.border = BorderFactory.createEmptyBorder(0, 10, 10, 10)

        add(controls, BorderLayout.NORTH)
        add(imageLabel, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(null)
    }

    private fun startProcessing() {
        val streamUrl = urlField.text.trim()
        if (streamUrl.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please provide a valid stream URL")
            return
        }

        stopProcessing()
        updateStatus("Loading model...")

        processingJob = coroutineScope.launch {
            val detector = try {
                MobileNetSsdDetector.fromCacheOrDownload()
            } catch (e: Exception) {
                updateStatus("Model setup failed: ${e.message}")
                return@launch
            }

            val capture = VideoCapture(streamUrl)
            if (!capture.isOpened) {
                updateStatus("Unable to open stream: $streamUrl")
                capture.release()
                return@launch
            }

            updateStatus("Connected. Running AI inference...")
            val frame = Mat()

            while (isActive) {
                val hasFrame = capture.read(frame)
                if (!hasFrame || frame.empty()) {
                    updateStatus("No frame received. Retrying...")
                    delay(120)
                    continue
                }

                val detections = detector.detect(frame)
                drawDetections(frame, detections)
                updateImage(frame.toBufferedImage())
            }

            frame.release()
            capture.release()
            updateStatus("Stopped")
        }
    }

    private fun stopProcessing() {
        processingJob?.cancel()
        processingJob = null
    }

    private fun updateImage(image: BufferedImage) {
        SwingUtilities.invokeLater {
            imageLabel.icon = ImageIcon(image)
            imageLabel.text = null
        }
    }

    private fun updateStatus(text: String) {
        SwingUtilities.invokeLater { statusLabel.text = text }
    }

    override fun dispose() {
        stopProcessing()
        coroutineScope.cancel()
        super.dispose()
    }
}

data class Detection(val label: String, val confidence: Float, val box: Rect)

class MobileNetSsdDetector private constructor(private val net: Net) {

    fun detect(frame: Mat, confidenceThreshold: Float = 0.5f): List<Detection> {
        val blob = Dnn.blobFromImage(
            frame,
            0.007843,
            Size(300.0, 300.0),
            Scalar(127.5, 127.5, 127.5),
            false,
            false
        )

        net.setInput(blob)
        val output = net.forward()
        val detectionsMat = output.reshape(1, (output.total() / 7).toInt())

        val cols = frame.cols()
        val rows = frame.rows()
        val detections = mutableListOf<Detection>()

        for (i in 0 until detectionsMat.rows()) {
            val confidence = detectionsMat.get(i, 2)[0].toFloat()
            if (confidence < confidenceThreshold) continue

            val classId = detectionsMat.get(i, 1)[0].toInt()
            val left = (detectionsMat.get(i, 3)[0] * cols).toInt().coerceAtLeast(0)
            val top = (detectionsMat.get(i, 4)[0] * rows).toInt().coerceAtLeast(0)
            val right = (detectionsMat.get(i, 5)[0] * cols).toInt().coerceAtMost(cols - 1)
            val bottom = (detectionsMat.get(i, 6)[0] * rows).toInt().coerceAtMost(rows - 1)

            if (right <= left || bottom <= top) continue
            val label = classNames.getOrElse(classId) { "class-$classId" }
            detections.add(Detection(label, confidence, Rect(left, top, right - left, bottom - top)))
        }

        blob.release()
        output.release()
        detectionsMat.release()
        return detections
    }

    companion object {
        fun fromCacheOrDownload(): MobileNetSsdDetector {
            val modelDir = File(System.getProperty("user.home"), MODEL_DIR).apply { mkdirs() }
            val prototxt = File(modelDir, PROTOTXT_FILE)
            val model = File(modelDir, MODEL_FILE)

            if (!prototxt.exists()) {
                URL("https://raw.githubusercontent.com/chuanqi305/MobileNet-SSD/master/$PROTOTXT_FILE").openStream().use { input ->
                    prototxt.outputStream().use { output -> input.copyTo(output) }
                }
            }

            if (!model.exists()) {
                URL("https://github.com/chuanqi305/MobileNet-SSD/raw/master/$MODEL_FILE").openStream().use { input ->
                    model.outputStream().use { output -> input.copyTo(output) }
                }
            }

            val net = Dnn.readNetFromCaffe(prototxt.absolutePath, model.absolutePath)
            return MobileNetSsdDetector(net)
        }
    }
}

private fun drawDetections(frame: Mat, detections: List<Detection>) {
    for (detection in detections) {
        Imgproc.rectangle(frame, detection.box, Scalar(0.0, 220.0, 0.0), 2)
        val text = "%s %.1f%%".format(detection.label, detection.confidence * 100f)
        val textPoint = Point(detection.box.x.toDouble(), (detection.box.y - 8).coerceAtLeast(20).toDouble())
        Imgproc.putText(frame, text, textPoint, Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, Scalar(20.0, 20.0, 255.0), 2)
    }
}

private fun Mat.toBufferedImage(): BufferedImage {
    val matOfByte = MatOfByte()
    Imgcodecs.imencode(".jpg", this, matOfByte)
    return ByteArrayInputStream(matOfByte.toArray()).use { input ->
        ImageIO.read(input)
    }.also {
        matOfByte.release()
    }
}
