package com.aviral.projects.chatbot

// Core Android & Compose
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


// Theme
//private val DyslexicFont = FontFamily(
//    Font(R.font.lexend_regular, FontWeight.Normal)
//)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatBotTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "screen1") {
                    composable("screen1") { UploadScreen(navController) }
                    composable("screen2/{sessionId}") { backStackEntry ->
                        ChatScreen(
                            navController = navController,
                            sessionId = backStackEntry.arguments?.getString("sessionId")
                        )
                    }
                }
            }
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2A6F97),
    secondary = Color(0xFFA8DADC),
    background = Color(0xFFF1FAEE),
)

@Composable
fun ChatBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(
            bodyLarge = TextStyle(
//                fontFamily = DyslexicFont,
                fontSize = 18.sp,
                color = Color.Black
            ),
            labelLarge = TextStyle(
//                fontFamily = DyslexicFont,
                fontSize = 18.sp,
                color = Color.White
            )
        ),
        content = content
    )
}

// Screen 1 - Upload Screen
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun UploadScreen(navController: NavController) {
    val context = LocalContext.current
    var mediaPlayer = remember { MediaPlayer() }

    // Clean up when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.apply {
                if (isPlaying) stop()
                release()
            }
        }
    }

    val viewModel: UploadViewModel = viewModel()
    val fileUploaded by viewModel.fileUploaded.collectAsState()
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // Camera Intent Launcher (Moved above to fix unresolved reference)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUri?.let { viewModel.uploadFile(it, context) }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imageUri = viewModel.createImageUri(context)
            imageUri?.let { cameraLauncher.launch(it) }
        }
    }

    // File Picker Launcher
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadFile(it, context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Upload Learning Material",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UploadButton(
                icon = Icons.Default.UploadFile,
                text = "Upload File",
                onClick = { fileLauncher.launch("*/*") }
            )

            UploadButton(
                icon = Icons.Default.CameraAlt,
                text = "Take Photo",
                onClick = {
                    val permissionCheckResult = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    )
                    if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                        imageUri = viewModel.createImageUri(context)
                        cameraLauncher.launch(imageUri!!)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )
        }

        if (fileUploaded) {
            Log.d("SCREEN", "Showing upload success UI")
            Column(
                modifier = Modifier.padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Add this Card to show extracted text
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = viewModel.OCR_text ?: "No text extracted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.OCR_text.isNullOrBlank())
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.OCR_text?.let { ocrText ->
                            if (ocrText.isBlank()) {
                                Toast.makeText(context, "No text found in image", Toast.LENGTH_SHORT).show()
                                return@let
                            }

                            viewModel.generateTtsAudio(
                                text = ocrText,
                                context = context,
                                onSuccess = { audioFile ->
                                    try {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            audioFile
                                        )

                                        mediaPlayer.apply {
                                            reset()
                                            setDataSource(context, contentUri)
                                            prepareAsync()
                                            setOnPreparedListener { mp ->
                                                mp.playbackParams = mp.playbackParams.setSpeed(0.8f)
                                                mp.start()
                                            }
                                            setOnCompletionListener {
                                                it.release()
                                                audioFile.delete()
                                                // Reset mediaPlayer for next use
                                                mediaPlayer = MediaPlayer()
                                            }
                                            setOnErrorListener { _, what, extra ->
                                                Log.e("TTS", "Playback error: $what, $extra")
                                                release()
                                                audioFile.delete()
                                                true
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("TTS", "Playback failed", e)
                                        audioFile.delete()
                                    }
                                },
                                onError = { error ->
                                    Log.e("TTS", error)
                                    // Show error to user
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        } ?: run {
                            Toast.makeText(context, "No content available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        "Speak Aloud",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

//                Button(
//                    onClick = { /* Handle reupload */ },
//                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
//                ) {
//                    Text("Reupload")
//                }

                val isProcessing by viewModel.isProcessing.collectAsState()

                // Add progress indicator
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(
                        onClick = {
                            viewModel.OCR_text?.let { ocrText ->
                                if (ocrText.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "No text extracted from document",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    viewModel.sendOCRToAPI(
                                        text = ocrText,
                                        context = context,
                                        onSuccess = { sessionId ->
                                            navController.navigate("screen2/$sessionId")
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            } ?: run {
                                Toast.makeText(
                                    context,
                                    "Please upload a document first",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Any questions? Ask me")
                    }
                }
            }
        }
    }
}

@Composable
fun UploadButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Text(text, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
    }
}

// Screen 2 - Chat Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, sessionId: String?) {
    val scope = rememberCoroutineScope()
    val textFieldState = remember { mutableStateOf(TextFieldValue()) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("How can I help you?", false)
        )
    }
    val viewModel: ChatViewModel = viewModel()
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }

    // Track transcription state
    var stableText by remember { mutableStateOf("") }
    var interimText by remember { mutableStateOf("") }

    // STT Service
    val deepgram = remember {
        DeepgramTranscriptionService(
            context = context,
            apiKey = "d56a937b3291497b90601f64fd35e3a74c359c5d",
            object : DeepgramTranscriptionService.TranscriptionListener {
                override fun onTranscriptReceived(transcript: String, isFinal: Boolean) {
                    if (isFinal) {
                        stableText += if (stableText.isEmpty()) transcript else " $transcript"
                        interimText = ""
                    } else {
                        // Update interim text with latest partial result
                        interimText = transcript
                    }

                    // Update text field with combined stable + interim
                    val displayText = if (interimText.isNotEmpty()) {
                        if (stableText.isEmpty()) interimText else "$stableText $interimText"
                    } else {
                        stableText
                    }

                    textFieldState.value = TextFieldValue(
                        text = displayText,
                        selection = TextRange(displayText.length)
                    )
                }

                override fun onError(error: String) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Get session ID from arguments
    val sessionId = remember {
        navController.currentBackStackEntry?.arguments?.getString("sessionId")
    }

    // Show error if session ID is missing
    LaunchedEffect(sessionId) {
        if (sessionId == null) {
            Toast.makeText(context, "Session expired", Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
    }

    // Audio Permission
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
            deepgram.startTranscribing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning Assistant") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textFieldState.value,
                    onValueChange = {
                        textFieldState.value = it
                        stableText = it.text
                        interimText = ""
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    placeholder = { Text("Type or speak your question...") }
                )

                IconButton(
                    onClick = {
                        if (textFieldState.value.text.isNotBlank()) {
                            val userMessage = textFieldState.value.text
                            messages.add(ChatMessage(userMessage, true))
                            messages.add(ChatMessage("Thinking...", false))

                            viewModel.sendMessage(userMessage) { response ->
                                messages[messages.lastIndex] = ChatMessage(response, false)
                                scope.launch {
                                    scrollState.animateScrollToItem(messages.size - 1)
                                }
                            }

                            // Clear input
                            stableText = ""
                            interimText = ""
                            textFieldState.value = TextFieldValue()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Send, "Send")
                }

                IconButton(
                    onClick = {
                        if (isRecording) {
                            deepgram.stopTranscribing()
                            isRecording = false
                            // Commit any remaining interim text
                            if (interimText.isNotEmpty()) {
                                stableText += if (stableText.isEmpty()) interimText else " $interimText"
                                interimText = ""
                                textFieldState.value = TextFieldValue(stableText)
                            }
                        } else {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                isRecording = true
                                deepgram.startTranscribing()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        "Voice Input",
                        tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary
                else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                color = if (message.isUser) Color.White else MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

fun MediaPlayer.playFile(file: File) {
    try {
        reset()
        setDataSource(file.absolutePath)
        prepareAsync()
        setOnPreparedListener { start() }
    } catch (e: IOException) {
        Log.e("MediaPlayer", "Error playing file", e)
    }
}