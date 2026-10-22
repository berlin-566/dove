package com.dove.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.security.SecureRandom
import android.util.Base64

@Composable
fun SendScreen() {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

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
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            val encrypted = Crypto.encrypt(selectedFileBytes!!, key)
            val fullPayload = key + encrypted

            val sessionId = SecureRandom().nextLong()
            val chunkSize = 300
            val totalChunks = (fullPayload.size + chunkSize - 1) / chunkSize
            var seed = 1L

            val writer = QRCodeWriter()

            while (isStreaming) {
                val symbol = Symbol(sessionId, totalChunks, chunkSize, seed++, fullPayload.copyOfRange(0, minOf(chunkSize, fullPayload.size)))
                val raw = Base64.encodeToString(symbol.serialize(), Base64.NO_WRAP)

                try {
                    val bitMatrix = writer.encode(raw, BarcodeFormat.QR_CODE, 512, 512)
                    val width = bitMatrix.width
                    val height = bitMatrix.height
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    for (x in 0 until width) {
                        for (y in 0 until height) {
                            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                        }
                    }
                    currentBitmap = bmp
                    currentFrameIndex++
                } catch (e: Exception) {
                    // Ignore optical rendering skip
                }
                delay(70) // ~14 FPS
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
                    .size(320.dp)
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(6.dp))
                    .padding(16.dp),
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
                text = "Frame: $currentFrameIndex (14 FPS)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
