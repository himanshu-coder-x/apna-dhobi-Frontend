package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        // Fetch API key securely from BuildConfig as per android-secret-management skill
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve API Key from BuildConfig, using fallback or prompt error message: ${e.message}")
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is empty or placeholder! Instructing user to configure it.")
            return@withContext "Hi there! I'm Apna Dhobi's AI laundry assistant.\n\nTo make me fully functional, please configure a valid `GEMINI_API_KEY` in the Secrets Panel layout of Google AI Studio and refresh. In the meantime, here is a mock helpful answer based on fabrics: For delicate items like silk or wedding wear, please book laundry or dry cleaning options!"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val systemInstruction = "You are Apna Dhobi's premium AI Laundry Assistant. " +
                "You are an expert in fabric care, stain removal, clothing repair advice, laundry categorization, detergent amounts, and home care. " +
                "Keep answers highly professional, short, clean, structured, and friendly. Advise users on which Apna Dhobi category (laundry, dry cleaning, ironing, shoe cleaning, blanket wash) fits their fabrics."

        try {
            // Build direct JSON payload to avoid library dependencies
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            }

            val systemInstructionJson = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", systemInstructionJson)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Empty body"
                    Log.e(TAG, "Request failed: Status ${response.code}, Msg: $errorBody")
                    return@withContext "I apologize, but I encountered an issue connecting to my laundry knowledge base. Status ${response.code}."
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    return@withContext "Empty response received."
                }

                // Standard Gemini REST API parsing
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
                "Response format mismatch. Please try again."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during call to Gemini API: ${e.message}", e)
            "I'm sorry, my stain-busting engine returned an error: ${e.localizedMessage}. Please ensure your internet connection is active."
        }
    }
}
