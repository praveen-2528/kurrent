package com.example.kurrent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.kurrent.MainActivity
import com.example.kurrent.R
import com.example.kurrent.data.AuthManager
import com.example.kurrent.data.SampleDataRepository
import com.example.kurrent.data.network.ApiClient
import com.example.kurrent.databinding.ActivityAuthBinding
import com.example.kurrent.model.RegisterWithOtpRequest
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    private var pendingEmail: String = ""
    private var pendingName: String = ""
    private var pendingExam: String = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AuthManager.init(this)
        SampleDataRepository.init(this)

        // If already logged in, jump directly to MainActivity
        if (AuthManager.isLoggedIn()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutAuthContainer) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                top = (36 * resources.displayMetrics.density).toInt() + statusBars.top,
                bottom = (40 * resources.displayMetrics.density).toInt() + navBars.bottom
            )
            windowInsets
        }
    }

    private fun setupListeners() {
        // Tab switcher
        binding.tabSignIn.setOnClickListener { showSignInTab() }
        binding.tabSignUp.setOnClickListener { showSignUpTab() }

        // Exam chip choice in Sign Up
        binding.chipGroupSignUpExams.setOnCheckedStateChangeListener { _, checkedIds ->
            pendingExam = when {
                checkedIds.contains(R.id.chipSignUpUpsc) -> "UPSC"
                checkedIds.contains(R.id.chipSignUpSsc) -> "SSC"
                checkedIds.contains(R.id.chipSignUpIbps) -> "IBPS"
                else -> "ALL"
            }
        }

        // 1. Submit Sign In
        binding.btnSubmitSignIn.setOnClickListener {
            val email = binding.etLoginEmail.text?.toString()?.trim().orEmpty()
            val pass = binding.etLoginPassword.text?.toString()?.trim().orEmpty()

            if (email.isBlank() || !email.contains("@")) {
                showError("Please enter a valid email address.")
                return@setOnClickListener
            }
            if (pass.isBlank()) {
                showError("Please enter your password.")
                return@setOnClickListener
            }

            setLoading(true)
            lifecycleScope.launch {
                val result = ApiClient.login(email, pass)
                setLoading(false)
                result.onSuccess { authRes ->
                    if (!authRes.token.isNullOrBlank() && authRes.user != null) {
                        AuthManager.saveSession(authRes.token, authRes.user)
                        Toast.makeText(this@AuthActivity, "Welcome back, ${authRes.user.displayName}!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        showError(authRes.message.ifBlank { "Login failed." })
                    }
                }.onFailure { e ->
                    showError(e.message ?: "Invalid email or password.")
                }
            }
        }

        // 2. Sign Up Step 1: Send OTP
        binding.btnSendOtp.setOnClickListener {
            val name = binding.etSignUpName.text?.toString()?.trim().orEmpty()
            val email = binding.etSignUpEmail.text?.toString()?.trim().orEmpty()

            if (name.isBlank()) {
                showError("Please enter your full name.")
                return@setOnClickListener
            }
            if (email.isBlank() || !email.contains("@")) {
                showError("Please enter a valid email address.")
                return@setOnClickListener
            }

            pendingName = name
            pendingEmail = email

            setLoading(true)
            lifecycleScope.launch {
                val res = ApiClient.sendOtp(email, "signup")
                setLoading(false)
                res.onSuccess {
                    binding.formSignUpStep1.visibility = View.GONE
                    binding.formSignUpStep2.visibility = View.VISIBLE
                    binding.tvOtpInstruction.text = "Verification code sent to $email.\nEnter it below to complete registration:"
                    hideError()
                    Toast.makeText(this@AuthActivity, "Verification code sent!", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    showError(e.message ?: "Could not send verification code.")
                }
            }
        }

        // 3. Sign Up Step 2: Verify OTP & Register
        binding.btnVerifyAndRegister.setOnClickListener {
            val otp = binding.etOtpCode.text?.toString()?.trim().orEmpty()
            val pass = binding.etSignUpPassword.text?.toString()?.trim().orEmpty()

            if (otp.length != 6) {
                showError("Please enter the complete 6-digit OTP code.")
                return@setOnClickListener
            }
            if (pass.length < 6) {
                showError("Password must be at least 6 characters.")
                return@setOnClickListener
            }

            setLoading(true)
            lifecycleScope.launch {
                val req = RegisterWithOtpRequest(
                    email = pendingEmail,
                    otp = otp,
                    password = pass,
                    displayName = pendingName,
                    targetExam = pendingExam
                )
                val res = ApiClient.registerWithOtp(req)
                setLoading(false)
                res.onSuccess { authRes ->
                    if (!authRes.token.isNullOrBlank() && authRes.user != null) {
                        AuthManager.saveSession(authRes.token, authRes.user)
                        Toast.makeText(this@AuthActivity, "Account verified! Welcome to Kurrent.", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        showError(authRes.message.ifBlank { "Registration failed." })
                    }
                }.onFailure { e ->
                    showError(e.message ?: "Invalid or expired OTP.")
                }
            }
        }

        // Step 2 -> Back to Step 1
        binding.btnBackToStep1.setOnClickListener {
            binding.formSignUpStep2.visibility = View.GONE
            binding.formSignUpStep1.visibility = View.VISIBLE
            hideError()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showSignInTab() {
        hideError()
        binding.tabSignIn.setTextColor(resources.getColor(R.color.white, null))
        binding.tabSignIn.setBackgroundResource(R.drawable.bg_date_chip)

        binding.tabSignUp.setTextColor(resources.getColor(R.color.text_muted, null))
        binding.tabSignUp.background = null

        binding.formSignIn.visibility = View.VISIBLE
        binding.formSignUpStep1.visibility = View.GONE
        binding.formSignUpStep2.visibility = View.GONE
    }

    private fun showSignUpTab() {
        hideError()
        binding.tabSignUp.setTextColor(resources.getColor(R.color.white, null))
        binding.tabSignUp.setBackgroundResource(R.drawable.bg_date_chip)

        binding.tabSignIn.setTextColor(resources.getColor(R.color.text_muted, null))
        binding.tabSignIn.background = null

        binding.formSignIn.visibility = View.GONE
        binding.formSignUpStep1.visibility = View.VISIBLE
        binding.formSignUpStep2.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.pbAuthLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmitSignIn.isEnabled = !loading
        binding.btnSendOtp.isEnabled = !loading
        binding.btnVerifyAndRegister.isEnabled = !loading
        if (loading) hideError()
    }

    private fun showError(msg: String) {
        binding.tvAuthError.text = msg
        binding.tvAuthError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvAuthError.visibility = View.GONE
    }
}
