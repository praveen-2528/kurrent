package com.example.kurrent.data

import android.content.Context
import android.content.SharedPreferences
import com.example.kurrent.model.UserProfile
import com.google.gson.Gson

object AuthManager {

    private const val PREFS_NAME = "kurrent_auth_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_PROFILE = "user_profile_json"

    private const val KEY_USER_STATS = "user_stats_json"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isLoggedIn(): Boolean {
        return !getAuthToken().isNullOrBlank()
    }

    fun getAuthToken(): String? {
        return prefs?.getString(KEY_TOKEN, null)
    }

    fun getUserProfile(): UserProfile? {
        val json = prefs?.getString(KEY_USER_PROFILE, null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserStats(): com.example.kurrent.model.UserStats? {
        val json = prefs?.getString(KEY_USER_STATS, null) ?: return null
        return try {
            gson.fromJson(json, com.example.kurrent.model.UserStats::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSession(token: String, user: UserProfile) {
        prefs?.edit()?.apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_PROFILE, gson.toJson(user))
            apply()
        }
    }

    fun saveStats(stats: com.example.kurrent.model.UserStats) {
        prefs?.edit()?.apply {
            putString(KEY_USER_STATS, gson.toJson(stats))
            apply()
        }
    }

    fun clearSession() {
        prefs?.edit()?.apply {
            remove(KEY_TOKEN)
            remove(KEY_USER_PROFILE)
            remove(KEY_USER_STATS)
            apply()
        }
    }
}
