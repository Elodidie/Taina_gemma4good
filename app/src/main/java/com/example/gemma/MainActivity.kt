package com.example.gemma

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val modelLoaded by vm.modelLoaded.collectAsState()
    val recordSaved by vm.recordSaved.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Camera URI
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.onPhotoSelected(it) } }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { vm.onPhotoSelected(it) } }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.filesDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            val file = File(context.filesDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Scroll when a new message is added OR when the last message grows
    // (bot responses update in-place, so messages.size alone never triggers)
    val lastMessageText = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastMessageText) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Snackbar when record is saved
    LaunchedEffect(recordSaved) {
        if (recordSaved != null) vm.acknowledgeRecordSaved()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Taina 🌿") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        if (!modelLoaded) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Loading model...", modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { message -> ChatBubble(message) }
        }

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera button
            IconButton(
                onClick = { launchCamera() },
                enabled = modelLoaded && !isLoading
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Take photo")
            }

            // Gallery button
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                enabled = modelLoaded && !isLoading
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload photo")
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                enabled = modelLoaded && !isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    vm.sendMessage(inputText)
                    inputText = ""
                }),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = { vm.sendMessage(inputText); inputText = "" },
                enabled = modelLoaded && !isLoading && inputText.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        // Show photo thumbnail if present
        message.photoUri?.let { uri ->
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Observation photo",
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(bottom = 4.dp),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (message.isLoading) TypingIndicator()
            else Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Text("●●●", modifier = Modifier.alpha(alpha))
}