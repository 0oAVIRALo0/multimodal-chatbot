package com.aviral.projects.chatbot

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class UploadViewModel : ViewModel() {
    private val _fileUploaded = MutableStateFlow(false)
    val fileUploaded: StateFlow<Boolean> = _fileUploaded
    var uploadedFile by mutableStateOf<File?>(null)
        private set

    private val _ttsFile = MutableStateFlow<File?>(null)
    var OCR_text by mutableStateOf<String?>(null)
        private set

    private val DEEPGRAM_API_URL = "https://api.deepgram.com/v1/speak?model=aura-asteria-en"
    private val DEEPGRAM_API_KEY = "d56a937b3291497b90601f64fd35e3a74c359c5d"

    private val okHttpClient = OkHttpClient()

    fun cleanup() {
        uploadedFile?.delete()
        _ttsFile.value?.delete()
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }

    fun uploadFile(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Decode bitmap with scaling
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }

                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: throw IOException("Failed to open image stream")

                // Process with ML Kit
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                // Using coroutines await() for async operation
                val textResult = recognizer.process(image).await()

                OCR_text = textResult.text
                Log.d("EXTRACTED TEXT", OCR_text!!)

                withContext(Dispatchers.Main) {
                    _fileUploaded.value = true
                    Toast.makeText(context, "OCR completed", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun createImageUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    fun generateTtsAudio(text: String, context: Context, onSuccess: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sanitizedText = text
                    .replace(Regex("[^\\p{Print}]"), " ") // Remove non-printable chars

                val jsonBody = """{"text": "${sanitizedText.replace("\"", "\\\"")}"}"""
                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(DEEPGRAM_API_URL)
                    .addHeader("Authorization", "Token $DEEPGRAM_API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    withContext(Dispatchers.Main) {
                        onError("API Error ${response.code}: $errorBody")
                    }
                    return@launch
                }

                val audioBytes = response.body?.bytes() ?: run {
                    withContext(Dispatchers.Main) {
                        onError("Empty audio response")
                    }
                    return@launch
                }

                val audioFile = File.createTempFile("tts_audio", ".mp3", context.cacheDir).apply {
                    deleteOnExit()
                }

                FileOutputStream(audioFile).use { fos ->
                    fos.write(audioBytes)
                }

                withContext(Dispatchers.Main) {
                    onSuccess(audioFile)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error generating speech: ${e.localizedMessage}")
                }
            }
        }
    }
}