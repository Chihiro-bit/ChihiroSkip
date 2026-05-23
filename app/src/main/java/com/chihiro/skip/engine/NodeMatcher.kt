package com.chihiro.skip.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.model.MatchCondition

class NodeMatcher {

    // 无歧义的跳过关键词（直接匹配即可点击）
    private val safeSkipKeywords = listOf(
        "跳过广告", "跳过 广告", "SkipAd", "Skip Ad", "SKIP AD",
        "跳过", "跳过 ", "Skip", "SKIP"
    )

    // 含计时的跳过关键词（如 "跳过 3"、"Skip 3s"）
    private val timedSkipPattern = Regex("""^(跳过|Skip)\s*\d+\s*(s|秒)?$""", RegexOption.IGNORE_CASE)

    // 需要位置验证才能点击的关键词
    private val contextualKeywords = listOf("关闭", "Close", "×", "✕", "✖", "X", "x")

    // 绝不匹配的危险关键词
    private val dangerKeywords = setOf(
        "下载", "安装", "打开", "购买", "订阅", "支付", "同意", "授权",
        "允许", "登录", "注册", "领取", "确认支付",
        "Continue", "Buy", "Subscribe", "Install", "Download",
        "Allow", "Login", "Register", "Confirm", "确认"
    )

    // ── 基础识别（不依赖规则文件）──────────────────────────
    fun findBasicSkipNode(
        rootNode: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): AccessibilityNodeInfo? {
        // 1. 无歧义跳过词
        for (kw in safeSkipKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(kw)
            val found = nodes.firstOrNull { n ->
                isNodeSafe(n) && (n.isClickable || hasClickableParent(n))
            }
            if (found != null) return found
        }

        // 2. 带计时的跳过词（正则）
        val allNodes = collectAllNodes(rootNode)
        for (node in allNodes) {
            val text = node.text?.toString() ?: continue
            if (timedSkipPattern.matches(text) && isNodeSafe(node)) return node
        }

        // 3. 关闭/× 等——仅位于角落时才匹配
        for (kw in contextualKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(kw)
            val found = nodes.firstOrNull { n ->
                isNodeSafe(n) && isInCornerArea(n, screenWidth, screenHeight)
            }
            if (found != null) return found
        }

        return null
    }

    // ── 精确规则匹配 ───────────────────────────────────────
    fun matchRule(
        rootNode: AccessibilityNodeInfo,
        rule: AdSkipRule
    ): AccessibilityNodeInfo? {
        val cond = rule.matchCondition

        // packageOnly: match by package alone, return root for coordinate actions
        if (cond.matchType == "packageOnly") return rootNode

        // viewId 最精准，优先匹配
        if (cond.viewId.isNotEmpty()) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(cond.viewId)
            nodes.firstOrNull { isNodeSafe(it) }?.let { return it }
        }

        // textEquals
        for (text in cond.textEquals) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            nodes.firstOrNull { isNodeSafe(it) && it.text?.toString() == text }?.let { return it }
        }

        // textContains
        for (text in cond.textContains) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            nodes.firstOrNull { isNodeSafe(it) }?.let { return it }
        }

        // contentDescription
        for (desc in cond.contentDescriptionContains) {
            findNodeByDesc(rootNode, desc)?.let { return it }
        }
        for (desc in cond.contentDescriptionEquals) {
            findNodeByDesc(rootNode, desc, exact = true)?.let { return it }
        }

        // className
        if (cond.className.isNotEmpty()) {
            collectAllNodes(rootNode).firstOrNull {
                it.className?.toString() == cond.className && isNodeSafe(it)
            }?.let { return it }
        }

        return null
    }

    // ── 工具方法 ───────────────────────────────────────────
    private fun isNodeSafe(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combined = text + desc
        return dangerKeywords.none { combined.contains(it, ignoreCase = true) }
    }

    private fun isInCornerArea(node: AccessibilityNodeInfo, sw: Int, sh: Int): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val edgeX = (sw * 0.20).toInt()
        val topEdge = (sh * 0.20).toInt()
        return cy < topEdge && (cx < edgeX || cx > sw - edgeX)
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var p = node.parent
        var depth = 0
        while (p != null && depth < 5) {
            if (p.isClickable) return true
            p = p.parent
            depth++
        }
        return false
    }

    private fun findNodeByDesc(
        root: AccessibilityNodeInfo,
        desc: String,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        val d = root.contentDescription?.toString() ?: ""
        val match = if (exact) d == desc else d.contains(desc, ignoreCase = true)
        if (match && isNodeSafe(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeByDesc(child, desc, exact)?.let { return it }
        }
        return null
    }

    private fun collectAllNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            node ?: return
            list.add(node)
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return list
    }
}
