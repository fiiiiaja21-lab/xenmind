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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
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
import com.xen.xenmind.data.Prefs
import com.xen.xenmind.data.XyrusClient
import com.xen.xenmind.ocr.OcrReader
import com.xen.xenmind.util.ScreenUtils
import kotlinx.coroutines.*

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
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 400
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
        var status by remember { mutableStateOf("siap") }
        var soal by remember { mutableStateOf("") }
        var jawaban by remember { mutableStateOf("") }

        val ctx = this@OverlayService

        fun doAsk() {
            val bmp = ScreenCaptureService.Shared.last
            if (bmp == null) { status = "capture belum aktif"; return }
            if (ScreenUtils.isMostlyBlack(bmp)) { status = "layar FLAG_SECURE"; return }
            status = "baca soal..."
            scope.launch {
                try {
                    val text = OcrReader.read(bmp)
                    soal = text
                    if (text.isBlank()) { status = "tidak ada teks"; return@launch }
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
                } catch (e: Exception) {
                    status = "gagal: ${e.message?.take(80)}"
                }
            }
        }

        fun doCopy() {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("xenmind", jawaban))
            status = "tersalin"
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
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .shadow(12.dp, CircleShape)
                        .background(Color(0xFFA855F7), CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures { expanded = true }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("X", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .background(Color(0xFF0F0819), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("XenMind", color = Color(0xFFD946EF),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Text("[-]", color = Color(0xFFA08CC8),
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures { expanded = false }
                            })
                    }
                    Spacer(Modifier.height(8.dp))
                    Btn("Auto Jawab", Color(0xFFA855F7)) { doAsk() }
                    Spacer(Modifier.height(6.dp))
                    Btn("Copy jawaban", Color(0xFF7C3AED)) { doCopy() }
                    Spacer(Modifier.height(6.dp))
                    Btn("Clear", Color(0xFF3F2A5A)) {
                        soal = ""; jawaban = ""; status = "siap"
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("status: $status", color = Color(0xFF50FFA0), fontSize = 11.sp)
                    if (soal.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("soal:", color = Color(0xFFA08CC8), fontSize = 11.sp)
                        Text(soal.take(400), color = Color(0xFFF0E6FF), fontSize = 11.sp)
                    }
                    if (jawaban.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("jawaban:", color = Color(0xFFA08CC8), fontSize = 11.sp)
                        Text(jawaban, color = Color(0xFFF0E6FF), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun Btn(label: String, color: Color, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
    }

    override fun onDestroy() {
        try { wm.removeView(composeView) } catch (_: Throwable) {}
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object { const val NOTIF_ID = 4243 }
}
