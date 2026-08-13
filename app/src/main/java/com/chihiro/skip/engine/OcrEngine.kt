package com.chihiro.skip.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.chihiro.skip.R
import com.chihiro.skip.model.CandidateNode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executor

/**
 * 离线 OCR 引擎（ML Kit bundled 中英模型，无需网络/Play services）。
 * 进程级单例：TextRecognizer 永不 close，模型只在进程内加载一次。
 */
class OcrEngine private constructor() {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 异步识别。回调在传入的 [executor] 上执行，不阻塞调用线程。
     */
    fun recognize(
        bitmap: Bitmap,
        executor: Executor,
        onResult: (List<OcrHit>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener(executor) { result ->
                onResult(
                    result.textBlocks.flatMap { block -> block.lines }
                        .mapNotNull { line ->
                            line.boundingBox?.let { box ->
                                OcrHit(line.text, Rect(box), box.centerX(), box.centerY())
                            }
                        }
                )
            }
            .addOnFailureListener(executor) { onError(it) }
    }

    companion object {
        @Volatile
        private var INSTANCE: OcrEngine? = null

        fun getInstance(): OcrEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OcrEngine().also { INSTANCE = it }
            }
    }
}

/** OCR 识别到的一行文本及其屏幕位置（截图坐标空间） */
data class OcrHit(
    val text: String,
    val boundingBox: Rect,
    val centerX: Int,
    val centerY: Int
) {
    companion object {
        // 与 CandidateNodeScanner.skipKeywords 同语义，独立维护
        private val skipLikeKeywords = listOf(
            "跳过", "跳过广告", "跳过片头", "跳过开屏", "关闭广告", "关闭",
            "skip ad", "skip", "close", "×"
        )
        private val dangerKeywords = listOf(
            "下载", "安装", "打开", "购买", "订阅", "支付", "同意", "授权",
            "允许", "登录", "注册", "领取", "确认",
            "continue", "buy", "subscribe", "install", "download",
            "allow", "login", "register", "confirm"
        )

        fun isSkipLike(text: String): Boolean =
            text.isNotBlank() && skipLikeKeywords.any { text.contains(it, ignoreCase = true) }

        fun isDangerous(text: String): Boolean =
            dangerKeywords.any { text.contains(it, ignoreCase = true) }
    }
}

/**
 * 把截图坐标空间的 OCR 命中换算成屏幕坐标空间的候选节点。
 * OCR 节点不在无障碍树里，生成规则时必须走坐标动作（见 RuleGenerator.isFromOcr 分支）。
 */
fun OcrHit.toCandidateNode(
    screenWidth: Int,
    screenHeight: Int,
    shotWidth: Int,
    shotHeight: Int,
    context: Context
): CandidateNode {
    val sx = screenWidth.toFloat() / shotWidth
    val sy = screenHeight.toFloat() / shotHeight
    val cx = (centerX * sx).toInt()
    val cy = (centerY * sy).toInt()
    return CandidateNode(
        text = text,
        bounds = Rect(
            (boundingBox.left * sx).toInt(),
            (boundingBox.top * sy).toInt(),
            (boundingBox.right * sx).toInt(),
            (boundingBox.bottom * sy).toInt()
        ),
        centerX = cx,
        centerY = cy,
        xRatio = cx.toFloat() / screenWidth,
        yRatio = cy.toFloat() / screenHeight,
        clickable = false,
        confidenceScore = 80,
        reason = context.getString(R.string.scan_reason_ocr, text),
        isFromOcr = true
    )
}
