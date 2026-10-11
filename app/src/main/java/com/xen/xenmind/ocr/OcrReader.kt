package com.xen.xenmind.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val img = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(img)
            .addOnSuccessListener { txt ->
                val sb = StringBuilder()
                for (block in txt.textBlocks) for (line in block.lines) {
                    sb.append(line.text).append("\n")
                }
                cont.resume(sb.toString())
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
