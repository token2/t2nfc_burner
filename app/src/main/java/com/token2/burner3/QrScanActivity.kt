package com.token2.burner3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * A focused, single-purpose QR reader. It returns the *raw* string of the first
 * QR code it sees and closes — deliberately doing no interpretation of its own,
 * so that all "is this the right code?" logic lives in one place
 * ([com.token2.burner3.otp.SeedInput]) and stays consistent between scanning and
 * manual entry.
 */
class QrScanActivity : ComponentActivity() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var delivered = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "Camera access is needed to scan the QR code.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private lateinit var previewView: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        setContentView(previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyze) }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private val scanner = BarcodeScanning.getClient()

    @androidx.camera.core.ExperimentalGetImage
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || delivered) {
            proxy.close(); return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val qr = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE || it.rawValue != null }
                val value = qr?.rawValue
                if (!value.isNullOrBlank() && !delivered) {
                    delivered = true
                    setResult(RESULT_OK, intent.putExtra(EXTRA_RESULT, value))
                    finish()
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    companion object {
        const val EXTRA_RESULT = "qr_result"
    }
}
