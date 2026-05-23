package com.chihiro.skip.repository

import android.content.Context
import android.content.SharedPreferences
import com.chihiro.skip.model.SkipLog
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SkipLogRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chihiro_logs", Context.MODE_PRIVATE)
    private val _logs = mutableListOf<SkipLog>()
    private val maxLogs = 500

    val logs: List<SkipLog> get() = _logs.toList()

    companion object {
        @Volatile
        private var INSTANCE: SkipLogRepository? = null

        fun getInstance(context: Context): SkipLogRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkipLogRepository(context.applicationContext).also {
                    INSTANCE = it
                    it.loadLogs()
                }
            }
    }

    fun addLog(log: SkipLog) {
        synchronized(_logs) {
            _logs.add(0, log)
            if (_logs.size > maxLogs) _logs.subList(maxLogs, _logs.size).clear()
        }
        saveLogs()
    }

    fun clearLogs() {
        synchronized(_logs) { _logs.clear() }
        prefs.edit().remove("logs_json").apply()
    }

    private fun loadLogs() {
        try {
            val json = prefs.getString("logs_json", null) ?: return
            val arr = JSONArray(json)
            _logs.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                _logs.add(
                    SkipLog(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        packageName = obj.optString("packageName"),
                        appName = obj.optString("appName"),
                        ruleName = obj.optString("ruleName"),
                        actionType = obj.optString("actionType"),
                        success = obj.optBoolean("success", false),
                        reason = obj.optString("reason"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveLogs() {
        try {
            val arr = JSONArray()
            synchronized(_logs) {
                _logs.take(200).forEach { log ->
                    arr.put(JSONObject().apply {
                        put("id", log.id)
                        put("packageName", log.packageName)
                        put("appName", log.appName)
                        put("ruleName", log.ruleName)
                        put("actionType", log.actionType)
                        put("success", log.success)
                        put("reason", log.reason)
                        put("timestamp", log.timestamp)
                    })
                }
            }
            prefs.edit().putString("logs_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
