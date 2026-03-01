package com.eni.magnifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("activity_main", "layout", packageName))

        viewFinder = findViewById(resources.getIdentifier("viewFinder", "id", packageName))
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }

        findViewById<Button>(resources.getIdentifier("btnFlipCamera", "id", packageName)).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }

        findViewById<Button>(resources.getIdentifier("btnResetCamera", "id", packageName)).setOnClickListener {
            emergencyCameraReset()
        }

        setupPinchToZoom()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview)
            } catch (exc: Exception) {
                Log.e("ENI_App", "Binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupPinchToZoom() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoom: Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                camera?.cameraControl?.setZoomRatio(currentZoom * detector.scaleFactor)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(this, listener)
        viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun emergencyCameraReset() {
        Toast.makeText(this, "ENI is forcing a hardware release...", Toast.LENGTH_SHORT).show()
        try {
            cameraProvider?.unbindAll()
            viewFinder.postDelayed({ startCamera() }, 500)
        } catch (e: Exception) {
            Log.e("ENI_App", "Reset failed", e)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
