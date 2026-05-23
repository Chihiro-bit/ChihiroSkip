package com.chihiro.skip.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chihiro.skip.model.CandidateNode

class CandidateNodeScanner {

    private val skipKeywords = setOf(
        "跳过", "skip", "关闭广告", "close ad", "跳过广告", "skip ad",
        "跳过片头", "片头跳过", "跳过开屏", "关闭", "×", "close"
    )

    private val timedPattern = Regex("""(跳过|skip)\s*\d+""", RegexOption.IGNORE_CASE)

    fun scan(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): List<CandidateNode> {
        val result = mutableListOf<CandidateNode>()
        collectNodes(root, result, screenWidth, screenHeight)
        return result.sortedByDescending { it.confidenceScore }
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        result: MutableList<CandidateNode>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        node ?: return
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
            val score = calculateScore(text, desc, viewId, rect, node, screenWidth, screenHeight)
            if (score >= 20) {
                val cx = rect.centerX()
                val cy = rect.centerY()
                result.add(
                    CandidateNode(
                        text = text,
                        contentDescription = desc,
                        viewId = viewId,
                        className = node.className?.toString() ?: "",
                        bounds = Rect(rect),
                        centerX = cx,
                        centerY = cy,
                        xRatio = if (screenWidth > 0) cx.toFloat() / screenWidth else 0f,
                        yRatio = if (screenHeight > 0) cy.toFloat() / screenHeight else 0f,
                        clickable = node.isClickable,
                        confidenceScore = score,
                        reason = buildReason(text, desc, viewId, node, score)
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), result, screenWidth, screenHeight)
        }
    }

    private fun calculateScore(
        text: String, desc: String, viewId: String,
        rect: Rect, node: AccessibilityNodeInfo,
        screenWidth: Int, screenHeight: Int
    ): Int {
        var score = 0
        val combined = "$text $desc".lowercase()

        if (timedPattern.containsMatchIn(combined)) score += 50
        skipKeywords.forEach { kw ->
            if (combined.contains(kw.lowercase())) score += 30
        }

        if (viewId.isNotEmpty()) {
            val idLower = viewId.lowercase()
            if (idLower.contains("skip") || idLower.contains("close") ||
                idLower.contains("ad") || idLower.contains("jump")
            ) score += 20
        }

        val isTopRight = rect.right > screenWidth * 0.6 && rect.top < screenHeight * 0.25
        val isTopLeft = rect.left < screenWidth * 0.4 && rect.top < screenHeight * 0.25
        if (isTopRight || isTopLeft) score += 15

        if (node.isClickable) score += 10

        val w = rect.width()
        val h = rect.height()
        if (w in 40..500 && h in 24..160) score += 5

        return score
    }

    private fun buildReason(
        text: String, desc: String, viewId: String,
        node: AccessibilityNodeInfo, score: Int
    ): String = buildString {
        if (text.isNotEmpty()) append("文字:\"$text\" ")
        if (desc.isNotEmpty()) append("描述:\"$desc\" ")
        if (viewId.isNotEmpty()) append("ID:${viewId.substringAfterLast('/')} ")
        if (node.isClickable) append("可点击 ")
        append("得分:$score")
    }
}
