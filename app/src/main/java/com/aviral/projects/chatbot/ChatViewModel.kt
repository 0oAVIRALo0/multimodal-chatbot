package com.aviral.projects.chatbot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val sessionId = savedStateHandle.get<String>("sessionId")
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.MINUTES)  // 2 minutes to establish connection
        .readTimeout(5, TimeUnit.MINUTES)     // 5 minutes to read response
        .writeTimeout(2, TimeUnit.MINUTES)    // 2 minutes to send request
        .build()

    fun sendMessage(message: String, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
//                // Replace with actual API call
//                delay(1000) // Simulate network delay
//                val response = "This is a sample response for: $message"
//
//                withContext(Dispatchers.Main) {
//                    callback(response)
//                }
                if (sessionId == null) {
                    callback("Session expired. Please upload the document again.")
                    return@launch
                }

                val jsonBody = JSONObject().apply {
                    put("session_id", sessionId)
                    put("question", message)
                }.toString()

                val request = Request.Builder()
                    .url("http://192.168.1.7:5000/query")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    callback("API Error: ${response.code} - $errorBody")
                    return@launch
                }

                val responseJson = JSONObject(response.body?.string() ?: "")
                val answer = responseJson.optString("answer", "No answer provided")
                callback(answer)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("Error: ${e.localizedMessage}")
                }
            }
        }
    }
}