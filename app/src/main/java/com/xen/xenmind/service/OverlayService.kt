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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
            setContent {
                OverlayRoot(prefs = prefs, scope = scope)
            }
        }
        wm.addView(composeView, params)
    }

    @Composable
    private fun OverlayRoot(prefs: Prefs, scope: CoroutineScope) {
        var expanded by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(0) } // 0 = Jawab, 1 = Chat
        var status by remember { mutableStateOf("siap") }
        var statusGood by remember { mutableStateOf(true) }
        var soal by remember { mutableStateOf("") }
        var jawaban by remember { mutableStateOf("") }
        var chatInput by remember { mutableStateOf("") }
        var chatHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }
        var busy by remember { mutableStateOf(false) }

        val ctx = this@OverlayService

        fun askAuto() {
            val bmp = ScreenCaptureService.Shared.last
            if (bmp == null) { status = "capture belum aktif"; statusGood = false; return }
            if (ScreenUtils.isMostlyBlack(bmp)) { status = "layar FLAG_SECURE"; statusGood = false; return }
            status = "baca soal..."; statusGood = true; busy = true
            scope.launch {
                try {
                    val text = OcrReader.read(bmp)
                    soal = text
                    if (text.isBlank()) { status = "tidak ada teks"; statusGood = false; busy = false; return@launch }
                    status = "kirim ke AI..."
                    val client = XyrusClient(prefs.apiKey)
                    val pair = client.askWithFallback(
                        chain = prefs.modelChain,
                        systemPrompt = client.systemPrompt(prefs.mode),
                        userContent = text,
                        retries = prefs.retryCount
                    ) { attempt, model ->
                        status = "coba $attempt · ${model.substringAfterLast('/')}"
                    }
                    jawaban = pair.first
                    status = "via ${pair.second.substringAfterLast('/')}"
                    statusGood = true
                } catch (e: Exception) {
                    status = "gagal: ${e.message?.take(80)}"
                    statusGood = false
                }
                busy = false
            }
        }

        fun sendChat() {
            val msg = chatInput.trim()
            if (msg.isBlank()) return
            chatHistory = chatHistory + ("you" to msg)
            chatInput = ""
            busy = true
            status = "chat..."
            scope.launch {
                try {
                    val client = XyrusClient(prefs.apiKey)
                    val pair = client.askWithFallback(
                        chain = prefs.modelChain,
                        systemPrompt = "Kamu asisten ramah. Jawab singkat dan jelas dalam Bahasa Indonesia.",
                        userContent = msg,
                        retries = prefs.retryCount
                    ) { attempt, model ->
                        status = "coba $attempt · ${model.substringAfterLast('/')}"
                    }
                    chatHistory = chatHistory + ("ai" to pair.first)
                    status = "via ${pair.second.substringAfterLast('/')}"
                    statusGood = true
                } catch (e: Exception) {
                    chatHistory = chatHistory + ("ai" to "error: ${e.message?.take(80)}")
                    status = "chat gagal"
                    statusGood = false
                }
                busy = false
            }
        }

        fun doCopy(text: String) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("xenmind", text))
            status = "tersalin"
            statusGood = true
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
                FloatingBubble(busy = busy) { expanded = true }
            } else {
                PanelBody(
                    tab = tab, onTab = { tab = it },
                    status = status, statusGood = statusGood, busy = busy,
                    soal = soal, jawaban = jawaban,
                    chatHistory = chatHistory, chatInput = chatInput,
                    onChatInput = { chatInput = it },
                    onSend = { sendChat() },
                    onAsk = { askAuto() },
                    onCopy = { doCopy(if (tab == 0) jawaban else chatHistory.lastOrNull { it.first == "ai" }?.second ?: "") },
                    onClear = {
                        if (tab == 0) { soal = ""; jawaban = ""; status = "siap"; statusGood = true }
                        else { chatHistory = emptyList(); status = "siap"; statusGood = true }
                    },
                    onClose = { expanded = false }
                )
            }
        }
    }

    // ══════════════════════════════════════════
    @Composable
    private fun FloatingBubble(busy: Boolean, onClick: () -> Unit) {
        val infinite = rememberInfiniteTransition(label = "bubble")
        val pulse by infinite.animateFloat(
            initialValue = 0.9f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                tween(if (busy) 700 else 1400, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ), label = "pulseBubble"
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow((22 * pulse).dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(OV_VIOLET_DEEP, OV_NEON)))
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { onClick() }
                },
            contentAlignment = Alignment.Center
        ) {
            RobotMark(size = 44.dp, glow = false)
        }
    }

    @Composable
    private fun PanelBody(
        tab: Int, onTab: (Int) -> Unit,
        status: String, statusGood: Boolean, busy: Boolean,
        soal: String, jawaban: String,
        chatHistory: List<Pair<String, String>>, chatInput: String,
        onChatInput: (String) -> Unit,
        onSend: () -> Unit, onAsk: () -> Unit,
        onCopy: () -> Unit, onClear: () -> Unit,
        onClose: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(OV_BG, OV_SURF))
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(OV_NEON.copy(alpha = 0.6f), OV_VIOLET.copy(alpha = 0.2f))),
                    RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                RobotMark(size = 28.dp, glow = true)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("XENMIND", color = OV_NEON, fontSize = 12.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("AI OVERLAY", color = OV_DIM, fontSize = 9.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
                }
                Text(
                    if (busy) "..." else "ok",
                    color = if (busy) OV_NEON else OV_OK,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = onClose) {
                    Text("[-]", color = OV_DIM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))

            // tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OV_BG.copy(alpha = 0.6f))
                    .padding(3.dp)
            ) {
                TabBtn("JAWAB SOAL", tab == 0, Modifier.weight(1f)) { onTab(0) }
                TabBtn("CHAT AI", tab == 1, Modifier.weight(1f)) { onTab(1) }
            }
            Spacer(Modifier.height(10.dp))

            // body
            if (tab == 0) {
                Column {
                    NeonSmallBtn("AUTO JAWAB", OV_VIOLET, busy) { onAsk() }
                    Spacer(Modifier.height(8.dp))
                    if (soal.isNotBlank()) {
                        SectionLabel("SOAL TERBACA")
                        ScrollBox(90.dp, soal)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (jawaban.isNotBlank()) {
                        SectionLabel("JAWABAN")
                        ScrollBox(160.dp, jawaban)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row {
                        NeonSmallBtn("COPY", OV_VIOLET_DEEP, false, Modifier.weight(1f)) { onCopy() }
                        Spacer(Modifier.width(6.dp))
                        NeonSmallBtn("CLEAR", Color(0xFF2D1B4D), false, Modifier.weight(1f)) { onClear() }
                    }
                }
            } else {
                Column {
                    if (chatHistory.isEmpty()) {
                        Text(
                            "Tes koneksi AI. Ketik pesan.",
                            color = OV_DIM, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    } else {
                        ScrollBox(220.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chatHistory.forEach { (who, msg) ->
                                    ChatBubble(who == "you", msg)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OV_BG)
                                .border(1.dp, OV_VIOLET.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (chatInput.isEmpty()) {
                                Text("ketik pesan...", color = OV_DIM.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = chatInput,
                                onValueChange = onChatInput,
                                textStyle = TextStyle(color = OV_TEXT, fontSize = 12.sp),
                                cursorBrush = SolidColor(OV_NEON),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(OV_VIOLET_DEEP, OV_NEON)))
                                .pointerInput(Unit) { detectTapGestures { onSend() } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(">", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (statusGood) OV_OK else OV_BAD, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    status,
                    color = if (statusGood) OV_OK else OV_BAD,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    private fun TabBtn(text: String, active: Boolean, mod: Modifier, onClick: () -> Unit) {
        Box(
            modifier = mod
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) OV_VIOLET_DEEP.copy(alpha = 0.4f) else Color.Transparent)
                .pointerInput(Unit) { detectTapGestures { onClick() } }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (active) OV_TEXT else OV_DIM,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }

    @Composable
    private fun NeonSmallBtn(text: String, color: Color, busy: Boolean, mod: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
        Box(
            modifier = mod
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (busy) color.copy(alpha = 0.4f) else color)
                .pointerInput(Unit) {
                    detectTapGestures { if (!busy) onClick() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        }
    }

    @Composable
    private fun SectionLabel(t: String) {
        Text(t, color = OV_NEON, fontSize = 9.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 4.dp))
    }

    @Composable
    private fun ScrollBox(height: androidx.compose.ui.unit.Dp, content: String) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(10.dp))
                .background(OV_BG.copy(alpha = 0.7f))
                .border(1.dp, OV_VIOLET.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Text(content, color = OV_TEXT, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }

    @Composable
    private fun ScrollBox(height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(10.dp))
                .background(OV_BG.copy(alpha = 0.7f))
                .border(1.dp, OV_VIOLET.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
            ) {
                content()
            }
        }
    }

    @Composable
    private fun ChatBubble(mine: Boolean, msg: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (mine) OV_VIOLET_DEEP.copy(alpha = 0.6f) else OV_BG)
                    .border(
                        1.dp,
                        if (mine) OV_NEON.copy(alpha = 0.4f) else OV_VIOLET.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(msg, color = OV_TEXT, fontSize = 11.sp, lineHeight = 15.sp)
            }
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
