package com.example.integracinmultimediaybiometrica

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var layoutLock: View
    private lateinit var layoutCamera: View
    private lateinit var btnLogin: Button
    private lateinit var btnPinManual: Button
    private lateinit var btnSwitchCamera: Button
    private lateinit var btnStopCamera: Button
    private lateinit var btnCapture: FloatingActionButton
    private lateinit var tvErrorMessage: TextView
    private lateinit var viewFinder: PreviewView

    // Teclado Custom Views
    private lateinit var layoutCustomKeyboard: View
    private lateinit var tvPinDisplay: TextView
    private lateinit var btnKeyDone: Button
    private lateinit var btnKeyDelete: Button
    
    private var currentPin = ""

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            showLockScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        setupViews()
        setupBiometrics()
        setupCustomKeyboard()
        
        btnLogin.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
        
        btnPinManual.setOnClickListener {
            showCustomKeyboard()
        }

        btnSwitchCamera.setOnClickListener {
            toggleCamera()
        }

        btnCapture.setOnClickListener {
            takePhoto()
        }

        btnStopCamera.setOnClickListener {
            stopCamera()
        }
    }

    private fun setupViews() {
        layoutLock = findViewById(R.id.layoutLock)
        layoutCamera = findViewById(R.id.layoutCamera)
        btnLogin = findViewById(R.id.btnLogin)
        btnPinManual = findViewById(R.id.btnPinManual)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnStopCamera = findViewById(R.id.btnStopCamera)
        btnCapture = findViewById(R.id.btnCapture)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        viewFinder = findViewById(R.id.viewFinder)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupCustomKeyboard() {
        layoutCustomKeyboard = findViewById(R.id.layoutCustomKeyboard)
        tvPinDisplay = findViewById(R.id.tvPinDisplay)
        btnKeyDone = findViewById(R.id.btnKeyDone)
        btnKeyDelete = findViewById(R.id.btnKeyDelete)

        btnKeyDone.setOnClickListener {
            if (currentPin.isNotEmpty()) {
                onAuthSuccess()
            }
        }

        btnKeyDelete.setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updatePinDisplay()
            }
        }
    }

    // Método invocado por android:onClick="onKeyClick" en el XML
    fun onKeyClick(view: View) {
        if (view is Button) {
            if (currentPin.length < 8) { // Límite razonable
                currentPin += view.text.toString()
                updatePinDisplay()
            }
        }
    }

    private fun updatePinDisplay() {
        tvPinDisplay.text = "*".repeat(currentPin.length)
    }

    private fun showCustomKeyboard() {
        currentPin = ""
        updatePinDisplay()
        layoutCustomKeyboard.visibility = View.VISIBLE
        btnLogin.visibility = View.GONE
        btnPinManual.visibility = View.GONE
    }

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showError("Biometría no disponible")
                    showManualPinOption()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showError("Huella no reconocida.")
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación de Seguridad")
            .setSubtitle("Identifíquese para acceder a la terminal")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    private fun onAuthSuccess() {
        layoutLock.visibility = View.GONE
        layoutCamera.visibility = View.VISIBLE
        layoutCustomKeyboard.visibility = View.GONE
        checkCameraPermission()
    }

    private fun toggleCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewFinder.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, currentCameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                showError("Error al conectar lente")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            cacheDir,
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    showError("Error al capturar")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    showValidationDialog(savedUri)
                }
            }
        )
    }

    private fun showValidationDialog(uri: Uri) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Validación de Identidad")
                .setMessage("Foto capturada con éxito.")
                .setPositiveButton("Finalizar") { dialog, _ ->
                    dialog.dismiss()
                    stopCamera()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        showLockScreen()
    }

    private fun showLockScreen() {
        layoutCamera.visibility = View.GONE
        layoutLock.visibility = View.VISIBLE
        layoutCustomKeyboard.visibility = View.GONE
        btnLogin.visibility = View.VISIBLE
        tvErrorMessage.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvErrorMessage.text = message
        tvErrorMessage.visibility = View.VISIBLE
    }

    private fun showManualPinOption() {
        btnPinManual.visibility = View.VISIBLE
    }
}