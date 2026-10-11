package com.xen.xenmind.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class XyrusClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    suspend fun ask(model: String, systemPrompt: String, userContent: String): String =
        withContext(Dispatchers.IO) {
            val body = ChatRequest(
                model = model,
                messages = listOf(Msg("system", systemPrompt), Msg("user", userContent)),
                stream = false,
                temperature = 0.3
            )
            val payload = json.encodeToString(ChatRequest.serializer(), body)
            val req = Request.Builder()
                .url("https://ai.xyrusrouter.my.id/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(media))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${raw.take(200)}")
                val parsed = json.decodeFromString(ChatResponse.serializer(), raw)
                parsed.choices.firstOrNull()?.message?.content
                    ?: throw RuntimeException("empty response")
            }
        }

    suspend fun askWithFallback(
        chain: List<String>,
        systemPrompt: String,
        userContent: String,
        retries: Int = 4,
        onAttempt: (Int, String) -> Unit = { _, _ -> }
    ): Pair<String, String> {
        var lastErr: Exception? = null
        val attempts = chain.take(retries.coerceAtMost(chain.size))
        attempts.forEachIndexed { idx, model ->
            onAttempt(idx + 1, model)
            try {
                return ask(model, systemPrompt, userContent) to model
            } catch (e: Exception) {
                lastErr = e
                if (e.message?.contains("401") == true) throw e
                delay((1000L * (1 shl idx)).coerceAtMost(8000L))
            }
        }
        throw lastErr ?: RuntimeException("no models available")
    }

    val systemUniversal: String = """
        Kamu asisten ujian. Jawab soal apapun yang ada di layar — pilihan ganda atau essay — dengan tepat dan lengkap.

        Aturan:
        1. Untuk SOAL PILIHAN GANDA, balas:
           [NO <nomor>] JAWABAN: <huruf>. <teks pilihan>
        2. Untuk SOAL ESSAY, balas:
           [NO <nomor>] JAWABAN: <jawaban lengkap, 1-3 paragraf sesuai bobot soal>
        3. Kalau ada beberapa soal dalam satu pesan, jawab SEMUA dengan format di atas, satu per baris.
        4. Kalau soal tidak ber-nomor, jawab secara berurutan sesuai urutan yang muncul.
        5. Bahasa Indonesia. Tanpa basa-basi. Langsung ke jawaban.
    """.trimIndent()

    val systemSingle: String = """
        Kamu asisten ujian. Jawab soal berikut dengan tepat dan lengkap.
        Format:
        JAWABAN: <jawaban>
    """.trimIndent()
}
