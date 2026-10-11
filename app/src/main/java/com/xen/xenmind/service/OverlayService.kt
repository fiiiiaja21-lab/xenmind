package com.xen.xenmind.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xen.xenmind.App
import com.xen.xenmind.R
import com.xen.xenmind.RobotMark
import com.xen.xenmind.data.Prefs
import com.xen.xenmind.data.XyrusClient
import com.xen.xenmind.ocr.OcrReader
import com.xen.xenmind.util.ScreenUtils
import kotlinx.coroutines.*

private val OV_BG = Color(0xFF0A0614)
private val OV_SURF = Color(0xFF150A26)
private val OV_NEON = Color(0xFFD946EF)
private val OV_VIOLET = Color(0xFFA855F7)
private val OV_VIOLET_DEEP = Color(0xFF7C3AED)
private val OV_TEXT = Color(0xFFF0E6FF)
private val OV_DIM = Color(0xFF9F8BC4)
private val OV_OK = Color(0xFF4ADE80)
private val OV_BAD = Color(0xFFFF4D6D)

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var wm: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: Prefs
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        prefs = Prefs(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startFg()
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startFg() {
        val notif = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIF_ID, notif)
    }

    private fun addOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 500
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent { Root(prefs, scope) }
        }
        wm.addView(composeView, params)
    }

    @Composable
    private fun Root(prefs: Prefs, scope: CoroutineScope) {
        var expanded by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("siap") }
        var statusGood by remember { mutableStateOf(true) }
        var busy by remember { mutableStateOf(false) }
        var output by remember { mutableStateOf("") }
        var manualNum by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf(0) } // 0=auto, 1=manual
        var autoLoop by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf("") }
        val ctx = this@OverlayService

        fun say(s: String, ok: Boolean = true) { status = s; statusGood = ok }

        suspend fun capture(): android.graphics.Bitmap? {
            val bmp = ScreenCaptureService.Shared.last ?: return null
            if (ScreenUtils.isMostlyBlack(bmp)) return null
            return bmp
        }

        suspend fun askAll(): String {
            val bmp = capture() ?: throw RuntimeException("capture belum aktif")
            val text = OcrReader.read(bmp)
            if (text.isBlank() || text.length < 15) throw RuntimeException("soal tidak terdeteksi")
            val client = XyrusClient(prefs.apiKey)
            val pair = client.askWithFallback(
                chain = prefs.modelChain,
                systemPrompt = client.systemUniversal,
                userContent = text,
                retries = prefs.retryCount
            ) { a, m -> say("coba $a · ${m.substringAfterLast('/')}") }
            say("via ${pair.second.substringAfterLast('/')}")
            return pair.first
        }

        suspend fun askSingle(num: String): String {
            val bmp = capture() ?: throw RuntimeException("capture belum aktif")
            val full = OcrReader.read(bmp)
            if (full.isBlank()) throw RuntimeException("soal tidak terdeteksi")
            val client = XyrusClient(prefs.apiKey)
            val pair = client.askWithFallback(
                chain = prefs.modelChain,
                systemPrompt = client.systemSingle,
                userContent = "Ini teks soal di layar. Cari dan jawab soal nomor $num saja.\n\n$full",
                retries = prefs.retryCount
            ) { a, m -> say("coba $a · ${m.substringAfterLast('/')}") }
            say("via ${pair.second.substringAfterLast('/')}")
            return pair.first
        }

        fun startAuto() {
            if (autoLoop) return
            autoLoop = true
            busy = true
            output = ""
            scope.launch {
                var iter = 0
                var lastFp = ""
                var sameCount = 0
                var emptyCount = 0

                while (autoLoop && isActive) {
                    iter++
                    progress = "iterasi $iter"
                    say("memproses #$iter...")
                    try {
                        val bmp = capture()
                        if (bmp == null) {
                            emptyCount++
                            say("tidak bisa capture / FLAG_SECURE", false)
                            if (emptyCount >= 3) { say("berhenti: capture gagal", false); break }
                            delay(800); continue
                        }
                        val text = OcrReader.read(bmp)
                        if (text.length < 15) {
                            emptyCount++
                            say("layar kosong / akhir", false)
                            if (emptyCount >= 3) { say("selesai · semua soal habis", true); break }
                            if (AutoScrollAccessibilityService.available()) {
                                AutoScrollAccessibilityService.instance?.swipeUp()
                            }
                            delay(1200); continue
                        }
                        emptyCount = 0
                        val fp = text.take(80)
                        if (fp == lastFp) {
                            sameCount++
                            if (sameCount >= 2) {
                                say("scroll mentok · selesai", true); break
                            }
                            if (AutoScrollAccessibilityService.available()) {
                                AutoScrollAccessibilityService.instance?.swipeUp()
                                delay(900)
                                continue
                            } else {
                                say("scroll gak tersedia · stop", false); break
                            }
                        } else {
                            sameCount = 0
                            lastFp = fp
                        }

                        val client = XyrusClient(prefs.apiKey)
                        val pair = client.askWithFallback(
                            chain = prefs.modelChain,
                            systemPrompt = client.systemUniversal,
                            userContent = text,
                            retries = prefs.retryCount
                        ) { a, m -> say("coba $a · ${m.substringAfterLast('/')}") }

                        output += "── LAYAR #$iter ──\n${pair.first}\n\n"
                        say("via ${pair.second.substringAfterLast('/')}")

                        if (AutoScrollAccessibilityService.available()) {
                            delay(400)
                            AutoScrollAccessibilityService.instance?.swipeUp()
                            delay(1000)
                        } else {
                            say("scroll manual: swipe ke bawah", false)
                            delay(3000)
                        }
                    } catch (e: Exception) {
                        say("gagal: ${e.message?.take(60)}", false)
                        delay(1500)
                    }
                }
                busy = false
                autoLoop = false
                progress = ""
            }
        }

        fun stopAuto() {
            autoLoop = false
            say("dihentikan")
        }

        fun manualAsk() {
            val num = manualNum.trim()
            if (num.isBlank()) { say("ketik nomor soal", false); return }
            busy = true
            scope.launch {
                say("cari soal no $num...")
                try {
                    val ans = askSingle(num)
                    output = "── SOAL NO $num ──\n$ans\n\n" + output
                    manualNum = ""
                } catch (e: Exception) {
                    say("gagal: ${e.message?.take(60)}", false)
                }
                busy = false
            }
        }

        fun copyOut() {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("xenmind", output))
            say("tersalin ke clipboard")
        }

        Box(
            modifier = Modifier.pointerInput(Unit) {
                detectDragGestures { _, drag ->
                    params.x = (params.x + drag.x.toInt()).coerceAtLeast(0)
                    params.y = (params.y + drag.y.toInt()).coerceAtLeast(0)
                    wm.updateViewLayout(composeView, params)
                }
            }
        ) {
            if (!expanded) {
                Bubble(busy) { expanded = true }
            } else {
                Panel(
                    mode = mode, onMode = { mode = it },
                    status = status, good = statusGood, busy = busy,
                    output = output, progress = progress,
                    manualNum = manualNum, onManualNum = { manualNum = it },
                    autoActive = autoLoop,
                    onAuto = { if (autoLoop) stopAuto() else startAuto() },
                    onManual = { manualAsk() },
                    onCopy = { copyOut() },
                    onClear = { output = ""; say("dibersihkan") },
                    onClose = { expanded = false }
                )
            }
        }
    }

    @Composable
    private fun Bubble(busy: Boolean, onClick: () -> Unit) {
        val tr = rememberInfiniteTransition(label = "b")
        val pulse by tr.animateFloat(0.9f, 1.06f,
            infiniteRepeatable(tween(if (busy) 700 else 1500), RepeatMode.Reverse),
            label = "bp")
        Box(
            modifier = Modifier
                .size(66.dp)
                .shadow((22 * pulse).dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(OV_VIOLET_DEEP, OV_NEON)))
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .pointerInput(Unit) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.Center
        ) {
            RobotMark(size = 44.dp)
        }
    }

    @Composable
    private fun Panel(
        mode: Int, onMode: (Int) -> Unit,
        status: String, good: Boolean, busy: Boolean,
        output: String, progress: String,
        manualNum: String, onManualNum: (String) -> Unit,
        autoActive: Boolean,
        onAuto: () -> Unit, onManual: () -> Unit,
        onCopy: () -> Unit, onClear: () -> Unit,
        onClose: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .width(350.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(OV_BG, OV_SURF)))
                .border(1.dp,
                    Brush.linearGradient(listOf(OV_NEON.copy(alpha = 0.6f), OV_VIOLET.copy(alpha = 0.2f))),
                    RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RobotMark(size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("XENMIND", color = OV_NEON, fontSize = 13.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("AUTO-SCROLL · UNIVERSAL", color = OV_DIM, fontSize = 8.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
                }
                TextButton(onClick = onClose) {
                    Text("[-]", color = OV_DIM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OV_BG.copy(alpha = 0.6f))
                    .padding(3.dp)
            ) {
                TabBtn("AUTO JAWAB", mode == 0, Modifier.weight(1f)) { onMode(0) }
                TabBtn("MINTA JAWABAN", mode == 1, Modifier.weight(1f)) { onMode(1) }
            }
            Spacer(Modifier.height(10.dp))

            if (mode == 0) {
                Column {
                    NeonBtn(if (autoActive) "STOP AUTO JAWAB" else "MULAI AUTO JAWAB",
                        if (autoActive) OV_BAD else OV_VIOLET, busy) { onAuto() }
                    if (progress.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(progress, color = OV_NEON, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("AI baca soal di layar, jawab, scroll sendiri sampai habis.",
                        color = OV_DIM, fontSize = 10.sp, lineHeight = 14.sp)
                }
            } else {
                Column {
                    Text("MASUKKAN NOMOR SOAL", color = OV_NEON, fontSize = 9.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OV_BG)
                            .border(1.dp, OV_VIOLET.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        if (manualNum.isEmpty()) {
                            Text("contoh: 3", color = OV_DIM.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = manualNum, onValueChange = onManualNum,
                            textStyle = TextStyle(color = OV_TEXT, fontSize = 13.sp),
                            cursorBrush = SolidColor(OV_NEON),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NeonBtn("MINTA JAWABAN", OV_VIOLET, busy) { onManual() }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (output.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HASIL", color = OV_NEON, fontSize = 9.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onCopy) {
                        Text("COPY", color = OV_NEON, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onClear) {
                        Text("CLEAR", color = OV_DIM, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OV_BG.copy(alpha = 0.7f))
                        .border(1.dp, OV_VIOLET.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    val sc = rememberScrollState()
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(sc)) {
                        Text(output, color = OV_TEXT, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (good) OV_OK else OV_BAD, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status, color = if (good) OV_OK else OV_BAD,
                    fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    @Composable
    private fun TabBtn(t: String, a: Boolean, m: Modifier, onClick: () -> Unit) {
        Box(
            modifier = m
                .clip(RoundedCornerShape(8.dp))
                .background(if (a) OV_VIOLET_DEEP.copy(alpha = 0.4f) else Color.Transparent)
                .pointerInput(Unit) { detectTapGestures { onClick() } }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(t,
                color = if (a) OV_TEXT else OV_DIM,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }

    @Composable
    private fun NeonBtn(t: String, c: Color, busy: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (busy) c.copy(alpha = 0.4f) else c)
                .pointerInput(Unit) { detectTapGestures { if (!busy) onClick() } },
            contentAlignment = Alignment.Center
        ) {
            Text(t, color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        }
    }

    override fun onDestroy() {
        try { wm.removeView(composeView) } catch (_: Throwable) {}
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object { const val NOTIF_ID = 4243 }
}
