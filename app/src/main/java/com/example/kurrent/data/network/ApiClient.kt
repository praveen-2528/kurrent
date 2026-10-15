package com.example.kurrent.data.network

import android.util.Log
import com.example.kurrent.model.DailyBriefing
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
