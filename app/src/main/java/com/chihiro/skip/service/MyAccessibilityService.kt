package com.chihiro.skip.service

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chihiro.skip.accessibility.AnalyzeSourceResult
import com.chihiro.skip.accessibility.EventWrapper
import com.chihiro.skip.accessibility.FastAccessibilityService
import com.chihiro.skip.engine.CandidateNodeScanner
import com.chihiro.skip.engine.ClickExecutor
import com.chihiro.skip.engine.NodeMatcher
import com.chihiro.skip.engine.RuleEngine
import com.chihiro.skip.engine.RuleRecorderManager
import com.chihiro.skip.engine.SafetyGuard
import com.chihiro.skip.repository.RuleRepository
import com.chihiro.skip.repository.SettingsRepository
import com.chihiro.skip.repository.SkipLogRepository
import com.chihiro.skip.skipInterface.ParameterCheckInterface
import com.chihiro.skip.utils.ScreenUtil

class MyAccessibilityService : FastAccessibilityService(), ParameterCheckInterface {

    companion object {
        private const val TAG = "ChihiroService"

        @Volatile
        var liveInstance: MyAccessibilityService? = null
            private set
    }

    override val enableListenApp = true

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var ruleEngine: RuleEngine
    private lateinit var recorderManager: RuleRecorderManager
    private lateinit var candidateScanner: CandidateNodeScanner

    private var lastPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        liveInstance = this
        initEngine()
        Log.i(TAG, "Service connected, interceptEnabled=${settingsRepo.interceptEnabled}")
    }

    override fun onDestroy() {
        super.onDestroy()
        liveInstance = null
        Log.i(TAG, "Service destroyed")
    }

    private fun initEngine() {
        settingsRepo = SettingsRepository.getInstance(this)
        val ruleRepo = RuleRepository.getInstance(this)
        val logRepo = SkipLogRepository.getInstance(this)
        val safetyGuard = SafetyGuard(this, settingsRepo)
        val nodeMatcher = NodeMatcher()
        val clickExecutor = ClickExecutor(this)
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
        candidateScanner = CandidateNodeScanner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::settingsRepo.isInitialized) return
        if (!settingsRepo.interceptEnabled && !recorderManager.isRecording) return

        val root = try { rootInActiveWindow } catch (_: Exception) { return } ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == "com.chihiro.skip") return

        // Track foreground changes for startupWindowMs
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
                    ruleEngine.process(root, pkg, sw, sh, windows)
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
                val candidates = candidateScanner.scan(root, sw, sh)
                recorderManager.updateCandidates(candidates)
                FloatingRecorderService.liveInstance?.onCandidatesReady(pkg, activity)
            } catch (e: Exception) {
                Log.e(TAG, "requestScan error", e)
            }
        }
    }

    override fun analyzeCallBack(wrapper: EventWrapper?, result: AnalyzeSourceResult) {}

    override fun handleRootNodeByPackageName(): MutableList<AccessibilityNodeInfo> = mutableListOf()

    override fun setExecuteHandleRootNode(value: Boolean) {
        if (::settingsRepo.isInitialized) {
            settingsRepo.interceptEnabled = value
            Log.i(TAG, "interceptEnabled set to $value")
        }
    }

    fun getInterceptEnabled(): Boolean =
        if (::settingsRepo.isInitialized) settingsRepo.interceptEnabled else false
}
