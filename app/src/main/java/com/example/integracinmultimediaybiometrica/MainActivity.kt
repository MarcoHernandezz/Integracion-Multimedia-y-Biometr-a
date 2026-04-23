package com.example.integracinmultimediaybiometrica

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var btnLogin: Button
    private lateinit var btnPinManual: Button
    private lateinit var tvErrorMessage: TextView

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
            // Simulación de entrada de PIN manual
            navigateToCamera()
        }
    }

    private fun setupViews() {
        btnLogin = findViewById(R.id.btnLogin)
        btnPinManual = findViewById(R.id.btnPinManual)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)

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
                    showError("Autenticación cancelada o no disponible")
                    showManualPinOption()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    navigateToCamera()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showError("Intento fallido")
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación de Seguridad")
            .setSubtitle("Identifíquese para acceder a la terminal")
            .setNegativeButtonText("Cancelar")
            .build()
    }

    private fun showError(message: String) {
        tvErrorMessage.text = message
        tvErrorMessage.visibility = View.VISIBLE
    }

    private fun showManualPinOption() {
        btnPinManual.visibility = View.VISIBLE
    }

    private fun navigateToCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
        finish()
    }
}