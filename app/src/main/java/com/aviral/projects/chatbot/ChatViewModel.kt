package com.aviral.projects.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {
    fun sendMessage(message: String, callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Replace with actual API call
                delay(1000) // Simulate network delay
                val response = "This is a sample response for: $message"

                withContext(Dispatchers.Main) {
                    callback(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("Error: ${e.localizedMessage}")
                }
            }
        }
    }
}