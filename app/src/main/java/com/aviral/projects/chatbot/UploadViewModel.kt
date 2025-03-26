package com.aviral.projects.chatbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.google.mlkit.vision.text.TextRecognition
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.asStateFlow
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

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

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId

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
                val mimeType = context.contentResolver.getType(uri) ?: ""
                when {
                    mimeType.startsWith("image/") -> processImage(uri, context)
                    mimeType == "application/pdf" -> processPdf(uri, context)
                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        processDocx(uri, context)
                    else -> throw IOException("Unsupported file type: $mimeType")
                }
                withContext(Dispatchers.Main) {
                    _fileUploaded.value = true
                    Toast.makeText(context, "File processed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processImage(uri: Uri, context: Context) {
//        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
//        val bitmap = context.contentResolver.openInputStream(uri)?.use {
//            BitmapFactory.decodeStream(it, null, options)
//        } ?: throw IOException("Failed to open image")
//
//        val image = InputImage.fromBitmap(bitmap, 0)
//        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
//        val textResult = recognizer.process(image).await()
//        OCR_text = textResult.text
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

    private fun processPdf(uri: Uri, context: Context) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val pdfReader = PdfReader(stream)
            val text = StringBuilder()

            // Extract text from each page
            for (pageNum in 1..pdfReader.numberOfPages) {
                text.append(
                    PdfTextExtractor.getTextFromPage(pdfReader, pageNum)
                )
            }

            OCR_text = text.toString().trim()
            Log.d("OCR TEXT FROM PDF", OCR_text!!)
            pdfReader.close()
        } ?: throw IOException("Failed to open PDF")
    }

    private suspend fun processDocx(uri: Uri, context: Context) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val zipInputStream = ZipInputStream(stream)
            val text = StringBuilder()

            // Extract text from document.xml
            generateSequence { zipInputStream.nextEntry }
                .filter { it.name == "word/document.xml" }
                .forEach { entry ->
                    val xmlContent = zipInputStream.readBytes().toString(Charsets.UTF_8)
                    val xml = XmlPullParserFactory.newInstance().newPullParser().apply {
                        setInput(xmlContent.reader())
                    }
                    var eventType = xml.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.TEXT) text.append(xml.text).append(" ")
                        eventType = xml.next()
                    }
                }

            // Reset stream to process images
            context.contentResolver.openInputStream(uri)?.use { newStream ->
                val imageZip = ZipInputStream(newStream)
                generateSequence { imageZip.nextEntry }
                    .filter { it.name.startsWith("word/media/") }
                    .forEach { imageEntry ->
                        val imageBytes = imageZip.readBytes()
                        val tempFile = File.createTempFile("docx_img_", ".${imageEntry.name.substringAfterLast('.')}", context.cacheDir)
                        FileOutputStream(tempFile).use { it.write(imageBytes) }

                        // Process image with OCR
                        val image = InputImage.fromFilePath(context, Uri.fromFile(tempFile))
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        val result = recognizer.process(image).await()
                        text.append(result.text).append(" ")
                        tempFile.delete()
                    }
            }

            OCR_text = text.toString().replace("\\s+".toRegex(), " ").trim()
        } ?: throw IOException("Failed to open DOCX")
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

    fun sendOCRToAPI(text: String, context: Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                // 1. Configure client with proper timeouts
                val client = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)  // 2 minutes to establish connection
                    .readTimeout(5, TimeUnit.MINUTES)     // 5 minutes to read response
                    .writeTimeout(2, TimeUnit.MINUTES)    // 2 minutes to send request
                    .build()

                // 2. Safe JSON encoding
                val jsonBody = JSONObject().apply {
                    put("text", text)
                }.toString()

                Log.d("API_REQUEST", "Request body: $jsonBody")

                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

                // 3. Build request with headers
                val request = Request.Builder()
                    .url("http://192.168.1.7:5000/process")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                // 4. Execute with proper resource handling
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    Log.d("API_RESPONSE", "Response: $responseBody")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details"
                        throw IOException("HTTP ${response.code}: $errorBody")
                    }

                    // Parse the JSON response
                    val jsonResponse = JSONObject(responseBody ?: throw IOException("Empty response body"))
                    val sessionId = jsonResponse.getString("session_id")

                    // Store the session ID
                    _sessionId.value = sessionId
                    Log.d("SESSION ID", sessionId)

                    // 5. Handle successful response
                    withContext(Dispatchers.Main) {
                        onSuccess(sessionId)
                    }
                }

            } catch (e: SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    onError("Server took too long to respond. Please try again later.")
                }
            } catch (e: ConnectException) {
                withContext(Dispatchers.Main) {
                    onError("Failed to connect to server. Check your network connection.")
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    onError("Network error: ${e.localizedMessage}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Unexpected error: ${e.localizedMessage}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }
}

//fun sendOCRToAPI2(text: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
//    viewModelScope.launch(Dispatchers.IO) {
//        _isProcessing.value = true
//        try {
//            val client = OkHttpClient()
//            val jsonBody = """{"text": "${text}"}"""
//            Log.d("DATA", jsonBody)
//            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
//
//            Log.d("HELLO", "HELLO")
//            val request = Request.Builder()
//                .url("http://192.168.1.7:5000/process")
//                .post(requestBody)
//                .build()
//
//            val response = client.newCall(request).execute()
//            Log.d("HERE", response.toString())
//
//            if (!response.isSuccessful) {
//                val errorBody = response.body?.string() ?: "Unknown error"
//                withContext(Dispatchers.Main) {
//                    onError("API Error ${response.code}: $errorBody")
//                }
//                return@launch
//            }
//
//            withContext(Dispatchers.Main) {
//                onSuccess()
//            }
//
//        } catch (e: Exception) {
//            withContext(Dispatchers.Main) {
//                onError("Network error: ${e.localizedMessage}")
//            }
//        } finally {
//            _isProcessing.value = false
//        }
//    }
//}