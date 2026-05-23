package com.chihiro.skip.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.chihiro.skip.model.RuleAction

class ClickExecutor(private val service: AccessibilityService) {

    private val TAG = "ClickExecutor"

    fun clickNode(node: AccessibilityNodeInfo, testMode: Boolean = false): Boolean {
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (testMode) {
            Log.i(TAG, "[TEST] would click node: \"$text\"")
            return true
        }
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "Clicked node via ACTION_CLICK: \"$text\"")
            return true
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 6) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked parent node at depth $depth")
                return true
            }
            parent = parent.parent
            depth++
        }
        return clickNodeByGesture(node, testMode)
    }

    fun clickNodeByGesture(node: AccessibilityNodeInfo, testMode: Boolean = false): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        return clickCoordinate(rect.exactCenterX(), rect.exactCenterY(), testMode)
    }

    fun clickCoordinate(x: Float, y: Float, testMode: Boolean = false): Boolean {
        if (testMode) {
            Log.i(TAG, "[TEST] would click coordinate ($x, $y)")
            return true
        }
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) { Log.d(TAG, "Gesture done at ($x,$y)") }
            override fun onCancelled(g: GestureDescription) { Log.w(TAG, "Gesture cancelled at ($x,$y)") }
        }, null)
        return true
    }

    fun executeAction(
        action: RuleAction,
        rootNode: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        safeAreaTop: Int = 0,
        safeAreaBottom: Int = 0,
        orientation: Int = 0,
        testMode: Boolean = false
    ): Boolean {
        if (action.orientation.isNotEmpty()) {
            val isPortrait = screenHeight >= screenWidth
            val wantPortrait = action.orientation == "portrait"
            val wantLandscape = action.orientation == "landscape"
            if (wantPortrait && !isPortrait) return false
            if (wantLandscape && isPortrait) return false
        }

        return when (action.type) {
            "clickNode" -> {
                val node = when (action.clickBy) {
                    "viewId" -> rootNode.findAccessibilityNodeInfosByViewId(action.value).firstOrNull()
                    "text" -> rootNode.findAccessibilityNodeInfosByText(action.value).firstOrNull()
                    "contentDescription" -> findByDesc(rootNode, action.value)
                    else -> null
                }
                node?.let { clickNode(it, testMode) } ?: false
            }
            "clickCoordinate" -> {
                if (action.x > 0 && action.y > 0)
                    clickCoordinate(action.x.toFloat(), action.y.toFloat(), testMode)
                else false
            }
            "clickRelativeCoordinate" -> {
                if (action.xRatio > 0f && action.yRatio > 0f) {
                    clickCoordinate(screenWidth * action.xRatio, screenHeight * action.yRatio, testMode)
                } else false
            }
            "clickSafeAreaRelative" -> {
                if (action.xRatioInSafeArea > 0f && action.yRatioInSafeArea > 0f) {
                    val safeHeight = screenHeight - safeAreaTop - safeAreaBottom
                    val x = screenWidth * action.xRatioInSafeArea
                    val y = safeAreaTop + safeHeight * action.yRatioInSafeArea
                    clickCoordinate(x, y, testMode)
                } else false
            }
            "clickRegion" -> {
                val cx = screenWidth * (action.regionLeftRatio + action.regionRightRatio) / 2f
                val cy = screenHeight * (action.regionTopRatio + action.regionBottomRatio) / 2f
                if (cx > 0 && cy > 0) clickCoordinate(cx, cy, testMode) else false
            }
            else -> false
        }
    }

    private fun findByDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            findByDesc(root.getChild(i) ?: continue, desc)?.let { return it }
        }
        return null
    }
}
