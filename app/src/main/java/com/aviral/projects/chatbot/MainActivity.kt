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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aviral.projects.chatbot.ui.theme.ChatbotTheme

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

class MainActivity : ComponentActivity(), DeepgramTranscriptionService.TranscriptionListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatBotTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "screen1") {
                    composable("screen1") { UploadScreen(navController) }
                    composable("screen2") { ChatScreen(navController) }
                }
            }
        }
    }

    override fun onTranscriptReceived(transcript: String, isFinal: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onError(error: String) {
        TODO("Not yet implemented")
    }
}

// Theme
//private val DyslexicFont = FontFamily(
//    Font(R.font.lexend_regular, FontWeight.Normal)
//)

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
                IconButton(
                    onClick = {
                        viewModel.OCR_text?.let { filePath ->
                            viewModel.generateTtsAudio(
                                text = filePath, // Or use actual text content if needed
                                context = context,
                                onSuccess = { audioFile ->
                                    MediaPlayer.create(context, Uri.fromFile(audioFile))?.apply {
                                        setOnPreparedListener { mp ->
                                            mp.playbackParams = mp.playbackParams.setSpeed(0.7f)
                                            mp.start()
                                        }
                                        setOnCompletionListener {
                                            it.release()
                                            audioFile.delete()
                                        }
                                        setOnErrorListener { _, what, extra ->
                                            Log.e("TTS", "Playback error: $what, $extra")
                                            release()
                                            audioFile.delete()
                                            false
                                        }
                                    } ?: run {
                                        Log.e("TTS", "Failed to initialize MediaPlayer")
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
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        "Speak Aloud",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = { /* Handle reupload */ },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Reupload")
                }

                Button(
                    onClick = { navController.navigate("screen2") },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Next")
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
fun ChatScreen(navController: NavController) {
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Learning Assistant") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
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
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            IconButton(
                onClick = { /* STT implementation */ },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Mic, "Voice Input")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                else MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                color = Color.White
            )
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

fun MediaPlayer.playFile(file: File) {
    reset()
    setDataSource(file.absolutePath)
    prepare()
    start()
}