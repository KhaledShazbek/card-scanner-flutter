package com.nateshmbhat.card_scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.nateshmbhat.card_scanner.models.CardDetails
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias onCardScanned = (cardDetails: CardDetails) -> Unit

class CardScannerCameraActivity : AppCompatActivity() {
  private var preview: Preview? = null
  private var imageCapture: ImageCapture? = null
  private var imageAnalyzer: ImageAnalysis? = null
  private var camera: Camera? = null
  private var previewUseCase: Preview? = null;
  private var cameraProvider: ProcessCameraProvider? = null
  private var cameraSelector: CameraSelector? = null
  private var textRecognizer: TextRecognizer? = null
  private var analysisUseCase: ImageAnalysis? = null
  private lateinit var cameraExecutor: ExecutorService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    cameraExecutor = Executors.newSingleThreadExecutor()

    // Request camera permissions
    if (allPermissionsGranted()) {
      startCamera()
    } else {
      ActivityCompat.requestPermissions(
              this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }
  }

  private fun startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener(Runnable {
      this.cameraProvider = cameraProviderFuture.get()
      this.cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

      try {
        bindAllCameraUseCases()
      } catch (exc: Exception) {
        Log.e(TAG, "Use case binding failed", exc)
      }
    }, ContextCompat.getMainExecutor(this))
  }


  private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
  }

  override fun onRequestPermissionsResult(
          requestCode: Int, permissions: Array<String>, grantResults:
          IntArray) {
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
      if (allPermissionsGranted()) {
        startCamera()
      } else {
        Toast.makeText(this,
                "Permissions not granted by the user.",
                Toast.LENGTH_SHORT).show()
        finish()
      }
    }
  }

  private fun bindAllCameraUseCases() {
    bindPreviewUseCase()
    bindAnalysisUseCase()
  }

  private fun bindPreviewUseCase() {
    if (previewUseCase != null) {
      cameraProvider?.unbind(previewUseCase)
    }
    previewUseCase = Preview.Builder().build()
    val previewView = findViewById<PreviewView>(R.id.viewFinder)
    previewUseCase!!.setSurfaceProvider(previewView.createSurfaceProvider())
    cameraProvider?.bindToLifecycle( /* lifecycleOwner= */this, cameraSelector!!, previewUseCase)
  }

  private fun bindAnalysisUseCase() {
    if (cameraProvider == null) {
      return
    }
    if (analysisUseCase != null) {
      cameraProvider?.unbind(analysisUseCase)
    }
    textRecognizer?.close()
    textRecognizer = TextRecognition.getClient()

    val analysisUseCase = ImageAnalysis.Builder().build()
            .also {
              it.setAnalyzer(cameraExecutor, TextRecognitionProcessor { cardDetails ->
                CardScannerPlugin.channel.invokeMethod("card_details", cardDetails.toString())
                finish()
                Log.d(TAG, "Card recognized : $cardDetails")
              })
            }
    cameraProvider!!.bindToLifecycle( /* lifecycleOwner= */this, cameraSelector!!, analysisUseCase)
  }

  companion object {
    private const val TAG = "CameraXBasic"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
  }

  override fun onResume() {
    super.onResume()
    bindAllCameraUseCases()
  }

  override fun onPause() {
    super.onPause()
    textRecognizer?.close()
  }

  override fun onDestroy() {
    super.onDestroy()
    textRecognizer?.close()
  }
}