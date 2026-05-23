package com.chihiro.skip.engine

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.model.SkipLog
import com.chihiro.skip.repository.RuleRepository
import com.chihiro.skip.repository.SettingsRepository
import com.chihiro.skip.repository.SkipLogRepository

class RuleEngine(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val ruleRepo: RuleRepository,
    private val logRepo: SkipLogRepository,
    private val safetyGuard: SafetyGuard,
    private val nodeMatcher: NodeMatcher,
    private val clickExecutor: ClickExecutor
) {
    private val TAG = "RuleEngine"
    private val lastClickTime = mutableMapOf<String, Long>()
    private val appForegroundTime = mutableMapOf<String, Long>()

    fun onAppForegrounded(packageName: String) {
        appForegroundTime[packageName] = System.currentTimeMillis()
    }

    fun process(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int,
        windows: List<AccessibilityWindowInfo>? = null
    ) {
        val guard = safetyGuard.check(packageName, windows = windows)
        if (guard is SafetyGuard.GuardResult.Blocked) {
            Log.v(TAG, "Blocked[$packageName]: ${(guard as SafetyGuard.GuardResult.Blocked).reason}")
            return
        }

        val now = System.currentTimeMillis()
        val lastClick = lastClickTime[packageName] ?: 0L
        if (now - lastClick < settingsRepo.cooldownMs) return

        if (safetyGuard.isRateLimited(packageName)) {
            Log.w(TAG, "Rate limited[$packageName]")
            return
        }

        val testMode = settingsRepo.testMode
        val mode = settingsRepo.mode
        val isPortrait = screenHeight >= screenWidth

        if (mode == "precise" || mode == "mixed") {
            val rules = ruleRepo.getRulesForPackage(packageName)
            for (rule in rules) {
                if (!checkValidScreen(rule, screenWidth, screenHeight)) continue
                if (!checkStartupWindow(rule, packageName, now)) continue

                val matchedNode = nodeMatcher.matchRule(rootNode, rule)
                if (matchedNode != null) {
                    Log.d(TAG, "Rule matched: \"${rule.name}\" for $packageName")
                    val delay = rule.delayMs.coerceIn(0L, 3000L)
                    if (delay > 0) Thread.sleep(delay)

                    val success = tryExecuteRule(rule, rootNode, screenWidth, screenHeight, isPortrait, testMode)
                    recordResult(packageName, rule.name, rule.action.type, success, now)
                    if (success) return
                }
            }
        }

        if ((mode == "basic" || mode == "mixed") && settingsRepo.enableBasicSkip) {
            val skipNode = nodeMatcher.findBasicSkipNode(rootNode, screenWidth, screenHeight)
            if (skipNode != null) {
                val text = skipNode.text?.toString()
                val desc = skipNode.contentDescription?.toString()
                if (!safetyGuard.isNodeTextSafe(text, desc)) return
                Log.d(TAG, "Basic match: \"$text\" for $packageName")
                Thread.sleep(300L)
                val success = clickExecutor.clickNode(skipNode, testMode)
                recordResult(packageName, "基础识别", "clickNode", success, now)
            }
        }
    }

    private fun tryExecuteRule(
        rule: AdSkipRule,
        rootNode: AccessibilityNodeInfo,
        screenWidth: Int, screenHeight: Int,
        isPortrait: Boolean,
        testMode: Boolean
    ): Boolean {
        // Orientation-specific overrides
        if (isPortrait && rule.portraitAction != null) {
            val ok = clickExecutor.executeAction(rule.portraitAction, rootNode, screenWidth, screenHeight, testMode = testMode)
            if (ok) return true
        }
        if (!isPortrait && rule.landscapeAction != null) {
            val ok = clickExecutor.executeAction(rule.landscapeAction, rootNode, screenWidth, screenHeight, testMode = testMode)
            if (ok) return true
        }

        // candidateActions list: try each in sequence
        if (rule.candidateActions.isNotEmpty()) {
            for (candidate in rule.candidateActions) {
                val ok = clickExecutor.executeAction(candidate, rootNode, screenWidth, screenHeight, testMode = testMode)
                if (ok) return true
            }
        }

        // Primary action
        var success = clickExecutor.executeAction(rule.action, rootNode, screenWidth, screenHeight, testMode = testMode)
        if (!success) {
            rule.fallbackAction?.let {
                success = clickExecutor.executeAction(it, rootNode, screenWidth, screenHeight, testMode = testMode)
            }
        }
        if (!success) {
            rule.relativeAction?.let {
                if (safetyGuard.isCoordinateClickAllowed(rule.packageName)) {
                    val x = screenWidth * it.xRatio
                    val y = screenHeight * it.yRatio
                    if (safetyGuard.isCoordinateSafe(x, y, screenWidth, screenHeight)) {
                        success = clickExecutor.executeAction(it, rootNode, screenWidth, screenHeight, testMode = testMode)
                    }
                }
            }
        }
        return success
    }

    private fun checkValidScreen(rule: AdSkipRule, screenWidth: Int, screenHeight: Int): Boolean {
        val vs = rule.validScreen ?: return true
        return vs.contains(screenWidth, screenHeight)
    }

    private fun checkStartupWindow(rule: AdSkipRule, packageName: String, now: Long): Boolean {
        if (rule.startupWindowMs <= 0L) return true
        val foregroundTime = appForegroundTime[packageName] ?: return true
        return now - foregroundTime <= rule.startupWindowMs
    }

    private fun recordResult(
        packageName: String, ruleName: String,
        actionType: String, success: Boolean, clickTime: Long
    ) {
        if (success) {
            lastClickTime[packageName] = clickTime
            safetyGuard.recordClick(packageName)
            settingsRepo.incrementTotalSkipCount()
            settingsRepo.incrementTodaySkipCount()
        }
        if (settingsRepo.logEnabled) {
            logRepo.addLog(
                SkipLog(
                    packageName = packageName,
                    ruleName = ruleName,
                    actionType = actionType,
                    success = success
                )
            )
        }
    }
}
