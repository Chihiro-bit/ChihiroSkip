package com.chihiro.skip.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "chihiro_settings"
        private const val KEY_INTERCEPT_ENABLED = "intercept_enabled"
        private const val KEY_TEST_MODE = "test_mode"
        private const val KEY_MODE = "mode"
        private const val KEY_LOG_ENABLED = "log_enabled"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        private const val KEY_MAX_CLICKS_PER_MIN = "max_clicks_per_minute"
        private const val KEY_ALLOW_RESTORE = "allow_restore_last_state"
        private const val KEY_ENABLE_BASIC = "enable_basic_skip"
        private const val KEY_ENABLE_PRECISE = "enable_precise_rule"
        private const val KEY_ENABLE_COORDINATE = "enable_coordinate_click"
        private const val KEY_TOTAL_SKIP_COUNT = "total_skip_count"
        private const val KEY_TODAY_SKIP_COUNT = "today_skip_count"
        private const val KEY_TODAY_DATE = "today_date"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── 主开关 ──────────────────────────────────────────────
    var interceptEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTERCEPT_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_INTERCEPT_ENABLED, v).apply()

    // ── 调试 ────────────────────────────────────────────────
    var testMode: Boolean
        get() = prefs.getBoolean(KEY_TEST_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_TEST_MODE, v).apply()

    // ── 识别模式: basic / precise / mixed ───────────────────
    var mode: String
        get() = prefs.getString(KEY_MODE, "mixed") ?: "mixed"
        set(v) = prefs.edit().putString(KEY_MODE, v).apply()

    var logEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOG_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_LOG_ENABLED, v).apply()

    // ── 安全参数 ─────────────────────────────────────────────
    var cooldownMs: Long
        get() = prefs.getLong(KEY_COOLDOWN_MS, 1500L)
        set(v) = prefs.edit().putLong(KEY_COOLDOWN_MS, v).apply()

    var maxClicksPerMinute: Int
        get() = prefs.getInt(KEY_MAX_CLICKS_PER_MIN, 10)
        set(v) = prefs.edit().putInt(KEY_MAX_CLICKS_PER_MIN, v).apply()

    var allowRestoreLastState: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_RESTORE, false)
        set(v) = prefs.edit().putBoolean(KEY_ALLOW_RESTORE, v).apply()

    // ── 功能开关 ─────────────────────────────────────────────
    var enableBasicSkip: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_BASIC, true)
        set(v) = prefs.edit().putBoolean(KEY_ENABLE_BASIC, v).apply()

    var enablePreciseRule: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_PRECISE, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLE_PRECISE, v).apply()

    var enableCoordinateClick: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_COORDINATE, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLE_COORDINATE, v).apply()

    // ── 统计 ─────────────────────────────────────────────────
    var totalSkipCount: Int
        get() = prefs.getInt(KEY_TOTAL_SKIP_COUNT, 0)
        set(v) = prefs.edit().putInt(KEY_TOTAL_SKIP_COUNT, v).apply()

    fun incrementTotalSkipCount() { totalSkipCount += 1 }

    fun getTodaySkipCount(): Int {
        ensureTodayKey()
        return prefs.getInt(KEY_TODAY_SKIP_COUNT, 0)
    }

    fun incrementTodaySkipCount() {
        ensureTodayKey()
        prefs.edit().putInt(KEY_TODAY_SKIP_COUNT, getTodaySkipCount() + 1).apply()
    }

    private fun ensureTodayKey() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val saved = prefs.getString(KEY_TODAY_DATE, "")
        if (saved != today) {
            prefs.edit()
                .putString(KEY_TODAY_DATE, today)
                .putInt(KEY_TODAY_SKIP_COUNT, 0)
                .apply()
        }
    }
}
