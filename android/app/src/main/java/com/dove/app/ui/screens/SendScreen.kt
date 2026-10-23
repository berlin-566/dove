package com.dove.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dove.app.crypto.Crypto
import com.dove.app.fountain.Symbol
import com.dove.app.fountain.pickIndices
import com.dove.app.settings.SettingsManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

@Composable
fun SendScreen() {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val transmissionFps = remember { SettingsManager.getTransmissionFps(context) }
    val gridLayout = remember { SettingsManager.getGridLayout(context) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                selectedFileBytes = bytes
                selectedFileName = uri.lastPathSegment ?: "file.bin"
            }
        }
    }

    LaunchedEffect(isStreaming) {
        if (isStreaming && selectedFileBytes != null) {
            val fileName = selectedFileName ?: "file.bin"
            val fileNameBytes = fileName.toByteArray(Charsets.UTF_8)
            val fileBytes = selectedFileBytes!!

            // Pack metadata: [2 bytes fileNameLen][fileName][fileBytes]
            val packedData = ByteBuffer.allocate(2 + fileNameBytes.size + fileBytes.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort(fileNameBytes.size.toShort())
                .put(fileNameBytes)
                .put(fileBytes)
                .array()

            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            val encrypted = Crypto.encrypt(packedData, key)

            // Inner payload: [key (32)][encrypted]
            val innerPayload = key + encrypted

            // Exact payload: [4 bytes innerPayloadLen][innerPayload]
            val fullPayload = ByteBuffer.allocate(4 + innerPayload.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(innerPayload.size)
                .put(innerPayload)
                .array()

            val sessionId = SecureRandom().nextLong()
            val chunkSize = when {
                fullPayload.size >= 800000 -> 380
                fullPayload.size >= 200000 -> 320
                fullPayload.size >= 50000 -> 256
                else -> 128
            }
            val totalChunks = (fullPayload.size + chunkSize - 1) / chunkSize

            // Slice fullPayload into fixed-size chunks
            val chunks = Array(totalChunks) { i ->
                val start = i * chunkSize
                val end = minOf(start + chunkSize, fullPayload.size)
                val chunk = ByteArray(chunkSize)
                System.arraycopy(fullPayload, start, chunk, 0, end - start)
                chunk
            }

            var seed = 1L
            val writer = QRCodeWriter()
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1
            )
            val isDual = gridLayout == "1x2"
            val delayMs = (1000f / transmissionFps).toLong().coerceAtLeast(16L)

            while (isStreaming) {
                try {
                    if (isDual) {
                        // 1x2 Dual Stack Mode
                        val bmp1 = generateQrBitmap(writer, sessionId, totalChunks, chunkSize, seed++, chunks, hints, 400, 400)
                        val bmp2 = generateQrBitmap(writer, sessionId, totalChunks, chunkSize, seed++, chunks, hints, 400, 400)

                        val combinedBmp = Bitmap.createBitmap(400, 816, Bitmap.Config.RGB_565)
                        val canvas = Canvas(combinedBmp)
                        canvas.drawColor(AndroidColor.BLACK)
                        canvas.drawBitmap(bmp1, 0f, 0f, null)
                        canvas.drawBitmap(bmp2, 0f, 416f, null)
                        currentBitmap = combinedBmp
                        currentFrameIndex += 2
                    } else {
                        // 1x1 Standard Mode
                        val bmp = generateQrBitmap(writer, sessionId, totalChunks, chunkSize, seed++, chunks, hints, 512, 512)
                        currentBitmap = bmp
                        currentFrameIndex++
                    }
                } catch (_: Exception) {
                    // Skip on render failure
                }
                delay(delayMs)
            }
        }
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
                    text = "Optical Transmitter",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (selectedFileName != null) "File: $selectedFileName (${selectedFileBytes?.size ?: 0} bytes)" else "No file selected",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Layout: $gridLayout | Speed: ${transmissionFps.toInt()} FPS",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Select File", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (selectedFileBytes != null) {
                        Button(
                            onClick = { isStreaming = !isStreaming },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(if (isStreaming) "Stop Stream" else "Start Stream")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isStreaming && currentBitmap != null) {
            Box(
                modifier = Modifier
                    .size(if (gridLayout == "1x2") 380.dp else 320.dp)
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = "Optical Stream Frame",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Symbols Broadcast: $currentFrameIndex (${transmissionFps.toInt()} FPS)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun generateQrBitmap(
    writer: QRCodeWriter,
    sessionId: Long,
    totalChunks: Int,
    chunkSize: Int,
    seed: Long,
    chunks: Array<ByteArray>,
    hints: Map<EncodeHintType, Any>,
    width: Int,
    height: Int
): Bitmap {
    val indices = pickIndices(seed, totalChunks)
    val combined = ByteArray(chunkSize)
    for (idx in indices) {
        val src = chunks[idx]
        for (b in 0 until chunkSize) {
            combined[b] = (combined[b].toInt() xor src[b].toInt()).toByte()
        }
    }

    val symbol = Symbol(sessionId, totalChunks, chunkSize, seed, combined)
    val raw = Base64.encodeToString(symbol.serialize(), Base64.NO_WRAP)
    val bitMatrix = writer.encode(raw, BarcodeFormat.QR_CODE, width, height, hints)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp
}
