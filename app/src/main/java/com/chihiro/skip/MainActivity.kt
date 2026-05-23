package com.chihiro.skip

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.chihiro.scrolllayout.ScrollLayout
import com.chihiro.scrolllayout.content.ContentScrollView
import com.chihiro.skip.accessibility.isAccessibilityEnable
import com.chihiro.skip.accessibility.requireAccessibility
import com.chihiro.skip.repository.RuleRepository
import com.chihiro.skip.repository.SettingsRepository
import com.chihiro.skip.repository.SkipLogRepository
import com.chihiro.skip.service.FloatingRecorderService
import com.chihiro.skip.service.MyAccessibilityService
import com.chihiro.skip.ui.RuleListActivity
import com.chihiro.skip.ui.SkipLogActivity
import com.chihiro.skip.utils.ImportExportManager
import com.chihiro.skip.utils.ScreenUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    // ── 视图引用 ──────────────────────────────────────────
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var startButton: ImageButton
    private lateinit var constraintLayoutResult: ConstraintLayout
    private lateinit var relativeLayout: RelativeLayout
    private lateinit var mScrollLayout: ScrollLayout
    private lateinit var settingsScrollView: ContentScrollView
    private lateinit var textInfo: TextView

    // ── 设置面板视图 ──────────────────────────────────────
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvInterceptStatus: TextView
    private lateinit var rgMode: RadioGroup
    private lateinit var rbModeBasic: RadioButton
    private lateinit var rbModePrecise: RadioButton
    private lateinit var rbModeMixed: RadioButton
    private lateinit var swEnablePrecise: SwitchMaterial
    private lateinit var tvRuleCount: TextView
    private lateinit var swTestMode: SwitchMaterial
    private lateinit var tvCurrentPackage: TextView
    private lateinit var tvTodayCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var swLogEnabled: SwitchMaterial
    private lateinit var etCooldownMs: EditText
    private lateinit var etMaxClicks: EditText
    private lateinit var swCoordinateClick: SwitchMaterial
    private lateinit var swRestoreState: SwitchMaterial

    // ── 仓库 ──────────────────────────────────────────────
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var ruleRepo: RuleRepository
    private lateinit var logRepo: SkipLogRepository

    // ── 状态 ──────────────────────────────────────────────
    private var isAnimating = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    // ── 文件选择器（使用 ActivityResult API，无需 READ_EXTERNAL_STORAGE）──
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        bgExecutor.execute {
            val result = ImportExportManager.importFromUri(this, uri)
            uiHandler.post {
                showImportResult(result)
                updateRuleCount()
            }
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        bgExecutor.execute {
            val ok = ImportExportManager.exportToUri(this, uri)
            uiHandler.post {
                Toast.makeText(
                    this,
                    if (ok) getString(R.string.export_success) else getString(R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── ScrollLayout 滚动监听 ─────────────────────────────
    private val mOnScrollChangedListener = object : ScrollLayout.OnScrollChangedListener {
        override fun onScrollProgressChanged(currentProgress: Float) {
            if (currentProgress >= 0) {
                var percent = (255 * currentProgress).toInt().coerceIn(0, 255)
                mScrollLayout.background.alpha = 255 - percent
            }
        }

        override fun onScrollFinished(currentStatus: ScrollLayout.Status) {}
        override fun onChildScroll(top: Int) {}
    }

    // ════════════════════════════════════════════════════════
    // 生命周期
    // ════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsRepo = SettingsRepository.getInstance(this)
        ruleRepo = RuleRepository.getInstance(this)
        logRepo = SkipLogRepository.getInstance(this)

        initView()
        initSettingsPanel()

        // 点击背景关闭面板
        relativeLayout.setOnClickListener { mScrollLayout.scrollToExit() }

        // 开始/停止按钮
        startButton.setOnClickListener { handleStartButton() }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateInterceptStatus()
        updateSettingsPanel()
    }

    // ════════════════════════════════════════════════════════
    // 视图初始化
    // ════════════════════════════════════════════════════════

    private fun initView() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.statusBarColor = Color.TRANSPARENT

        relativeLayout = findViewById(R.id.root)
        constraintLayout = findViewById(R.id.constraintLayout)
        startButton = findViewById(R.id.start_button)
        constraintLayoutResult = findViewById(R.id.result_view)
        textInfo = findViewById(R.id.textView2)

        mScrollLayout = findViewById(R.id.scroll_down_layout)
        settingsScrollView = findViewById(R.id.settings_scroll_view)

        // 设置 ContentScrollView 监听器（避免库内部 NPE）
        settingsScrollView.setOnScrollChangeListener { _, _, _, _ -> }

        mScrollLayout.setMinOffset(350)
        mScrollLayout.setMaxOffset((ScreenUtil.getScreenHeight(this) * 0.5).toInt())
        mScrollLayout.setExitOffset(ScreenUtil.dip2px(this, 150F))
        mScrollLayout.setIsSupportExit(true)
        mScrollLayout.isAllowHorizontalScroll = true
        mScrollLayout.setOnScrollChangedListener(mOnScrollChangedListener)
        mScrollLayout.setToExit()
        mScrollLayout.background.alpha = 0
    }

    private fun initSettingsPanel() {
        // 服务状态区域
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvInterceptStatus = findViewById(R.id.tv_intercept_status)

        findViewById<View>(R.id.btn_open_accessibility).setOnClickListener {
            requireAccessibility()
        }

        // 拦截模式
        rgMode = findViewById(R.id.rg_mode)
        rbModeBasic = findViewById(R.id.rb_mode_basic)
        rbModePrecise = findViewById(R.id.rb_mode_precise)
        rbModeMixed = findViewById(R.id.rb_mode_mixed)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_mode_basic -> "basic"
                R.id.rb_mode_precise -> "precise"
                else -> "mixed"
            }
            settingsRepo.mode = mode
        }

        // 精确规则
        swEnablePrecise = findViewById(R.id.sw_enable_precise)
        tvRuleCount = findViewById(R.id.tv_rule_count)

        swEnablePrecise.setOnCheckedChangeListener { _, checked ->
            settingsRepo.enablePreciseRule = checked
        }

        findViewById<View>(R.id.btn_import_rules).setOnClickListener {
            importLauncher.launch("*/*")
        }

        findViewById<View>(R.id.btn_export_rules).setOnClickListener {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("chihiro_rules_$ts.json")
        }

        findViewById<View>(R.id.btn_view_rules).setOnClickListener {
            startActivity(Intent(this, RuleListActivity::class.java))
        }

        // 调试
        swTestMode = findViewById(R.id.sw_test_mode)
        tvCurrentPackage = findViewById(R.id.tv_current_package)

        swTestMode.setOnCheckedChangeListener { _, checked ->
            settingsRepo.testMode = checked
            if (checked) Toast.makeText(this, "测试模式：只识别，不点击", Toast.LENGTH_SHORT).show()
        }

        // 日志统计
        tvTodayCount = findViewById(R.id.tv_today_count)
        tvTotalCount = findViewById(R.id.tv_total_count)
        swLogEnabled = findViewById(R.id.sw_log_enabled)

        swLogEnabled.setOnCheckedChangeListener { _, checked ->
            settingsRepo.logEnabled = checked
        }

        findViewById<View>(R.id.btn_view_logs).setOnClickListener {
            startActivity(Intent(this, SkipLogActivity::class.java))
        }

        findViewById<View>(R.id.btn_clear_logs).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_clear_logs)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    logRepo.clearLogs()
                    updateLogStats()
                    Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // 录制助手
        findViewById<View>(R.id.btn_start_recorder).setOnClickListener {
            startRecorderAssistant()
        }

        // 安全设置
        etCooldownMs = findViewById(R.id.et_cooldown_ms)
        etMaxClicks = findViewById(R.id.et_max_clicks)
        swCoordinateClick = findViewById(R.id.sw_coordinate_click)
        swRestoreState = findViewById(R.id.sw_restore_state)

        etCooldownMs.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toLongOrNull()?.let { settingsRepo.cooldownMs = it.coerceIn(100L, 10000L) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etMaxClicks.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { settingsRepo.maxClicksPerMinute = it.coerceIn(1, 60) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        swCoordinateClick.setOnCheckedChangeListener { _, checked ->
            settingsRepo.enableCoordinateClick = checked
        }

        swRestoreState.setOnCheckedChangeListener { _, checked ->
            settingsRepo.allowRestoreLastState = checked
        }
    }

    // ════════════════════════════════════════════════════════
    // 录制助手
    // ════════════════════════════════════════════════════════

    private fun startRecorderAssistant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recorder_permission_title)
                .setMessage(R.string.recorder_permission_msg)
                .setPositiveButton(R.string.recorder_permission_grant) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        startService(Intent(this, FloatingRecorderService::class.java))
        Toast.makeText(this, "录制助手已启动，请切换到目标应用", Toast.LENGTH_SHORT).show()
    }

    // ════════════════════════════════════════════════════════
    // 按钮逻辑（严格区分 accessibilityEnabled vs interceptEnabled）
    // ════════════════════════════════════════════════════════

    private fun handleStartButton() {
        if (!isAccessibilityEnable) {
            // 无障碍权限未开启 → 跳转系统设置
            requireAccessibility()
            return
        }
        // 无障碍已开启，切换拦截状态
        val newState = !settingsRepo.interceptEnabled
        settingsRepo.interceptEnabled = newState

        // 通知真实服务实例（如果服务已运行）
        MyAccessibilityService.liveInstance?.setExecuteHandleRootNode(newState)

        if (newState) {
            startAnimation()
        } else {
            reverseAnimation()
        }
        updateInterceptStatus()
    }

    // ════════════════════════════════════════════════════════
    // UI 状态更新
    // ════════════════════════════════════════════════════════

    private fun updateAccessibilityStatus() {
        if (isAccessibilityEnable) {
            tvAccessibilityStatus.text = getString(R.string.accessibility_on)
            tvAccessibilityStatus.setTextColor(getColor(R.color.status_on))
            textInfo.text = getString(R.string.service_status_enable)
        } else {
            tvAccessibilityStatus.text = getString(R.string.accessibility_off)
            tvAccessibilityStatus.setTextColor(getColor(R.color.status_off))
            textInfo.text = getString(R.string.service_status_disable)
        }
    }

    private fun updateInterceptStatus() {
        val running = settingsRepo.interceptEnabled
        tvInterceptStatus.text = getString(
            if (running) R.string.intercept_running else R.string.intercept_stopped
        )
        tvInterceptStatus.setTextColor(
            getColor(if (running) R.color.status_on else R.color.status_off)
        )
    }

    private fun updateSettingsPanel() {
        // 模式
        when (settingsRepo.mode) {
            "basic" -> rbModeBasic.isChecked = true
            "precise" -> rbModePrecise.isChecked = true
            else -> rbModeMixed.isChecked = true
        }
        // 开关
        swEnablePrecise.isChecked = settingsRepo.enablePreciseRule
        swTestMode.isChecked = settingsRepo.testMode
        swLogEnabled.isChecked = settingsRepo.logEnabled
        swCoordinateClick.isChecked = settingsRepo.enableCoordinateClick
        swRestoreState.isChecked = settingsRepo.allowRestoreLastState

        // 数字
        etCooldownMs.setText(settingsRepo.cooldownMs.toString())
        etMaxClicks.setText(settingsRepo.maxClicksPerMinute.toString())

        updateRuleCount()
        updateLogStats()
        updateAccessibilityStatus()
        updateInterceptStatus()
    }

    private fun updateRuleCount() {
        tvRuleCount.text = getString(R.string.rule_count, ruleRepo.ruleCount)
    }

    private fun updateLogStats() {
        tvTodayCount.text = getString(R.string.today_skip_count, settingsRepo.getTodaySkipCount())
        tvTotalCount.text = getString(R.string.total_skip_count, settingsRepo.totalSkipCount)
    }

    // ════════════════════════════════════════════════════════
    // 导入结果弹窗
    // ════════════════════════════════════════════════════════

    private fun showImportResult(result: RuleRepository.ImportResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_result, null)
        dialogView.findViewById<TextView>(R.id.tv_import_success_count).text =
            "成功：${result.successCount} 条"
        dialogView.findViewById<TextView>(R.id.tv_import_failed_count).text =
            "失败：${result.failedCount} 条"
        if (result.failedReasons.isNotEmpty()) {
            dialogView.findViewById<TextView>(R.id.tv_import_failed_header).visibility = View.VISIBLE
            dialogView.findViewById<TextView>(R.id.tv_import_failed_reasons).text =
                result.failedReasons.joinToString("\n")
        }
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    // ════════════════════════════════════════════════════════
    // 动画（保持原有逻辑不变）
    // ════════════════════════════════════════════════════════

    private fun startAnimation() {
        val successColor = getColor(R.color.colorSuccess)
        val transparent = getColor(android.R.color.transparent)
        val centerX = startButton.left + startButton.width / 2
        val centerY = startButton.top + startButton.height / 2
        val endRadius = hypot(constraintLayout.width.toDouble(), constraintLayout.height.toDouble()).toFloat()

        val bg = View(this).apply {
            setBackgroundColor(successColor)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        }
        constraintLayout.addView(bg, 0)

        val anim = ViewAnimationUtils.createCircularReveal(bg, centerX, centerY, 0f, endRadius)
        anim.duration = 500
        anim.interpolator = AccelerateInterpolator()
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                constraintLayout.removeView(bg)
                constraintLayout.setBackgroundColor(transparent)
                constraintLayoutResult.visibility = View.VISIBLE
                constraintLayoutResult.setBackgroundColor(successColor)
                isAnimating = true
            }
        })
        anim.start()
    }

    private fun reverseAnimation() {
        val darkBg = getColor(R.color.colorBgDark)
        val successColor = getColor(R.color.colorSuccess)
        constraintLayout.setBackgroundColor(darkBg)
        constraintLayoutResult.visibility = View.INVISIBLE

        val centerX = startButton.left + startButton.width / 2
        val centerY = startButton.top + startButton.height / 2
        val endRadius = hypot(constraintLayout.width.toDouble(), constraintLayout.height.toDouble()).toFloat()

        val bg = View(this).apply {
            setBackgroundColor(successColor)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        }
        constraintLayout.addView(bg, 0)

        val anim = ViewAnimationUtils.createCircularReveal(bg, centerX, centerY, endRadius, 0f)
        anim.duration = 500
        anim.interpolator = AccelerateInterpolator()
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                constraintLayout.removeView(bg)
                constraintLayout.background = resources.getDrawable(R.drawable.bg_main_gradient, theme)
            }
        })
        anim.start()
    }
}
