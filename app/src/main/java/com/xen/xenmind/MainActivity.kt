package com.xen.xenmind

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xen.xenmind.data.Prefs
import com.xen.xenmind.service.OverlayService
import com.xen.xenmind.service.ScreenCaptureService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppUI() }
    }

    @Composable
    fun AppUI() {
        val ctx = LocalContext.current
        val prefs = remember { Prefs(ctx) }
        var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
        var apiKey by remember { mutableStateOf(prefs.apiKey) }
        var chainText by remember { mutableStateOf(prefs.modelChain.joinToString(", ")) }
        var status by remember { mutableStateOf("") }

        val overlayPerm = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { hasOverlay = Settings.canDrawOverlays(ctx) }

        val capturePerm = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { r ->
            if (r.resultCode == RESULT_OK && r.data != null) {
                val i = Intent(ctx, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, r.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, r.data)
                }
                ctx.startForegroundService(i)
                status = "Screen capture aktif"
            } else {
                status = "Capture dibatalkan"
            }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0F0819), Color(0xFF190C2A))))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("◤ XENMIND ◢", color = Color(0xFFD946EF), fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Overlay AI untuk bantu jawab soal di layar.",
                    color = Color(0xFFA08CC8), fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))

                Spacer(Modifier.height(4.dp))
                Head("1. API & Model")
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it; prefs.apiKey = it },
                    label = { Text("Xyrus API Key") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = chainText,
                    onValueChange = { chainText = it; prefs.modelChain = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() } },
                    label = { Text("Model chain (koma)") },
                    modifier = Modifier.fillMaxWidth())

                Head("2. Permission")
                OutBtn(if (hasOverlay) "Overlay: ✓" else "Izinkan Overlay") {
                    overlayPerm.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")))
                }
                OutBtn("Izinkan Screen Capture") {
                    val mpm = ctx.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                    capturePerm.launch(mpm.createScreenCaptureIntent())
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(ctx)) { status = "Izinkan overlay dulu"; return@Button }
                        if (apiKey.isBlank()) { status = "Isi API key dulu"; return@Button }
                        ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
                        status = "Overlay jalan. Balik ke app ujian."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7))
                ) { Text("MULAI OVERLAY", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = {
                        ctx.stopService(Intent(ctx, OverlayService::class.java))
                        ctx.stopService(Intent(ctx, ScreenCaptureService::class.java))
                        status = "Dihentikan"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5078))
                ) { Text("STOP") }

                if (status.isNotBlank()) Text(status, color = Color(0xFF50FFA0), fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    @Composable fun Head(t: String) =
        Text(t, color = Color(0xFFD946EF), fontSize = 14.sp, fontWeight = FontWeight.Bold)

    @Composable fun OutBtn(label: String, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Text(label, color = Color(0xFFF0E6FF))
        }
    }
}
