package com.chihiro.skip.utils

import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AccessibilityService.takeScreenshot 结果转 Bitmap 工具。
 * 仅 API 30+ 使用（takeScreenshot 本身是 API 30 才有的能力）。
 */
object ScreenshotUtil {

    /**
     * 从截图结果拷贝出软件位图，并释放 HardwareBuffer。
     * 无论成败都必须 close()，否则硬件缓冲区泄漏。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun copyFromScreenshot(result: ScreenshotResult): Bitmap? {
        val hb = result.hardwareBuffer
        return try {
            Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            hb.close()
        }
    }

    /** 宽度超过 [maxWidth] 时等比缩小（降低 OCR 输入分辨率，提速减内存） */
    fun downscale(bitmap: Bitmap, maxWidth: Int = 1080): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val h = bitmap.height * maxWidth / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxWidth, h, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }
}
