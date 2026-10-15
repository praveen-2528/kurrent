package com.example.kurrent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.kurrent.R
import com.example.kurrent.data.AuthManager
import com.example.kurrent.data.SampleDataRepository
import com.example.kurrent.data.network.ApiClient
import com.example.kurrent.databinding.BottomSheetAccountBinding
import com.example.kurrent.model.RegisterWithOtpRequest
import com.example.kurrent.model.UserStats
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AccountBottomSheetDialog(
    private val onAuthChanged: (Boolean) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAccountBinding? = null
    private val binding get() = _binding!!

    private var pendingEmail: String = ""
    private var pendingName: String = ""
    private var pendingExam: String = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshViewState()
        setupListeners()
    }

    private fun refreshViewState() {
        if (AuthManager.isLoggedIn()) {
            showProfileView()
        } else {
            showGuestView()
        }
    }

    private fun showProfileView() {
        binding.layoutGuestView.visibility = View.GONE
        binding.layoutProfileView.visibility = View.VISIBLE

        val profile = AuthManager.getUserProfile()
        if (profile != null) {
            val name = profile.displayName.ifBlank { "Aspirant" }
            binding.tvProfileName.text = name
            binding.tvProfileEmail.text = profile.email
            binding.tvAvatarInitial.text = name.take(1).uppercase()
            binding.tvProfileExamBadge.text = when (profile.targetExam) {
                "UPSC" -> "UPSC Civil Services Aspirant"
                "SSC" -> "SSC CGL / CHSL Aspirant"
                "IBPS" -> "IBPS / Banking Aspirant"
                else -> "Comprehensive Aspirant (All Exams)"
            }
        }

        // Fetch latest stats from server
        val token = AuthManager.getAuthToken() ?: return
        lifecycleScope.launch {
            val res = ApiClient.getProfile(token)
            if (res.isSuccess) {
                val stats = res.getOrNull()?.progress
                if (stats != null) {
                    AuthManager.saveStats(stats)
                    updateStatsDisplay(stats)
                }
            }
        }
    }

    private fun updateStatsDisplay(stats: UserStats) {
        binding.tvProfileStreak.text = "${stats.currentStreak} Days"
        binding.tvProfileTotalRead.text = "${stats.totalArticlesRead}"
        binding.tvProfileBookmarks.text = "${stats.cloudBookmarks.size}"
    }

    private fun showGuestView() {
        binding.layoutGuestView.visibility = View.VISIBLE
        binding.layoutProfileView.visibility = View.GONE
        showSignInTab()
    }

    private fun setupListeners() {
        // Tab Switcher
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
                        Toast.makeText(context, "Welcome back, ${authRes.user.displayName}!", Toast.LENGTH_SHORT).show()
                        onAuthChanged(true)
                        refreshViewState()
                        // Immediate background sync
                        SampleDataRepository.syncUserCloudProgress()
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
                res.onSuccess { otpRes ->
                    binding.formSignUpStep1.visibility = View.GONE
                    binding.formSignUpStep2.visibility = View.VISIBLE
                    binding.tvOtpInstruction.text = "Verification code sent to $email.\nEnter it below to complete registration:"
                    hideError()
                    Toast.makeText(context, "Verification code sent!", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Account verified! Welcome to Kurrent.", Toast.LENGTH_SHORT).show()
                        onAuthChanged(true)
                        refreshViewState()
                        // Immediate initial sync
                        SampleDataRepository.syncUserCloudProgress()
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

        // Manual Sync Button in Profile View
        binding.btnManualSync.setOnClickListener {
            binding.btnManualSync.isEnabled = false
            binding.btnManualSync.text = "Syncing..."
            lifecycleScope.launch {
                val stats = SampleDataRepository.syncUserCloudProgress()
                binding.btnManualSync.isEnabled = true
                binding.btnManualSync.text = "Sync Progress Now"
                if (stats != null) {
                    updateStatsDisplay(stats)
                    Toast.makeText(context, "Progress synced to cloud!", Toast.LENGTH_SHORT).show()
                    onAuthChanged(true)
                } else {
                    Toast.makeText(context, "Sync complete (offline cache up to date)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Log Out Button
        binding.btnLogout.setOnClickListener {
            AuthManager.clearSession()
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            dismiss()
            val intent = android.content.Intent(requireContext(), AuthActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
