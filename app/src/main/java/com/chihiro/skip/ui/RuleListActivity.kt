package com.chihiro.skip.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chihiro.skip.R
import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.repository.RuleRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class RuleListActivity : AppCompatActivity() {

    private lateinit var ruleRepo: RuleRepository
    private lateinit var adapter: RuleAdapter
    private lateinit var rvRules: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.rule_list_title)
            setDisplayHomeAsUpEnabled(true)
        }

        ruleRepo = RuleRepository.getInstance(this)
        rvRules = findViewById(R.id.rv_rules)
        tvEmpty = findViewById(R.id.tv_empty_rules)

        adapter = RuleAdapter(ruleRepo.rules.toMutableList(),
            onToggle = { rule, enabled ->
                ruleRepo.setRuleEnabled(rule.id, enabled)
            },
            onDelete = { rule ->
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_rule_confirm, rule.name))
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        ruleRepo.deleteRule(rule.id)
                        refreshList()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        rvRules.layoutManager = LinearLayoutManager(this)
        rvRules.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rvRules.adapter = adapter
        refreshList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshList() {
        val rules = ruleRepo.rules
        adapter.submitList(rules.toMutableList())
        tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Adapter ─────────────────────────────────────────────
    class RuleAdapter(
        private val items: MutableList<AdSkipRule>,
        private val onToggle: (AdSkipRule, Boolean) -> Unit,
        private val onDelete: (AdSkipRule) -> Unit
    ) : RecyclerView.Adapter<RuleAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_rule_name)
            val tvPackage: TextView = v.findViewById(R.id.tv_rule_package)
            val tvPriority: TextView = v.findViewById(R.id.tv_rule_priority)
            val swEnabled: SwitchMaterial = v.findViewById(R.id.sw_rule_enabled)
            val btnDelete: View = v.findViewById(R.id.btn_rule_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val rule = items[pos]
            h.tvName.text = rule.name.ifEmpty { "(未命名规则)" }
            h.tvPackage.text = rule.packageName.ifEmpty { "适用于所有 App" }
            h.tvPriority.text = "优先级：${rule.priority}"
            h.swEnabled.isChecked = rule.enabled
            h.swEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(rule, checked)
            }
            h.btnDelete.setOnClickListener { onDelete(rule) }
        }

        fun submitList(newList: MutableList<AdSkipRule>) {
            items.clear()
            items.addAll(newList)
            notifyDataSetChanged()
        }
    }
}
