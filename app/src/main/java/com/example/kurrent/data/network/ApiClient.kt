package com.example.kurrent.data.network

import android.util.Log
import com.example.kurrent.model.AuthResponse
import com.example.kurrent.model.DailyBriefing
import com.example.kurrent.model.LoginRequest
import com.example.kurrent.model.ProfileResponse
import com.example.kurrent.model.RegisterWithOtpRequest
import com.example.kurrent.model.ResetPasswordRequest
import com.example.kurrent.model.SendOtpRequest
import com.example.kurrent.model.SendOtpResponse
import com.example.kurrent.model.SyncProgressRequest
import com.example.kurrent.model.SyncProgressResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "KurrentApiClient"

    // Default EC2 Public IP (can also be changed dynamically or in settings)
    private const val DEFAULT_BASE_URL = "http://18.60.42.240:8000"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetches today's briefing from the EC2 backend.
     * Returns null if server is unreachable, offline, or returns invalid JSON.
     */
    suspend fun getTodayBriefing(baseUrl: String = DEFAULT_BASE_URL): DailyBriefing? =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/briefing/today"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            return@withContext gson.fromJson(bodyString, DailyBriefing::class.java)
                        }
                    } else {
                        Log.w(TAG, "Server returned HTTP ${response.code} for $url")
                    }
                }
            } catch (e: Exception) {
                // Network unreachable or parsing exception -> graceful fallback to offline cache
                Log.w(TAG, "Backend sync error: ${e.message}", e)
            }
            return@withContext null
        }

    /**
     * Fetches past briefing by YYYY-MM-DD for revision.
     */
    suspend fun getBriefingByDate(date: String, baseUrl: String = DEFAULT_BASE_URL): DailyBriefing? =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/briefing/$date"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            return@withContext gson.fromJson(bodyString, DailyBriefing::class.java)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Briefing date fetch error: ${e.message}", e)
            }
            return@withContext null
        }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun sendOtp(email: String, purpose: String = "signup", baseUrl: String = DEFAULT_BASE_URL): Result<SendOtpResponse> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/auth/send-otp"
            val json = gson.toJson(SendOtpRequest(email, purpose))
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = gson.fromJson(body, SendOtpResponse::class.java)
                        Result.success(parsed)
                    } else {
                        Result.failure(Exception(body.ifBlank { "Failed to send verification code." }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun registerWithOtp(req: RegisterWithOtpRequest, baseUrl: String = DEFAULT_BASE_URL): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/auth/register"
            val json = gson.toJson(req)
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = gson.fromJson(body, AuthResponse::class.java)
                        Result.success(parsed)
                    } else {
                        Result.failure(Exception(body.ifBlank { "Registration failed." }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun login(email: String, pass: String, baseUrl: String = DEFAULT_BASE_URL): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/auth/login"
            val json = gson.toJson(LoginRequest(email, pass))
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = gson.fromJson(body, AuthResponse::class.java)
                        Result.success(parsed)
                    } else {
                        Result.failure(Exception(body.ifBlank { "Invalid email or password." }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun resetPassword(
        email: String,
        otp: String,
        newPassword: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/auth/forgot-password/reset"
        val json = gson.toJson(ResetPasswordRequest(email, otp, newPassword))
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = gson.fromJson(body, AuthResponse::class.java)
                    Result.success(parsed)
                } else {
                    Result.failure(Exception(body.ifBlank { "Password reset failed." }))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncProgress(req: SyncProgressRequest, token: String, baseUrl: String = DEFAULT_BASE_URL): Result<SyncProgressResponse> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/user/sync"
            val json = gson.toJson(req)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(json.toRequestBody(JSON_MEDIA))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = gson.fromJson(body, SyncProgressResponse::class.java)
                        Result.success(parsed)
                    } else {
                        Result.failure(Exception(body.ifBlank { "Sync failed." }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getProfile(token: String, baseUrl: String = DEFAULT_BASE_URL): Result<ProfileResponse> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/user/profile"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = gson.fromJson(body, ProfileResponse::class.java)
                        Result.success(parsed)
                    } else {
                        Result.failure(Exception(body.ifBlank { "Failed to load profile." }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
