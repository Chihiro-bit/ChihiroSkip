package com.chihiro.skip.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.chihiro.skip.MainActivity
import com.chihiro.skip.MyApp
import com.chihiro.skip.R
import com.chihiro.skip.accessibility.AnalyzeSourceResult
import com.chihiro.skip.accessibility.EventWrapper
import com.chihiro.skip.accessibility.FastAccessibilityService
import com.chihiro.skip.engine.CandidateNodeScanner
import com.chihiro.skip.engine.ClickExecutor
import com.chihiro.skip.engine.NodeMatcher
import com.chihiro.skip.engine.OcrEngine
import com.chihiro.skip.engine.OcrHit
import com.chihiro.skip.engine.RuleEngine
import com.chihiro.skip.engine.RuleRecorderManager
import com.chihiro.skip.engine.SafetyGuard
import com.chihiro.skip.engine.toCandidateNode
import com.chihiro.skip.manager.LanguageHelper
import com.chihiro.skip.model.SkipLog
import com.chihiro.skip.repository.RuleRepository
import com.chihiro.skip.repository.SettingsRepository
import com.chihiro.skip.repository.SkipLogRepository
import com.chihiro.skip.skipInterface.ParameterCheckInterface
import com.chihiro.skip.utils.ScreenUtil
import com.chihiro.skip.utils.ScreenshotUtil

class MyAccessibilityService : FastAccessibilityService(), ParameterCheckInterface {

    companion object {
        private const val TAG = "ChihiroService"

        /** 系统约 2 张/10s，留 1s 余量 */
        private const val OCR_MIN_INTERVAL_MS = 6_000L

        /** 防御性强制复位（异常路径下 ocrInFlight 滞留兜底） */
        private const val OCR_RESET_MS = 30_000L

        @Volatile
        var liveInstance: MyAccessibilityService? = null
            private set
    }

    override val enableListenApp = true

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var ruleEngine: RuleEngine
    private lateinit var recorderManager: RuleRecorderManager
    private lateinit var candidateScanner: CandidateNodeScanner
    private lateinit var safetyGuard: SafetyGuard
    private lateinit var clickExecutor: ClickExecutor
    private lateinit var logRepo: SkipLogRepository

    // ── OCR 兜底（仅 API 30+）────────────────────────────────
    private var ocrEngine: OcrEngine? = null
    private val ocrGate = Any()
    private var ocrInFlight = false
    private var lastOcrAt = 0L

    private var lastPackage = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrap(newBase))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        liveInstance = this
        initEngine()
        if (settingsRepo.interceptEnabled) {
            showRunningNotification()
        }
        Log.i(TAG, "Service connected, interceptEnabled=${settingsRepo.interceptEnabled}")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRunningNotification()
        liveInstance = null
        Log.i(TAG, "Service destroyed")
    }

    private fun initEngine() {
        settingsRepo = SettingsRepository.getInstance(this)
        val ruleRepo = RuleRepository.getInstance(this)
        logRepo = SkipLogRepository.getInstance(this)
        safetyGuard = SafetyGuard(this, settingsRepo)
        val nodeMatcher = NodeMatcher()
        clickExecutor = ClickExecutor(this)
        ruleEngine = RuleEngine(
            context = this,
            settingsRepo = settingsRepo,
            ruleRepo = ruleRepo,
            logRepo = logRepo,
            safetyGuard = safetyGuard,
            nodeMatcher = nodeMatcher,
            clickExecutor = clickExecutor
        )
        recorderManager = RuleRecorderManager.getInstance(this)
        candidateScanner = CandidateNodeScanner(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ocrEngine = OcrEngine.getInstance()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::settingsRepo.isInitialized) return
        if (!settingsRepo.interceptEnabled && !recorderManager.isRecording) return

        val root = try { rootInActiveWindow } catch (_: Exception) { return } ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == "com.chihiro.skip") return

        if (pkg != lastPackage) {
            ruleEngine.onAppForegrounded(pkg)
            lastPackage = pkg
        }

        val sw = ScreenUtil.getScreenWidth(this)
        val sh = ScreenUtil.getScreenHeight(this)
        val windows = try { windows?.toList() } catch (_: Exception) { null }

        executor.execute {
            try {
                if (settingsRepo.interceptEnabled) {
                    val result = ruleEngine.process(root, pkg, sw, sh, windows)
                    if (result == RuleEngine.ProcessResult.NOT_HANDLED) {
                        tryOcrFallback(pkg, sw, sh)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in ruleEngine.process", e)
            }
        }
    }

    fun requestScan() {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return
        val pkg = root.packageName?.toString() ?: return
        val activity = try {
            windows?.firstOrNull { it.isActive }?.root?.packageName?.toString() ?: pkg
        } catch (_: Exception) { pkg }
        val sw = ScreenUtil.getScreenWidth(this)
        val sh = ScreenUtil.getScreenHeight(this)

        executor.execute {
            try {
                val tree = candidateScanner.scan(root, sw, sh)
                // ① 先建 session（修复 updateCandidates 在 session 为 null 时不落数据的顺序问题）
                FloatingRecorderService.liveInstance?.onCandidatesReady(pkg, activity)
                // ② 树候选立即可用
                recorderManager.updateCandidates(tree)
                // ③ OCR 增强（API 30+，与兜底共用截屏门闩；撞限频则静默跳过，树候选不受影响）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && claimOcrSlot()) {
                    takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val bmp = ScreenshotUtil.copyFromScreenshot(result)
                            if (bmp == null) { releaseOcrSlot(); return }
                            val scaled = ScreenshotUtil.downscale(bmp, 1080)
                            ocrEngine?.recognize(
                                scaled, executor,
                                onResult = { hits ->
                                    val ocrNodes = hits.asSequence()
                                        .filter {
                                            OcrHit.isSkipLike(it.text) &&
                                                !OcrHit.isDangerous(it.text) &&
                                                it.centerY < scaled.height / 2
                                        }
                                        .take(4)
                                        .map { it.toCandidateNode(sw, sh, scaled.width, scaled.height, this@MyAccessibilityService) }
                                        .toList()
                                    if (ocrNodes.isNotEmpty()) {
                                        recorderManager.updateCandidates(tree + ocrNodes)
                                    }
                                    releaseOcrSlot()
                                },
                                onError = { e ->
                                    Log.w(TAG, "Recorder OCR error", e)
                                    releaseOcrSlot()
                                }
                            )
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "Recorder takeScreenshot failed: $errorCode")
                            releaseOcrSlot()
                        }
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestScan error", e)
            }
        }
    }

    // ── OCR 兜底（拦截路径）────────────────────────────────────

    private fun tryOcrFallback(pkg: String, sw: Int, sh: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!settingsRepo.ocrEnabled || !settingsRepo.enableCoordinateClick) return
        if (recorderManager.isRecording) return
        if (safetyGuard.check(pkg) is SafetyGuard.GuardResult.Blocked) return
        if (safetyGuard.isRateLimited(pkg)) return
        if (!claimOcrSlot()) return
        takeScreenshotAndOcr(pkg, sw, sh)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotAndOcr(pkg: String, sw: Int, sh: Int) {
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val bmp = ScreenshotUtil.copyFromScreenshot(result)
                if (bmp == null) { releaseOcrSlot(); return }
                val scaled = ScreenshotUtil.downscale(bmp, 1080)
                ocrEngine?.recognize(
                    scaled, executor,
                    onResult = { hits ->
                        handleOcrHits(hits, pkg, sw, sh, scaled.width, scaled.height)
                        releaseOcrSlot()
                    },
                    onError = { e ->
                        Log.w(TAG, "Fallback OCR error", e)
                        releaseOcrSlot()
                    }
                )
            }

            override fun onFailure(errorCode: Int) {
                // 含系统限频码，静默降级
                Log.w(TAG, "Fallback takeScreenshot failed: $errorCode")
                releaseOcrSlot()
            }
        })
    }

    private fun handleOcrHits(
        hits: List<OcrHit>, pkg: String, sw: Int, sh: Int, shotW: Int, shotH: Int
    ) {
        for (hit in hits) {
            if (!OcrHit.isSkipLike(hit.text) || OcrHit.isDangerous(hit.text)) continue
            if (hit.centerY >= shotH / 2) continue
            // 截图坐标空间 → App 屏幕坐标空间（截图可能含系统栏高度差，全部走比例换算）
            val x = hit.centerX.toFloat() * sw / shotW
            val y = hit.centerY.toFloat() * sh / shotH
            if (!safetyGuard.isCoordinateClickAllowed(pkg)) return
            if (!safetyGuard.isCoordinateSafe(x, y, sw, sh)) continue
            Log.i(TAG, "OCR fallback hit: \"${hit.text}\" at ($x,$y) for $pkg")
            clickExecutor.clickCoordinate(x, y, settingsRepo.testMode)
            safetyGuard.recordClick(pkg)
            settingsRepo.incrementTotalSkipCount()
            settingsRepo.incrementTodaySkipCount()
            if (settingsRepo.logEnabled) {
                logRepo.addLog(
                    SkipLog(
                        packageName = pkg,
                        ruleName = getString(R.string.ocr_fallback_rule_name),
                        actionType = "clickCoordinate",
                        success = true
                    )
                )
            }
            return // 只点第一个命中
        }
    }

    /** 截屏门闩：同一时刻只允许一张在途截图（兜底与录制扫描共用），并限频 */
    private fun claimOcrSlot(): Boolean = synchronized(ocrGate) {
        val now = System.currentTimeMillis()
        if (ocrInFlight) {
            if (now - lastOcrAt > OCR_RESET_MS) ocrInFlight = false // 异常路径兜底复位
            else return false
        }
        if (now - lastOcrAt < OCR_MIN_INTERVAL_MS) return false
        ocrInFlight = true
        lastOcrAt = now
        true
    }

    private fun releaseOcrSlot() = synchronized(ocrGate) { ocrInFlight = false }

    override fun analyzeCallBack(wrapper: EventWrapper?, result: AnalyzeSourceResult) {}

    override fun handleRootNodeByPackageName(): MutableList<AccessibilityNodeInfo> = mutableListOf()

    override fun setExecuteHandleRootNode(value: Boolean) {
        if (::settingsRepo.isInitialized) {
            settingsRepo.interceptEnabled = value
            if (value) showRunningNotification() else cancelRunningNotification()
            Log.i(TAG, "interceptEnabled set to $value")
        }
    }

    fun getInterceptEnabled(): Boolean =
        if (::settingsRepo.isInitialized) settingsRepo.interceptEnabled else false

    // ── 通知 ──────────────────────────────────────────────────

    private fun showRunningNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MyApp.NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(MyApp.NOTIF_ID, notification)
    }

    private fun cancelRunningNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(MyApp.NOTIF_ID)
    }
}
