package com.chihiro.skip.manager

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.chihiro.skip.repository.SettingsRepository

/**
 * 应用内语言切换工具：语言选择持久化在 SettingsRepository。
 * 各 Activity/Service 在 attachBaseContext 中调用 [wrap] 应用所选语言；
 * 切换语言时调用 [applyLanguage]（Activity 会自动 recreate 生效）。
 */
object LanguageHelper {

    /** 支持的语言标签，空串 = 跟随系统 */
    val SUPPORTED_TAGS = listOf("", "zh", "en", "ja", "ko", "fr")

    /** 返回应用了所选语言的上下文；跟随系统时原样返回 */
    fun wrap(base: Context): Context {
        val tag = SettingsRepository.getInstance(base).languageTag
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(config)
    }

    /** 保存语言选择；若在 Activity 中调用则立即重建生效 */
    fun applyLanguage(context: Context, tag: String) {
        SettingsRepository.getInstance(context).languageTag = tag
        (context as? Activity)?.recreate()
    }

    /** 当前语言标签（空串 = 跟随系统） */
    fun currentTag(context: Context): String =
        SettingsRepository.getInstance(context).languageTag
}
