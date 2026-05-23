package com.chihiro.skip.repository

import android.content.Context
import android.util.Log
import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.model.MatchCondition
import com.chihiro.skip.model.RuleAction
import com.chihiro.skip.model.ValidScreen
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class RuleRepository private constructor(private val context: Context) {

    private val rulesFile: File get() = File(context.filesDir, "rules.json")
    private val _rules = mutableListOf<AdSkipRule>()

    val rules: List<AdSkipRule> get() = _rules.toList()
    val ruleCount: Int get() = _rules.size

    companion object {
        private const val TAG = "RuleRepository"

        @Volatile
        private var INSTANCE: RuleRepository? = null

        fun getInstance(context: Context): RuleRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleRepository(context.applicationContext).also {
                    INSTANCE = it
                    it.loadRules()
                }
            }
    }

    fun loadRules() {
        try {
            if (!rulesFile.exists()) return
            val jsonObj = JSONObject(rulesFile.readText())
            val arr = jsonObj.optJSONArray("rules") ?: return
            _rules.clear()
            for (i in 0 until arr.length()) {
                parseRule(arr.getJSONObject(i))?.let { _rules.add(it) }
            }
            _rules.sortByDescending { it.priority }
        } catch (e: Exception) {
            Log.e(TAG, "loadRules failed", e)
        }
    }

    fun saveRules() {
        try {
            val arr = JSONArray()
            _rules.forEach { arr.put(ruleToJson(it)) }
            val root = JSONObject().apply {
                put("version", 2)
                put("rules", arr)
            }
            rulesFile.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "saveRules failed", e)
        }
    }

    fun addRule(rule: AdSkipRule) {
        _rules.removeAll { it.id == rule.id }
        _rules.add(rule)
        _rules.sortByDescending { it.priority }
        saveRules()
    }

    fun updateRule(rule: AdSkipRule) {
        val idx = _rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            _rules[idx] = rule
            _rules.sortByDescending { it.priority }
            saveRules()
        }
    }

    fun deleteRule(id: String) {
        if (_rules.removeAll { it.id == id }) saveRules()
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        val idx = _rules.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _rules[idx] = _rules[idx].copy(enabled = enabled)
            saveRules()
        }
    }

    fun getRulesForPackage(packageName: String): List<AdSkipRule> =
        _rules.filter {
            it.enabled && (it.packageName.isEmpty() || it.packageName == packageName)
        }.sortedByDescending { it.priority }

    fun importRules(jsonText: String): ImportResult {
        return try {
            val jsonObj = JSONObject(jsonText)
            val arr = jsonObj.optJSONArray("rules")
                ?: return ImportResult(0, 1, listOf("JSON 格式错误：缺少 rules 数组"))
            val success = mutableListOf<AdSkipRule>()
            val failed = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                try {
                    val rule = parseRule(arr.getJSONObject(i))
                    if (rule != null) success.add(rule)
                    else failed.add("第 ${i + 1} 条：解析失败")
                } catch (e: Exception) {
                    failed.add("第 ${i + 1} 条：${e.message}")
                }
            }
            success.forEach { addRule(it) }
            ImportResult(success.size, failed.size, failed)
        } catch (e: Exception) {
            ImportResult(0, 1, listOf("JSON 解析失败：${e.message}"))
        }
    }

    fun exportRules(): String {
        val arr = JSONArray()
        _rules.forEach { arr.put(ruleToJson(it)) }
        return JSONObject().apply {
            put("version", 2)
            put("rules", arr)
        }.toString(2)
    }

    // ── Parse ────────────────────────────────────────────────
    private fun parseRule(obj: JSONObject): AdSkipRule? = try {
        val candidateArr = obj.optJSONArray("candidateActions")
        val candidateList = if (candidateArr != null) {
            (0 until candidateArr.length()).map { parseAction(candidateArr.getJSONObject(it)) }
        } else emptyList()

        AdSkipRule(
            id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = obj.optString("name"),
            packageName = obj.optString("packageName"),
            enabled = obj.optBoolean("enabled", true),
            priority = obj.optInt("priority", 0),
            matchCondition = parseMatchCondition(obj.optJSONObject("match") ?: JSONObject()),
            action = parseAction(obj.optJSONObject("action") ?: JSONObject()),
            fallbackAction = obj.optJSONObject("fallbackAction")?.let { parseAction(it) },
            relativeAction = obj.optJSONObject("relativeAction")?.let { parseAction(it) },
            candidateActions = candidateList,
            portraitAction = obj.optJSONObject("portraitAction")?.let { parseAction(it) },
            landscapeAction = obj.optJSONObject("landscapeAction")?.let { parseAction(it) },
            delayMs = obj.optLong("delayMs", 300L),
            cooldownMs = obj.optLong("cooldownMs", 1500L),
            maxClicksPerMinute = obj.optInt("maxClicksPerMinute", 3),
            startupWindowMs = obj.optLong("startupWindowMs", 0L),
            allowCoordinateClick = obj.optBoolean("allowCoordinateClick", false),
            validScreen = obj.optJSONObject("validScreen")?.let { parseValidScreen(it) },
            recordedScreenWidth = obj.optInt("recordedScreenWidth", 0),
            recordedScreenHeight = obj.optInt("recordedScreenHeight", 0)
        )
    } catch (e: Exception) { null }

    private fun parseMatchCondition(obj: JSONObject) = MatchCondition(
        activityName = obj.optString("activityName"),
        textEquals = parseStrList(obj.optJSONArray("textEquals")),
        textContains = parseStrList(obj.optJSONArray("textContains")),
        contentDescriptionEquals = parseStrList(obj.optJSONArray("contentDescriptionEquals")),
        contentDescriptionContains = parseStrList(obj.optJSONArray("contentDescriptionContains")),
        viewId = obj.optString("viewId"),
        className = obj.optString("className"),
        requireClickable = obj.optBoolean("requireClickable", false),
        matchType = obj.optString("matchType")
    )

    private fun parseAction(obj: JSONObject) = RuleAction(
        type = obj.optString("type"),
        clickBy = obj.optString("clickBy"),
        value = obj.optString("value"),
        x = obj.optInt("x", 0),
        y = obj.optInt("y", 0),
        xRatio = obj.optDouble("xRatio", 0.0).toFloat(),
        yRatio = obj.optDouble("yRatio", 0.0).toFloat(),
        xRatioInSafeArea = obj.optDouble("xRatioInSafeArea", 0.0).toFloat(),
        yRatioInSafeArea = obj.optDouble("yRatioInSafeArea", 0.0).toFloat(),
        baseWidth = obj.optInt("baseWidth", 0),
        baseHeight = obj.optInt("baseHeight", 0),
        orientation = obj.optString("orientation"),
        regionLeftRatio = obj.optDouble("regionLeftRatio", 0.0).toFloat(),
        regionTopRatio = obj.optDouble("regionTopRatio", 0.0).toFloat(),
        regionRightRatio = obj.optDouble("regionRightRatio", 0.0).toFloat(),
        regionBottomRatio = obj.optDouble("regionBottomRatio", 0.0).toFloat()
    )

    private fun parseValidScreen(obj: JSONObject) = ValidScreen(
        minWidth = obj.optInt("minWidth", 0),
        maxWidth = obj.optInt("maxWidth", Int.MAX_VALUE),
        minHeight = obj.optInt("minHeight", 0),
        maxHeight = obj.optInt("maxHeight", Int.MAX_VALUE)
    )

    private fun parseStrList(arr: JSONArray?): List<String> =
        if (arr == null) emptyList()
        else (0 until arr.length()).map { arr.getString(it) }

    // ── Serialize ────────────────────────────────────────────
    private fun ruleToJson(rule: AdSkipRule): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("name", rule.name)
        put("packageName", rule.packageName)
        put("enabled", rule.enabled)
        put("priority", rule.priority)
        put("match", matchToJson(rule.matchCondition))
        put("action", actionToJson(rule.action))
        rule.fallbackAction?.let { put("fallbackAction", actionToJson(it)) }
        rule.relativeAction?.let { put("relativeAction", actionToJson(it)) }
        if (rule.candidateActions.isNotEmpty()) {
            put("candidateActions", JSONArray(rule.candidateActions.map { actionToJson(it) }))
        }
        rule.portraitAction?.let { put("portraitAction", actionToJson(it)) }
        rule.landscapeAction?.let { put("landscapeAction", actionToJson(it)) }
        put("delayMs", rule.delayMs)
        put("cooldownMs", rule.cooldownMs)
        put("maxClicksPerMinute", rule.maxClicksPerMinute)
        if (rule.startupWindowMs > 0) put("startupWindowMs", rule.startupWindowMs)
        if (rule.allowCoordinateClick) put("allowCoordinateClick", true)
        rule.validScreen?.let { put("validScreen", validScreenToJson(it)) }
        if (rule.recordedScreenWidth > 0) put("recordedScreenWidth", rule.recordedScreenWidth)
        if (rule.recordedScreenHeight > 0) put("recordedScreenHeight", rule.recordedScreenHeight)
    }

    private fun matchToJson(m: MatchCondition) = JSONObject().apply {
        if (m.activityName.isNotEmpty()) put("activityName", m.activityName)
        if (m.textEquals.isNotEmpty()) put("textEquals", JSONArray(m.textEquals))
        if (m.textContains.isNotEmpty()) put("textContains", JSONArray(m.textContains))
        if (m.contentDescriptionEquals.isNotEmpty())
            put("contentDescriptionEquals", JSONArray(m.contentDescriptionEquals))
        if (m.contentDescriptionContains.isNotEmpty())
            put("contentDescriptionContains", JSONArray(m.contentDescriptionContains))
        if (m.viewId.isNotEmpty()) put("viewId", m.viewId)
        if (m.className.isNotEmpty()) put("className", m.className)
        if (m.matchType.isNotEmpty()) put("matchType", m.matchType)
    }

    private fun actionToJson(a: RuleAction) = JSONObject().apply {
        put("type", a.type)
        if (a.clickBy.isNotEmpty()) put("clickBy", a.clickBy)
        if (a.value.isNotEmpty()) put("value", a.value)
        if (a.x != 0) put("x", a.x)
        if (a.y != 0) put("y", a.y)
        if (a.xRatio != 0f) put("xRatio", a.xRatio)
        if (a.yRatio != 0f) put("yRatio", a.yRatio)
        if (a.xRatioInSafeArea != 0f) put("xRatioInSafeArea", a.xRatioInSafeArea)
        if (a.yRatioInSafeArea != 0f) put("yRatioInSafeArea", a.yRatioInSafeArea)
        if (a.baseWidth != 0) put("baseWidth", a.baseWidth)
        if (a.baseHeight != 0) put("baseHeight", a.baseHeight)
        if (a.orientation.isNotEmpty()) put("orientation", a.orientation)
        if (a.regionLeftRatio != 0f) put("regionLeftRatio", a.regionLeftRatio)
        if (a.regionTopRatio != 0f) put("regionTopRatio", a.regionTopRatio)
        if (a.regionRightRatio != 0f) put("regionRightRatio", a.regionRightRatio)
        if (a.regionBottomRatio != 0f) put("regionBottomRatio", a.regionBottomRatio)
    }

    private fun validScreenToJson(vs: ValidScreen) = JSONObject().apply {
        if (vs.minWidth > 0) put("minWidth", vs.minWidth)
        if (vs.maxWidth < Int.MAX_VALUE) put("maxWidth", vs.maxWidth)
        if (vs.minHeight > 0) put("minHeight", vs.minHeight)
        if (vs.maxHeight < Int.MAX_VALUE) put("maxHeight", vs.maxHeight)
    }

    data class ImportResult(
        val successCount: Int,
        val failedCount: Int,
        val failedReasons: List<String>
    )
}
