package com.example.integracinmultimediaybiometrica

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
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

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Launcher para la autenticación fallback por PIN del sistema
    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onAuthSuccess()
        } else {
            showError("Autenticación por PIN fallida o cancelada")
        }
    }

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
            launchDeviceCredentialFallback()
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

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showError("Error: $errString")
                    showManualPinOption()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showError("Huella no reconocida. Intente de nuevo.")
                }
            })

        // Configuración para permitir Biometría y Credenciales del Dispositivo (PIN/Patrón)
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación de Seguridad")
            .setSubtitle("Identifíquese para acceder a la terminal")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    /**
     * Lanza el flujo de PIN/Patrón del sistema como alternativa robusta.
     * Esto asegura que el teclado numérico o el patrón se desplieguen correctamente
     * usando la interfaz nativa del sistema operativo.
     */
    private fun launchDeviceCredentialFallback() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Autenticación Requerida",
            "Por favor, ingrese su PIN o Patrón de seguridad"
        )
        if (intent != null) {
            deviceCredentialLauncher.launch(intent)
        } else {
            // Si no hay PIN configurado en el dispositivo
            showError("No hay un PIN o Patrón configurado en este dispositivo.")
        }
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
                showError("Error al conectar lente: ${exc.message}")
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
                    showError("Error al capturar: ${exc.message}")
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
                .setMessage("Foto capturada con éxito para validación.")
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