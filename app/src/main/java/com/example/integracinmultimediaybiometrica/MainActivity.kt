package com.example.integracinmultimediaybiometrica

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var tvErrorMessage: TextView
    private lateinit var viewFinder: PreviewView

    private var cameraProvider: ProcessCameraProvider? = null
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
        
        btnLogin.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
        
        btnPinManual.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }

        btnSwitchCamera.setOnClickListener {
            toggleCamera()
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
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        viewFinder = findViewById(R.id.viewFinder)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showError("Autenticación interrumpida: $errString")
                    showManualPinOption()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showError("Intento fallido. Verifique su identidad.")
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
        checkCameraPermission()
    }

    private fun toggleCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera() // Reinicia con el nuevo selector
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

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewFinder.surfaceProvider
                }

            try {
                // Desvincular todo antes de volver a vincular
                cameraProvider?.unbindAll()
                // Vincular al ciclo de vida de la actividad (this)
                cameraProvider?.bindToLifecycle(this, currentCameraSelector, preview)
            } catch (exc: Exception) {
                showError("Error al conectar lente: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA // Reset a trasera
        showLockScreen()
    }

    private fun showLockScreen() {
        layoutCamera.visibility = View.GONE
        layoutLock.visibility = View.VISIBLE
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