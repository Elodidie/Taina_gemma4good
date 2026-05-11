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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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

// ─── Navigation ───────────────────────────────────────────────────────────────

enum class TainaScreen { Chat, Records, Stats }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TainaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TainaApp()
                }
            }
        }
    }
}

@Composable
fun TainaApp() {
    var currentScreen by remember { mutableStateOf(TainaScreen.Chat) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == TainaScreen.Chat,
                    onClick  = { currentScreen = TainaScreen.Chat },
                    icon     = { Icon(Icons.Default.Forum, contentDescription = "Chat") },
                    label    = { Text("Taina") }
                )
                NavigationBarItem(
                    selected = currentScreen == TainaScreen.Records,
                    onClick  = { currentScreen = TainaScreen.Records },
                    icon     = { Icon(Icons.Default.List, contentDescription = "Records") },
                    label    = { Text("My Records") }
                )
                NavigationBarItem(
                    selected = currentScreen == TainaScreen.Stats,
                    onClick  = { currentScreen = TainaScreen.Stats },
                    icon     = { Icon(Icons.Default.BarChart, contentDescription = "Stats") },
                    label    = { Text("Stats") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                TainaScreen.Chat    -> ChatScreen()
                TainaScreen.Records -> RecordsScreen()
                TainaScreen.Stats   -> StatsScreen()
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

    // Request location permission on first load so GPS is available for records
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) vm.refreshLocation()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Camera URI
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // File picker — GetContent allows selecting images from any folder (Downloads, etc.)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
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
        CenterAlignedTopAppBar(
            title = { Text("Taina 🌿") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor    = Color(0xFF282828),
                titleContentColor = Color(0xFFF2EEE4)
            ),
            expandedHeight = 40.dp,
            windowInsets = WindowInsets(0)
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
                    photoPickerLauncher.launch("image/*")
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
        // Show photo thumbnail if present — load from the internal file, never from a content URI
        message.photoPath?.let { path ->
            Image(
                painter = rememberAsyncImagePainter(File(path)),
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