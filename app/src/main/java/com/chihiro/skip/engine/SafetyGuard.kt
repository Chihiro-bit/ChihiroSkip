package com.chihiro.skip.engine

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.chihiro.skip.repository.SettingsRepository

class SafetyGuard(private val context: Context, private val settingsRepo: SettingsRepository) {

    private val TAG = "SafetyGuard"

    // 系统/安装器/支付类 App 黑名单，永远不点击
    private val systemPackages = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.huawei.systemmanager",
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.oppo.market",
        "com.xiaomi.market",
        "com.huawei.appmarket",
        "com.tencent.mm",      // 微信
        "com.eg.android.AlipayGphone",  // 支付宝
        "com.unionpay",        // 银联
        "com.icbc",            // 工商银行
        "com.ccb.loansystem"   // 建设银行
    )

    // 危险关键词：命中则不点击
    private val dangerKeywords = setOf(
        "下载", "安装", "打开", "购买", "订阅", "支付", "同意", "授权",
        "允许", "登录", "注册", "领取", "确认支付", "立即支付",
        "Continue", "Buy", "Subscribe", "Install", "Download",
        "Allow", "Login", "Register", "Confirm", "确认"
    )

    // 每包名的点击时间戳列表（用于限频）
    private val clickTimestamps = mutableMapOf<String, MutableList<Long>>()

    // ── 主检查入口 ─────────────────────────────────────────
    fun check(
        packageName: String,
        nodeText: String? = null,
        windows: List<AccessibilityWindowInfo>? = null
    ): GuardResult {
        if (!settingsRepo.interceptEnabled) {
            return GuardResult.Blocked("总开关已关闭")
        }
        if (packageName in systemPackages) {
            return GuardResult.Blocked("系统/敏感 App：$packageName")
        }
        if (isScreenLocked()) {
            return GuardResult.Blocked("屏幕已锁定")
        }
        if (isImeVisible(windows)) {
            return GuardResult.Blocked("输入法已弹出")
        }
        nodeText?.let { text ->
            val hit = dangerKeywords.firstOrNull { text.contains(it, ignoreCase = true) }
            if (hit != null) return GuardResult.Blocked("命中危险关键词：$hit")
        }
        return GuardResult.Allow
    }

    // ── 频率检查 ──────────────────────────────────────────
    fun isRateLimited(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val times = clickTimestamps.getOrPut(packageName) { mutableListOf() }
        times.removeAll { now - it > 60_000L }
        return times.size >= settingsRepo.maxClicksPerMinute
    }

    fun recordClick(packageName: String) {
        val now = System.currentTimeMillis()
        val times = clickTimestamps.getOrPut(packageName) { mutableListOf() }
        times.removeAll { now - it > 60_000L }
        times.add(now)
        Log.d(TAG, "Click recorded for $packageName, total this minute: ${times.size}")
    }

    // ── 坐标安全检查 ──────────────────────────────────────
    fun isCoordinateSafe(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Boolean {
        if (x <= 0 || y <= 0) return false
        if (x > screenWidth || y > screenHeight) return false
        return true
    }

    // ── 节点文本安全检查 ──────────────────────────────────
    fun isNodeTextSafe(text: String?, desc: String?): Boolean {
        val combined = "${text ?: ""}${desc ?: ""}"
        return dangerKeywords.none { combined.contains(it, ignoreCase = true) }
    }

    // ── 是否允许坐标点击 ──────────────────────────────────
    fun isCoordinateClickAllowed(packageName: String): Boolean {
        if (!settingsRepo.enableCoordinateClick) return false
        if (packageName in systemPackages) return false
        return true
    }

    // ── 私有工具 ──────────────────────────────────────────
    private fun isScreenLocked(): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private fun isImeVisible(windows: List<AccessibilityWindowInfo>?): Boolean {
        if (windows == null) return false
        return windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }

    sealed class GuardResult {
        object Allow : GuardResult()
        data class Blocked(val reason: String) : GuardResult()
    }
}
