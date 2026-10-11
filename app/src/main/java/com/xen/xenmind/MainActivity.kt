package com.xen.xenmind

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.xen.xenmind.data.Prefs
import com.xen.xenmind.service.OverlayService
import com.xen.xenmind.service.ScreenCaptureService

private val P1 = Color(0xFF0A0614)   // deepest
private val P2 = Color(0xFF150A26)   // bg
private val P3 = Color(0xFF1E1035)   // surface
private val Neon = Color(0xFFD946EF) // glow
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
                status = "Screen capture siap"
                statusGood = true
            } else {
                captureGranted = false
                status = "Capture dibatalkan"
                statusGood = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(P2, P1),
                        center = Offset(0f, 0f),
                        radius = 1600f
                    )
                )
        ) {
            // ambient glow top
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .offset(x = (-80).dp, y = (-100).dp)
                    .blur(140.dp)
                    .background(Neon.copy(alpha = 0.25f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(x = 200.dp, y = 500.dp)
                    .blur(140.dp)
                    .background(VioletDeep.copy(alpha = 0.28f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                // ─────── HEADER ───────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RobotMark(size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "XENMIND",
                            color = TxtLight,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = LocalTextStyle.current.copy(
                                shadow = Shadow(Neon, Offset.Zero, 24f)
                            )
                        )
                        Text(
                            "AI OVERLAY ASSISTANT",
                            color = Neon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Overlay pintar buat bantu jawab soal di layar. Aktifkan izin, tap MULAI, dan bubble robot akan muncul.",
                    color = TxtDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // ─────── CARD 1: API ───────
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonDot()
                        Spacer(Modifier.width(10.dp))
                        Text("API CONFIG", color = Neon, fontSize = 11.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(14.dp))

                    LabeledField(
                        label = "XYRUS API KEY",
                        value = apiKey,
                        placeholder = "xyrz-xxxxx...",
                        onValueChange = { apiKey = it; prefs.apiKey = it },
                        isSecret = true
                    )
                    Spacer(Modifier.height(12.dp))

                    val chainStr = prefs.modelChain.joinToString(", ")
                    LabeledField(
                        label = "MODEL CHAIN",
                        value = chainStr,
                        placeholder = "xyrz/gpt-5.6-luna, ...",
                        onValueChange = {
                            prefs.modelChain = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                        },
                        isSecret = false
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Model akan dicoba berurutan kalau salah satu error.",
                        color = TxtDim, fontSize = 10.sp, lineHeight = 14.sp
                    )
                }

                // ─────── CARD 2: PERMISSION ───────
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonDot()
                        Spacer(Modifier.width(10.dp))
                        Text("PERMISSIONS", color = Neon, fontSize = 11.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(14.dp))

                    PermissionRow(
                        label = "Overlay",
                        desc = "Biar bubble muncul di atas app lain",
                        granted = hasOverlay
                    ) {
                        overlayPerm.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")))
                    }
                    Spacer(Modifier.height(10.dp))
                    PermissionRow(
                        label = "Screen Capture",
                        desc = "Biar bisa baca soal di layar",
                        granted = captureGranted
                    ) {
                        val mpm = ctx.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        capturePerm.launch(mpm.createScreenCaptureIntent())
                    }
                }

                // ─────── CTA ───────
                Spacer(Modifier.height(4.dp))

                val ready = hasOverlay && captureGranted && apiKey.isNotBlank()

                NeonButton(
                    text = "MULAI OVERLAY",
                    enabled = ready,
                    onClick = {
                        if (!Settings.canDrawOverlays(ctx)) {
                            status = "Izinkan overlay dulu"; statusGood = false; return@NeonButton
                        }
                        if (!captureGranted) {
                            status = "Izinkan screen capture dulu"; statusGood = false; return@NeonButton
                        }
                        if (apiKey.isBlank()) {
                            status = "Isi API key dulu"; statusGood = false; return@NeonButton
                        }
                        ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
                        status = "Overlay aktif. Balik ke app ujian."
                        statusGood = true
                    }
                )

                Spacer(Modifier.height(4.dp))
                StopButton {
                    ctx.stopService(Intent(ctx, OverlayService::class.java))
                    ctx.stopService(Intent(ctx, ScreenCaptureService::class.java))
                    captureGranted = false
                    status = "Overlay dihentikan"
                    statusGood = true
                }

                if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (statusGood) Ok.copy(alpha = 0.12f)
                                else Bad.copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                (if (statusGood) Ok else Bad).copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            status,
                            color = if (statusGood) Ok else Bad,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    "v1.0 · xenmind · powered by xyrus router",
                    color = TxtDim.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // ══════════════════════════════════════════════════
    // COMPONENTS
    // ══════════════════════════════════════════════════

    @Composable
    fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(P3.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Violet.copy(alpha = 0.5f),
                            Neon.copy(alpha = 0.15f)
                        )
                    ),
                    RoundedCornerShape(18.dp)
                )
                .padding(18.dp)
        ) {
            Column(content = content)
        }
    }

    @Composable
    fun LabeledField(
        label: String,
        value: String,
        placeholder: String,
        onValueChange: (String) -> Unit,
        isSecret: Boolean
    ) {
        Column {
            Text(
                label,
                color = TxtDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = TxtDim.copy(alpha = 0.5f), fontSize = 13.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = TxtLight, fontSize = 13.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Neon,
                    unfocusedBorderColor = Violet.copy(alpha = 0.35f),
                    cursorColor = Neon
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    fun PermissionRow(
        label: String,
        desc: String,
        granted: Boolean,
        onClick: () -> Unit
    ) {
        val bg by animateColorAsState(
            if (granted) Ok.copy(alpha = 0.1f) else Violet.copy(alpha = 0.08f),
            animationSpec = tween(300),
            label = "permBg"
        )
        val border by animateColorAsState(
            if (granted) Ok.copy(alpha = 0.4f) else Violet.copy(alpha = 0.3f),
            animationSpec = tween(300),
            label = "permBorder"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) Ok.copy(alpha = 0.2f)
                        else Violet.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (granted) "OK" else "-",
                    color = if (granted) Ok else Violet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TxtLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = TxtDim, fontSize = 10.sp, lineHeight = 13.sp)
            }
            if (!granted) {
                TextButton(onClick = onClick) {
                    Text("IZIN", color = Neon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text(
                    "AKTIF",
                    color = Ok,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }

    @Composable
    fun NeonButton(text: String, enabled: Boolean, onClick: () -> Unit) {
        val bg = if (enabled) {
            Brush.horizontalGradient(listOf(VioletDeep, Neon))
        } else {
            Brush.horizontalGradient(listOf(P3, P3))
        }
        val glow = if (enabled) Neon.copy(alpha = 0.5f) else Color.Transparent

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(if (enabled) 20.dp else 0.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(
                    1.dp,
                    if (enabled) Neon.copy(alpha = 0.6f) else TxtDim.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp)
                )
        ) {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text,
                    color = if (enabled) TxtLight else TxtDim.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }

    @Composable
    fun StopButton(onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bad.copy(alpha = 0.15f))
                .border(1.dp, Bad.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Text("STOP OVERLAY", color = Bad, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }
    }

    @Composable
    fun NeonDot() {
        Box(
            modifier = Modifier
                .size(8.dp)
                .shadow(8.dp, CircleShape)
                .background(Neon, CircleShape)
        )
    }
}

// ══════════════════════════════════════════════════════
// ROBOT LOGO (vector canvas)
// ══════════════════════════════════════════════════════
@Composable
fun RobotMark(size: androidx.compose.ui.unit.Dp, glow: Boolean = true) {
    val infinite = rememberInfiniteTransition(label = "robot")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(size)
            .shadow(if (glow) (18 * pulse).dp else 0.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(VioletDeep, Neon))
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.06f

            // antenna
            drawLine(
                color = Color.White,
                start = Offset(w * 0.5f, h * 0.12f),
                end = Offset(w * 0.5f, h * 0.24f),
                strokeWidth = stroke
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.05f,
                center = Offset(w * 0.5f, h * 0.1f)
            )

            // head
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.22f, h * 0.26f),
                size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f)
            )
            // eyes
            drawCircle(Color(0xFF0A0614), w * 0.06f, Offset(w * 0.37f, h * 0.48f))
            drawCircle(Color(0xFF0A0614), w * 0.06f, Offset(w * 0.63f, h * 0.48f))
            // mouth
            drawLine(
                color = Color(0xFF0A0614),
                start = Offset(w * 0.4f, h * 0.62f),
                end = Offset(w * 0.6f, h * 0.62f),
                strokeWidth = stroke * 0.7f
            )
        }
    }
}
