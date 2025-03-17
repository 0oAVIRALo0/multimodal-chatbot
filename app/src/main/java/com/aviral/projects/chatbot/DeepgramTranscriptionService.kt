package com.aviral.projects.chatbot

import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.Context

class DeepgramTranscriptionService(
    private val context: Context,
    private val apiKey: String,
    private val listener: TranscriptionListener
) {
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val audioQueue = mutableListOf<ByteArray>()
    private var isWaitingForResponse = false
    private val mutex = Any()

    interface TranscriptionListener {
        fun onTranscriptReceived(transcript: String, isFinal: Boolean)
        fun onError(error: String)
    }

    fun startTranscribing() {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("wss://api.deepgram.com/v1/listen?model=nova-2&encoding=linear16&sample_rate=16000&channels=1&interim_results=true")
            .addHeader("Authorization", "Token $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                startAudioRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                CoroutineScope(Dispatchers.Main).launch {
                    listener.onError("WebSocket error: ${t.message}")
                }
                stopTranscribing()
            }
        })
    }

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Microphone permission required")
            return
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
//        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // Get the minimum buffer size for AudioRecord (for initialization)
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        // Define a smaller chunk size for sending audio data (adjust as needed)
        val chunkSize = 4096

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(chunkSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, chunkSize) ?: 0
//                if (bytesRead > 0) {
//                    synchronized(mutex) {
//                        audioQueue.add(buffer.copyOf(bytesRead))
//                    }
//                    processQueue()
//                }
                if (bytesRead > 0) {
                    webSocket?.send(buffer.copyOf(bytesRead).toByteString())
                }
            }
        }
    }

    private fun processQueue() {
        synchronized(mutex) {
            if (!isWaitingForResponse && audioQueue.isNotEmpty()) {
                val chunk = audioQueue.removeAt(0)
                webSocket?.send(chunk.toByteString())
                isWaitingForResponse = true
            }
        }
    }

    private fun handleMessage(jsonMessage: String) {
        try {
            val response = gson.fromJson(jsonMessage, DeepgramResponse::class.java)
            val transcript = response.channel?.alternatives?.firstOrNull()?.transcript ?: return

            if (response.isFinal) {
                listener.onTranscriptReceived(transcript, true)
            } else if (response.speechFinal) {
                listener.onTranscriptReceived(transcript, true)
            } else {
                listener.onTranscriptReceived(transcript, false)
            }

            // When we receive any response, send next chunk
            synchronized(mutex) {
                isWaitingForResponse = false
            }
            processQueue()
        } catch (e: Exception) {
            listener.onError("Error parsing response: ${e.message}")
        }
    }

    fun stopTranscribing() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }

    // Data classes for parsing Deepgram responses
    data class DeepgramResponse(
        val isFinal: Boolean = false,
        val speechFinal: Boolean = false,
        val channel: Channel? = null
    )

    data class Channel(
        val alternatives: List<Alternative>
    )

    data class Alternative(
        val transcript: String
    )
}