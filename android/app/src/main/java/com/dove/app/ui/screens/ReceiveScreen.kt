package com.dove.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dove.app.crypto.Crypto
import com.dove.app.fountain.FountainDecoder
import com.dove.app.fountain.Symbol
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import android.util.Base64
import java.util.concurrent.Executors

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

    var progressText by remember { mutableStateOf("Ready to scan") }
    var progressPercent by remember { mutableStateOf(0) }
    var decoder by remember { mutableStateOf<FountainDecoder?>(null) }
    var isDone by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera permission is required to scan optical QR streams.",
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
                Text(
                    text = "Optical Receiver",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressText,
                    fontSize = 13.sp,
                    color = if (isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val executor = Executors.newSingleThreadExecutor()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                try {
                                    if (!isDone) {
                                        val rawValue = decodeQrFromImageProxy(imageProxy)
                                        if (rawValue != null) {
                                            val bytes = Base64.decode(rawValue, Base64.DEFAULT)
                                            val symbol = Symbol.deserialize(bytes)
                                            if (symbol != null) {
                                                if (decoder == null || decoder?.sessionId != symbol.sessionId) {
                                                    decoder = FountainDecoder(symbol.sessionId, symbol.totalChunks, symbol.chunkSize)
                                                }

                                                val currentDecoder = decoder!!
                                                val complete = currentDecoder.addSymbol(symbol)
                                                val pct = currentDecoder.progressPercentage()

                                                progressPercent = pct
                                                progressText = "Reconstructing: ${currentDecoder.resolvedChunks.size}/${currentDecoder.totalChunks} symbols ($pct%)"

                                                if (complete && !isDone) {
                                                    isDone = true
                                                    val reconstructed = currentDecoder.reconstruct()
                                                    if (reconstructed.size > 44) {
                                                        val key = reconstructed.copyOfRange(0, 32)
                                                        val ciphertext = reconstructed.copyOfRange(32, reconstructed.size)
                                                        val decrypted = Crypto.decrypt(ciphertext, key)

                                                        saveToDownloads(ctx, decrypted)
                                                        progressText = "File Transferred and Saved to Downloads"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Ignore parse errors on intermediate frames
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                        } catch (e: Exception) {
                            progressText = "Camera initialization notice: ${e.message}"
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun decodeQrFromImageProxy(image: ImageProxy): String? {
    return try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride

        val yuvBytes = if (rowStride == width) {
            bytes
        } else {
            val clean = ByteArray(width * height)
            for (y in 0 until height) {
                System.arraycopy(bytes, y * rowStride, clean, y * width, width)
            }
            clean
        }

        val source = PlanarYUVLuminanceSource(
            yuvBytes, width, height, 0, 0, width, height, false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        val result = reader.decodeWithState(binaryBitmap)
        reader.reset()
        result.text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun saveToDownloads(context: Context, data: ByteArray) {
    try {
        val fileName = "dove_received_${System.currentTimeMillis()}.bin"
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
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
        // Safe failover
    }
}
