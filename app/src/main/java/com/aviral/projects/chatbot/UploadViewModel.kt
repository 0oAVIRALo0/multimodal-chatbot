package com.aviral.projects.chatbot

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues.TAG
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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

    fun uploadFile(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) { // 🚀 Moved entire function to IO thread
            try {
                uploadedFile = uri.toFile(context)
                val contentResolver = context.contentResolver

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        getFileName(contentResolver, uri),
                        contentResolver.openInputStream(uri)!!.readBytes()
                            .toRequestBody(contentResolver.getType(uri)?.toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("http://192.168.1.7:5000/process-file")
                    .post(requestBody)
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body?.string()

                // Add detailed logging
                Log.d("UPLOAD", "Response code: ${response.code}")
                Log.d("UPLOAD", "Response body: ${responseBody}")

                // Parse server response to get file path
                OCR_text = responseBody?.let {
                    val json = JSONObject(it) // Parse JSON
                    json.optString("OCR_text", "") // Get the file_path value
                }
                OCR_text?.let { Log.d("FILE PATH", it) }

                // Update state on main thread
                withContext(Dispatchers.Main) {
                    _fileUploaded.value = response.isSuccessful
                    if (!response.isSuccessful) {
                        Log.e("UPLOAD", "Upload failed: ${response.message}")
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
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

    @SuppressLint("Range")
    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        return when (uri.scheme) {
            "content" -> contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            } ?: "unknown_file"
            else -> uri.path?.substringAfterLast('/') ?: "unknown_file"
        }
    }

    fun generateTtsAudio(text: String, context: Context, onSuccess: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonBody = """{"text": "${text.replace("\"", "\\\"")}"}"""

                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(DEEPGRAM_API_URL)
                    .addHeader("Authorization", "Token $DEEPGRAM_API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    onError("API Error: ${response.code} - ${response.message}")
                    return@launch
                }

                val audioBytes = response.body?.bytes() ?: run {
                    onError("Empty audio response")
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


fun Uri.toFile(context: Context): File {
    val inputStream = context.contentResolver.openInputStream(this)
        ?: throw IOException("Cannot open input stream")

    val file = File.createTempFile("upload_", ".tmp", context.cacheDir).apply {
        deleteOnExit()
    }

    Log.d("FILE_CONV", "Converting URI to file: ${this.path}")
    Log.d("FILE_CONV", "Target file: ${file.absolutePath}")

    file.outputStream().use { output ->
        inputStream.copyTo(output)
    }
    return file
}