package com.chihiro.skip.model

import java.util.UUID

data class AdSkipRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val packageName: String = "",
    var enabled: Boolean = true,
    val priority: Int = 0,
    val matchCondition: MatchCondition = MatchCondition(),
    val action: RuleAction = RuleAction(),
    val fallbackAction: RuleAction? = null,
    val relativeAction: RuleAction? = null,
    /** 候选点列表：依次尝试，首个成功即止 */
    val candidateActions: List<RuleAction> = emptyList(),
    val portraitAction: RuleAction? = null,
    val landscapeAction: RuleAction? = null,
    val delayMs: Long = 300L,
    val cooldownMs: Long = 1500L,
    val maxClicksPerMinute: Int = 3,
    /** 0 = 不限制；> 0 = App 切到前台后的前 N ms 内才执行 */
    val startupWindowMs: Long = 0L,
    /** 是否允许坐标点击（需用户手动确认）*/
    val allowCoordinateClick: Boolean = false,
    /** 有效屏幕范围，超出范围不执行 */
    val validScreen: ValidScreen? = null,
    /** 录制时记录的屏幕宽度 */
    val recordedScreenWidth: Int = 0,
    /** 录制时记录的屏幕高度 */
    val recordedScreenHeight: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ValidScreen(
    val minWidth: Int = 0,
    val maxWidth: Int = Int.MAX_VALUE,
    val minHeight: Int = 0,
    val maxHeight: Int = Int.MAX_VALUE
) {
    fun contains(w: Int, h: Int) =
        w >= minWidth && w <= maxWidth && h >= minHeight && h <= maxHeight
}

data class MatchCondition(
    val activityName: String = "",
    val textEquals: List<String> = emptyList(),
    val textContains: List<String> = emptyList(),
    val contentDescriptionEquals: List<String> = emptyList(),
    val contentDescriptionContains: List<String> = emptyList(),
    val viewId: String = "",
    val className: String = "",
    val requireClickable: Boolean = false,
    /** "packageOnly" = 只匹配包名，不匹配 activity */
    val matchType: String = ""
)

/**
 * type:    clickNode / clickCoordinate / clickRelativeCoordinate / clickSafeAreaRelative
 * clickBy: text / contentDescription / viewId / coordinate / relativeCoordinate
 * orientation: "portrait" / "landscape" / "" (不限)
 */
data class RuleAction(
    val type: String = "",
    val clickBy: String = "",
    val value: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val xRatio: Float = 0f,
    val yRatio: Float = 0f,
    /** 安全区坐标（不含状态栏/导航栏）*/
    val xRatioInSafeArea: Float = 0f,
    val yRatioInSafeArea: Float = 0f,
    /** 录制时参考屏幕尺寸 */
    val baseWidth: Int = 0,
    val baseHeight: Int = 0,
    /** 仅在该方向下执行，空 = 任意方向 */
    val orientation: String = "",
    /** 区域中心点击 */
    val regionLeftRatio: Float = 0f,
    val regionTopRatio: Float = 0f,
    val regionRightRatio: Float = 0f,
    val regionBottomRatio: Float = 0f
)

// ── 以下已有，保持不变 ──

data class AppConfig(
    val packageName: String = "",
    val appName: String = "",
    var enabled: Boolean = true,
    var allowCoordinateClick: Boolean = false,
    var mode: String = "basic",
    var customDelayMs: Long = -1L,
    var maxClicksPerMinute: Int = -1,
    var isWhitelist: Boolean = false,
    var isBlacklist: Boolean = false
)

data class SkipLog(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String = "",
    val appName: String = "",
    val ruleName: String = "",
    val actionType: String = "",
    val success: Boolean = false,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
