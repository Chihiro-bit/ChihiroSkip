package com.chihiro.skip.ui

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chihiro.skip.R
import com.chihiro.skip.manager.LanguageHelper
import com.chihiro.skip.repository.RuleRepository
import com.google.android.material.appbar.MaterialToolbar

class RulePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
    }

    private lateinit var etName: EditText
    private lateinit var tvPackage: TextView
    private lateinit var tvAction: TextView
    private lateinit var tvMatchCondition: TextView
    private lateinit var tvRelativeAction: TextView

    private lateinit var repo: RuleRepository
    private var ruleId: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_preview)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        repo = RuleRepository.getInstance(this)
        ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: run { finish(); return }

        etName = findViewById(R.id.et_rule_name)
        tvPackage = findViewById(R.id.tv_rule_package)
        tvAction = findViewById(R.id.tv_rule_action)
        tvMatchCondition = findViewById(R.id.tv_rule_match)
        tvRelativeAction = findViewById(R.id.tv_rule_relative)

        loadRule()

        findViewById<android.view.View>(R.id.btn_save_rule).setOnClickListener { saveRule() }
        findViewById<android.view.View>(R.id.btn_cancel_rule).setOnClickListener {
            repo.deleteRule(ruleId)
            finish()
        }

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.rule_preview_title)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadRule() {
        val rule = repo.rules.find { it.id == ruleId } ?: return
        etName.setText(rule.name)
        tvPackage.text = rule.packageName
        val a = rule.action
        tvAction.text = when (a.type) {
            "clickNode" -> "${a.clickBy}: \"${a.value}\""
            "clickRelativeCoordinate" -> getString(R.string.preview_coord_relative, a.xRatio, a.yRatio)
            "clickCoordinate" -> getString(R.string.preview_coord_absolute, a.x, a.y)
            else -> a.type
        }
        val m = rule.matchCondition
        tvMatchCondition.text = buildString {
            if (m.activityName.isNotEmpty()) append("Activity: ${m.activityName}\n")
            if (m.textEquals.isNotEmpty()) append(getString(R.string.preview_match_text, m.textEquals))
            if (m.viewId.isNotEmpty()) append("ViewId: ${m.viewId}\n")
            if (isEmpty()) append(getString(R.string.preview_match_package))
        }.trimEnd()
        val r = rule.relativeAction
        tvRelativeAction.text = if (r != null)
            getString(R.string.preview_alt_coord, r.xRatio, r.yRatio)
        else getString(R.string.none)
    }

    private fun saveRule() {
        val rule = repo.rules.find { it.id == ruleId } ?: return
        val newName = etName.text.toString().trim().ifEmpty { rule.name }
        repo.updateRule(rule.copy(name = newName))
        Toast.makeText(this, R.string.rule_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
