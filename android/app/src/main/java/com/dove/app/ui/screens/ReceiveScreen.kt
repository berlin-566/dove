package com.dove.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dove.app.crypto.Crypto
import com.dove.app.fountain.FountainDecoder
import com.dove.app.fountain.Symbol
import com.dove.app.settings.SettingsManager
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

data class ReceivedFileResult(
    val fileName: String,
    val data: ByteArray,
    val isImage: Boolean,
    val textContent: String?
)

@OptIn(ExperimentalGetImage::class)
@Composable
fun ReceiveScreen(lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var progressText by remember { mutableStateOf("Point camera at optical stream") }
    var progressPercent by remember { mutableIntStateOf(0) }
    var framesAnalyzed by remember { mutableIntStateOf(0) }
    var symbolsReceivedCount by remember { mutableIntStateOf(0) }
    var codesInViewCount by remember { mutableIntStateOf(0) }
    var currentSpeedKbps by remember { mutableFloatStateOf(0f) }
    var currentSymPerSec by remember { mutableIntStateOf(0) }
    var decoder by remember { mutableStateOf<FountainDecoder?>(null) }
    var completedResult by remember { mutableStateOf<ReceivedFileResult?>(null) }
    var detectedBoxes by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(1080) }
    var imageHeight by remember { mutableIntStateOf(1920) }

    // Rolling throughput measurement
    var lastThroughputCheck by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastSymbolsCount by remember { mutableIntStateOf(0) }
    var lastBytesCount by remember { mutableLongStateOf(0L) }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera permission is required to scan optical streams.",
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Grant Camera Permission")
            }
        }
        return
    }

    if (completedResult != null) {
        val result = completedResult!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transfer Complete",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "File: ${result.fileName} (${result.data.size} bytes)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved to Downloads",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (result.isImage) {
                val bmp = remember(result) {
                    try {
                        BitmapFactory.decodeByteArray(result.data, 0, result.data.size)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Image Preview",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Received Image Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            } else if (result.textContent != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Text Content",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.textContent,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    completedResult = null
                    decoder = null
                    progressPercent = 0
                    symbolsReceivedCount = 0
                    codesInViewCount = 0
                    currentSpeedKbps = 0f
                    detectedBoxes = emptyList()
                    progressText = "Point camera at optical stream"
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Receive Another File")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Optical Receiver",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Codes in view: $codesInViewCount",
                        fontSize = 13.sp,
                        color = if (codesInViewCount > 0) Color(0xFF238636) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressText,
                    fontSize = 13.sp,
                    color = if (progressPercent > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Symbols: $symbolsReceivedCount",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (currentSpeedKbps > 0) "${String.format("%.1f", currentSpeedKbps)} KB/s ($currentSymPerSec sym/s)" else "Scanning...",
                        fontSize = 11.sp,
                        color = if (currentSpeedKbps > 0) Color(0xFF58A6FF) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Frames: $framesAnalyzed",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            val scanner = BarcodeScanning.getClient(options)

                            val resolutionPref = SettingsManager.getCameraResolution(ctx)
                            val targetSize = if (resolutionPref == "720p") {
                                android.util.Size(1280, 720)
                            } else {
                                android.util.Size(1920, 1080)
                            }

                            val executor = Executors.newSingleThreadExecutor()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setTargetResolution(targetSize)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && completedResult == null) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

                                    if (rotation == 90 || rotation == 270) {
                                        imageWidth = imageProxy.height
                                        imageHeight = imageProxy.width
                                    } else {
                                        imageWidth = imageProxy.width
                                        imageHeight = imageProxy.height
                                    }

                                    framesAnalyzed++

                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            codesInViewCount = barcodes.size
                                            val boxes = mutableListOf<Rect>()

                                            for (barcode in barcodes) {
                                                barcode.boundingBox?.let { boxes.add(it) }

                                                val rawValue = barcode.rawValue
                                                if (rawValue != null) {
                                                    try {
                                                        val bytes = Base64.decode(rawValue, Base64.DEFAULT)
                                                        val symbol = Symbol.deserialize(bytes)
                                                        if (symbol != null) {
                                                            if (decoder == null || decoder?.sessionId != symbol.sessionId) {
                                                                decoder = FountainDecoder(symbol.sessionId, symbol.totalChunks, symbol.chunkSize)
                                                            }

                                                            val currentDecoder = decoder!!
                                                            val complete = currentDecoder.addSymbol(symbol)
                                                            symbolsReceivedCount++

                                                            val now = System.currentTimeMillis()
                                                            val elapsed = now - lastThroughputCheck
                                                            if (elapsed >= 1000) {
                                                                val symDelta = symbolsReceivedCount - lastSymbolsCount
                                                                val bytesDelta = (currentDecoder.resolvedChunks.size * currentDecoder.chunkSize).toLong() - lastBytesCount
                                                                currentSymPerSec = (symDelta * 1000f / elapsed).toInt()
                                                                currentSpeedKbps = (bytesDelta / 1024f) * (1000f / elapsed)
                                                                lastThroughputCheck = now
                                                                lastSymbolsCount = symbolsReceivedCount
                                                                lastBytesCount = (currentDecoder.resolvedChunks.size * currentDecoder.chunkSize).toLong()
                                                            }

                                                            val pct = currentDecoder.progressPercentage()
                                                            progressPercent = pct
                                                            progressText = "Reconstructing: ${currentDecoder.resolvedChunks.size}/${currentDecoder.totalChunks} symbols ($pct%)"

                                                            if (complete && completedResult == null) {
                                                                val reconstructed = currentDecoder.reconstruct()
                                                                if (reconstructed.size >= 44) {
                                                                    val buf = ByteBuffer.wrap(reconstructed).order(ByteOrder.BIG_ENDIAN)
                                                                    val candidateLen = buf.int

                                                                    val (key, ciphertext) = if (candidateLen in 44..(reconstructed.size - 4)) {
                                                                        val inner = ByteArray(candidateLen)
                                                                        buf.get(inner)
                                                                        Pair(inner.copyOfRange(0, 32), inner.copyOfRange(32, candidateLen))
                                                                    } else {
                                                                        Pair(reconstructed.copyOfRange(0, 32), reconstructed.copyOfRange(32, reconstructed.size))
                                                                    }

                                                                    try {
                                                                        val decrypted = Crypto.decrypt(ciphertext, key)
                                                                        if (decrypted.size >= 2) {
                                                                            val decBuf = ByteBuffer.wrap(decrypted).order(ByteOrder.BIG_ENDIAN)
                                                                            val nameLen = decBuf.short.toInt() and 0xFFFF

                                                                            val (fileName, fileData) = if (nameLen in 1..(decrypted.size - 2)) {
                                                                                val nameBytes = ByteArray(nameLen)
                                                                                decBuf.get(nameBytes)
                                                                                val nameStr = String(nameBytes, Charsets.UTF_8)
                                                                                val dataBytes = ByteArray(decrypted.size - 2 - nameLen)
                                                                                decBuf.get(dataBytes)
                                                                                Pair(nameStr, dataBytes)
                                                                            } else {
                                                                                Pair("received_${System.currentTimeMillis()}.bin", decrypted)
                                                                            }

                                                                            saveToDownloads(ctx, fileName, fileData)

                                                                            val lower = fileName.lowercase()
                                                                            val isImg = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                                                                            val textPreview = if (!isImg && (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".csv"))) {
                                                                                String(fileData, Charsets.UTF_8).take(2000)
                                                                            } else null

                                                                            completedResult = ReceivedFileResult(
                                                                                fileName = fileName,
                                                                                data = fileData,
                                                                                isImage = isImg,
                                                                                textContent = textPreview
                                                                            )
                                                                        }
                                                                    } catch (_: Exception) {
                                                                        // Additional frames needed
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } catch (_: Exception) {
                                                        // Ignore corrupted or intermediate frames
                                                    }
                                                }
                                            }
                                            detectedBoxes = boxes
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                        } catch (e: Exception) {
                            progressText = "Camera notice: ${e.message}"
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Green Bounding Reticles for all detected QR codes
            if (detectedBoxes.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / imageWidth.toFloat()
                    val scaleY = size.height / imageHeight.toFloat()

                    for (box in detectedBoxes) {
                        val left = box.left * scaleX
                        val top = box.top * scaleY
                        val boxWidth = box.width() * scaleX
                        val boxHeight = box.height() * scaleY

                        drawRoundRect(
                            color = Color(0xFF238636),
                            topLeft = Offset(left, top),
                            size = Size(boxWidth, boxHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            } else {
                // Default Viewfinder Reticle Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(2.dp, Color(0x6658A6FF), RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

private fun saveToDownloads(context: Context, fileName: String, data: ByteArray) {
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(data)
            }
        }
    } catch (_: Exception) {
        // Failover
    }
}

private fun getMimeType(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".txt") -> "text/plain"
        lower.endsWith(".md") -> "text/markdown"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".pdf") -> "application/pdf"
        else -> "application/octet-stream"
    }
}
