package com.xen.xenmind

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xen.xenmind.data.Prefs
import com.xen.xenmind.service.OverlayService
import com.xen.xenmind.service.ScreenCaptureService

private val P1 = Color(0xFF0A0614)
private val P2 = Color(0xFF150A26)
private val P3 = Color(0xFF1E1035)
private val Neon = Color(0xFFD946EF)
private val Violet = Color(0xFFA855F7)
private val VioletDeep = Color(0xFF7C3AED)
private val TxtLight = Color(0xFFF0E6FF)
private val TxtDim = Color(0xFF9F8BC4)
private val Ok = Color(0xFF4ADE80)
private val Bad = Color(0xFFFF4D6D)

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
        var captureGranted by remember { mutableStateOf(false) }
        var apiKey by remember { mutableStateOf(prefs.apiKey) }
        var chain by remember { mutableStateOf(prefs.modelChain.joinToString(", ")) }
        var status by remember { mutableStateOf("") }
        var statusGood by remember { mutableStateOf(true) }

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
                captureGranted = true
                status = "Screen capture siap"; statusGood = true
            } else {
                captureGranted = false
                status = "Capture dibatalkan"; statusGood = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(P2, P1), Offset(0f, 0f), 1600f))
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-80).dp, y = (-100).dp)
                    .blur(150.dp)
                    .background(Neon.copy(alpha = 0.25f), CircleShape)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RobotMark(size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "XENMIND",
                            color = TxtLight,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = LocalTextStyle.current.copy(
                                shadow = Shadow(Neon, Offset.Zero, 28f)
                            )
                        )
                        Text(
                            "AI OVERLAY · UNIVERSAL",
                            color = Neon, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                        )
                    }
                }
                Text(
                    "AI baca soal di layar, jawab otomatis, scroll sendiri sampai habis. Support pilihan ganda & essay.",
                    color = TxtDim, fontSize = 12.sp,
                    textAlign = TextAlign.Center, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                GlassCard {
                    CardTitle("API CONFIG")
                    Spacer(Modifier.height(12.dp))
                    Field("XYRUS API KEY", apiKey, "xyrz-xxxxx...") {
                        apiKey = it; prefs.apiKey = it
                    }
                    Spacer(Modifier.height(12.dp))
                    Field("MODEL CHAIN", chain, "xyrz/gpt-5.6-luna, ...") {
                        chain = it
                        prefs.modelChain = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                    }
                }

                GlassCard {
                    CardTitle("PERMISSIONS")
                    Spacer(Modifier.height(12.dp))
                    PermRow("Overlay", "Bubble ngambang di atas app lain", hasOverlay) {
                        overlayPerm.launch(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        ))
                    }
                    Spacer(Modifier.height(8.dp))
                    PermRow("Screen Capture", "Baca soal di layar", captureGranted) {
                        val mpm = ctx.getSystemService(
                            android.media.projection.MediaProjectionManager::class.java
                        )
                        capturePerm.launch(mpm.createScreenCaptureIntent())
                    }
                }

                val ready = hasOverlay && captureGranted && apiKey.isNotBlank()

                NeonBtn("MULAI OVERLAY", ready) {
                    if (!Settings.canDrawOverlays(ctx)) {
                        status = "Izinkan overlay dulu"; statusGood = false; return@NeonBtn
                    }
                    if (!captureGranted) {
                        status = "Izinkan screen capture dulu"; statusGood = false; return@NeonBtn
                    }
                    if (apiKey.isBlank()) {
                        status = "Isi API key dulu"; statusGood = false; return@NeonBtn
                    }
                    ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
                    status = "Overlay aktif. Balik ke app ujian."
                    statusGood = true
                }

                StopBtn {
                    ctx.stopService(Intent(ctx, OverlayService::class.java))
                    ctx.stopService(Intent(ctx, ScreenCaptureService::class.java))
                    captureGranted = false
                    status = "Dihentikan"; statusGood = true
                }

                if (status.isNotBlank()) StatusBar(status, statusGood)

                Spacer(Modifier.height(20.dp))
                Text(
                    "v1.0 · xenmind · powered by xyrus router",
                    color = TxtDim.copy(alpha = 0.6f),
                    fontSize = 10.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    @Composable fun CardTitle(t: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Neon, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(t, color = Neon, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    }

    @Composable fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(P3.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(
                        Violet.copy(alpha = 0.5f),
                        Neon.copy(alpha = 0.15f)
                    )),
                    RoundedCornerShape(18.dp)
                )
                .padding(18.dp)
        ) { content() }
    }

    @Composable fun Field(label: String, value: String, ph: String, on: (String) -> Unit) {
        Column {
            Text(label, color = TxtDim, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 6.dp))
            OutlinedTextField(
                value = value, onValueChange = on,
                placeholder = { Text(ph, color = TxtDim.copy(alpha = 0.5f), fontSize = 13.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = TxtLight, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Neon,
                    unfocusedBorderColor = Violet.copy(alpha = 0.35f),
                    cursorColor = Neon
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable fun PermRow(label: String, desc: String, granted: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (granted) Ok.copy(alpha = 0.1f) else Violet.copy(alpha = 0.08f))
                .border(1.dp,
                    if (granted) Ok.copy(alpha = 0.4f) else Violet.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (granted) Ok.copy(alpha = 0.2f) else Violet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (granted) "OK" else "!",
                    color = if (granted) Ok else Violet,
                    fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TxtLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = TxtDim, fontSize = 10.sp, lineHeight = 13.sp)
            }
            if (granted) {
                Text("AKTIF", color = Ok, fontSize = 11.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            } else {
                TextButton(onClick = onClick) {
                    Text("IZIN", color = Neon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    @Composable fun NeonBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(if (enabled) 20.dp else 0.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(VioletDeep, Neon))
                    else Brush.horizontalGradient(listOf(P3, P3))
                )
                .border(1.dp,
                    if (enabled) Neon.copy(alpha = 0.6f) else TxtDim.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp))
        ) {
            TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
                Text(text,
                    color = if (enabled) TxtLight else TxtDim.copy(alpha = 0.5f),
                    fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
    }

    @Composable fun StopBtn(onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bad.copy(alpha = 0.15f))
                .border(1.dp, Bad.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            TextButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Text("STOP OVERLAY", color = Bad, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }
    }

    @Composable fun StatusBar(msg: String, good: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (good) Ok.copy(alpha = 0.12f) else Bad.copy(alpha = 0.12f))
                .border(1.dp,
                    (if (good) Ok else Bad).copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(msg,
                color = if (good) Ok else Bad,
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RobotMark(size: androidx.compose.ui.unit.Dp) {
    val infinite = rememberInfiniteTransition(label = "robot")
    val pulse by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "robotPulse"
    )
    Box(
        modifier = Modifier
            .size(size)
            .shadow((16 * pulse).dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(VioletDeep, Neon)))
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.055f

            // antenna
            drawLine(Color.White, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.24f), stroke)
            drawCircle(Color.White, w * 0.05f, Offset(w * 0.5f, h * 0.1f))

            // head
            drawRoundRect(
                Color.White,
                Offset(w * 0.22f, h * 0.26f),
                androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.5f),
                androidx.compose.ui.geometry.CornerRadius(w * 0.12f)
            )

            // eyes
            drawCircle(Color(0xFF0A0614), w * 0.06f, Offset(w * 0.37f, h * 0.48f))
            drawCircle(Color(0xFF0A0614), w * 0.06f, Offset(w * 0.63f, h * 0.48f))

            // mouth
            drawLine(Color(0xFF0A0614),
                Offset(w * 0.4f, h * 0.62f),
                Offset(w * 0.6f, h * 0.62f), stroke * 0.7f)
        }
    }
}
