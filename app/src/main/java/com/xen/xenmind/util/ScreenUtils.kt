package com.xen.xenmind.util

import android.graphics.Bitmap

object ScreenUtils {
    fun isMostlyBlack(bmp: Bitmap, threshold: Float = 0.95f): Boolean {
        val w = bmp.width; val h = bmp.height
        val step = 20
        var total = 0; var dark = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bmp.getPixel(x, y)
                val lum = ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
                total++
                if (lum < 12) dark++
                x += step
            }
            y += step
        }
        return total > 0 && (dark.toFloat() / total) > threshold
    }
}
