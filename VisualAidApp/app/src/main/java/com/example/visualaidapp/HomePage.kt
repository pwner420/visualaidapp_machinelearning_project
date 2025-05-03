// VisualAidApp: HomePage and CameraCapture components
package com.example.visualaidapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.*
import java.io.File

@Composable
fun HomePage(onReady: () -> Unit = {}) {
    val context = LocalContext.current
    val captioner = remember { ImageCaptioner(context) } // Caption generator class

    // Accessibility focus management
    val captionFocusRequester = remember { FocusRequester() }
    val clearImageFocusRequester = remember { FocusRequester() }
    val neutralFocusRequester = remember { FocusRequester() }
    val generateFocusRequester = remember { FocusRequester() }
    val cameraFocusRequester = remember { FocusRequester() }

    // State variables to manage app state
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captionText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showCamera by remember { mutableStateOf(false) }
    var screenReaderAnnouncement by remember { mutableStateOf("") }
    var shouldGenerateCaption by remember { mutableStateOf(false) }

    // Initial screen reader announcement
    LaunchedEffect(Unit) {
        onReady()
        screenReaderAnnouncement = "Welcome. You can take a photo with your camera or pick one" +
                "from your storage to get a description of it. Move between different buttons," +
                "by swiping left and right on your screen"
        delay(11000)
        screenReaderAnnouncement = ""
    }

    // Generate caption when triggered
    LaunchedEffect(shouldGenerateCaption) {
        if (shouldGenerateCaption && imageBitmap != null) {
            isLoading = true
            progress = 0f
            delay(300)
            neutralFocusRequester.requestFocus()

            withContext(Dispatchers.Default) {
                val caption = captioner.generateCaption(imageBitmap!!) { progress = it }
                withContext(Dispatchers.Main) {
                    captionText = caption
                    isLoading = false
                    shouldGenerateCaption = false
                }
            }
        }
    }

    // Auto-focus the generate button if image is ready
    LaunchedEffect(imageBitmap) {
        if (imageBitmap != null && captionText == null && !isLoading) {
            delay(2000)
            generateFocusRequester.requestFocus()
        }
    }

    // Image picker logic
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val stream = context.contentResolver.openInputStream(it)
                imageBitmap = BitmapFactory.decodeStream(stream)
                captionText = null
                isLoading = false
                progress = 0f
                shouldGenerateCaption = false
            }
        }
    )

    // Main layout
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screen reader live region
        if (screenReaderAnnouncement.isNotBlank() &&
            screenReaderAnnouncement != "Image cleared, you can now select a new one.") {
            Text(
                text = screenReaderAnnouncement,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = screenReaderAnnouncement
                },
                color = Color.Transparent,
                fontSize = 1.sp
            )
        }

        // Caption generation in progress announcement
        if (isLoading) {
            Text(
                text = "Generating caption...",
                modifier = Modifier.focusRequester(neutralFocusRequester).focusable().semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = "Generating caption. Please wait."
                },
                color = Color.Transparent,
                fontSize = 1.sp
            )
        }

        // Show camera preview if enabled
        if (showCamera) {
            CameraCapture(
                onImageCaptured = {
                    imageBitmap = it
                    showCamera = false
                    captionText = null
                    isLoading = false
                    progress = 0f
                    shouldGenerateCaption = false
                },
                onError = {
                    showCamera = false
                    Log.e("CameraCapture", "Error capturing image", it)
                }
            )
        } else {
            // Show image picker and camera buttons if no image
            if (imageBitmap == null) {
                Button(
                    onClick = { showCamera = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .semantics { contentDescription = "Open Camera" }
                        .focusable().focusRequester(cameraFocusRequester),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("Take a Photo", fontSize = 24.sp, color = Color.White)
                }

                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("Pick Image from Storage", fontSize = 24.sp, color = Color.White)
                }
            }

            // Show image preview and captioning controls
            if (imageBitmap != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageBitmap),
                    contentDescription = "Selected image preview...",
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(bottom = 16.dp)
                )

                // Show caption button
                if (!isLoading && captionText == null) {
                    Button(
                        onClick = { shouldGenerateCaption = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            .focusRequester(generateFocusRequester).focusable().semantics {
                                contentDescription = ""
                                liveRegion = LiveRegionMode.Assertive
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Generate Caption", fontSize = 24.sp, color = Color.White)
                    }
                }
            }

            // Show loading progress bar
            if (isLoading) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics {
                        contentDescription = "Generating caption progress"
                    }
                )
            }

            // Show caption result and clear button
            captionText?.let { cap ->
                Text(
                    text = cap,
                    fontSize = 24.sp,
                    modifier = Modifier.focusRequester(captionFocusRequester).focusable()
                        .padding(bottom = 24.dp).semantics {
                            contentDescription = "Caption generated, it goes: $cap"
                            liveRegion = LiveRegionMode.Assertive
                        }
                )

                Button(
                    onClick = {
                        imageBitmap = null
                        captionText = null
                        isLoading = false
                        progress = 0f
                        shouldGenerateCaption = false
                        screenReaderAnnouncement = "Image cleared, you can now select a new one."
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        .focusRequester(clearImageFocusRequester).focusable(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Clear Image", fontSize = 24.sp, color = Color.White)
                }

                // Delay focus shift to give time for screen reader to announce caption
                LaunchedEffect(captionText) {
                    captionFocusRequester.requestFocus()
                    val fullText = "Caption generated, it goes: $cap"
                    val wordCount = fullText.trim().split("\\s+".toRegex()).size
                    val delayByWords = (wordCount / 2.5) * 1100
                    val delayByChars = (fullText.length / 13.0) * 1100
                    val delayMillis = maxOf(delayByWords, delayByChars).toLong().coerceIn(1000L, 15000L)
                    delay(delayMillis)
                    clearImageFocusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
fun CameraCapture(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Compose wrapper for CameraX PreviewView
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Camera preview" }
        )

        // Properly async camera binding using addListener
        LaunchedEffect(Unit) {
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                } catch (exc: Exception) {
                    Log.e("CameraCapture", "Camera binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // Floating action button to take a photo
        FloatingActionButton(
            onClick = {
                val photoFile = File.createTempFile("captured", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                            onImageCaptured(bitmap)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            onError(exception)
                        }
                    }
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp).semantics {
                contentDescription = "Take photo"
            },
            containerColor = Color.Black
        ) {
            Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.White)
        }
    }
}
