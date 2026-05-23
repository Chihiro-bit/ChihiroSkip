package com.chihiro.skip.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chihiro.skip.R
import com.chihiro.skip.model.SkipLog
import com.chihiro.skip.repository.SkipLogRepository
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SkipLogActivity : AppCompatActivity() {

    private val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skip_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.log_list_title)
            setDisplayHomeAsUpEnabled(true)
        }

        val logRepo = SkipLogRepository.getInstance(this)
        val logs = logRepo.logs
        val rv = findViewById<RecyclerView>(R.id.rv_logs)
        val tvEmpty = findViewById<TextView>(R.id.tv_empty_logs)

        if (logs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            rv.layoutManager = LinearLayoutManager(this)
            rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
            rv.adapter = LogAdapter(logs, sdf)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    class LogAdapter(
        private val items: List<SkipLog>,
        private val sdf: SimpleDateFormat
    ) : RecyclerView.Adapter<LogAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvRule: TextView = v.findViewById(R.id.tv_log_rule)
            val tvResult: TextView = v.findViewById(R.id.tv_log_result)
            val tvPackage: TextView = v.findViewById(R.id.tv_log_package)
            val tvAction: TextView = v.findViewById(R.id.tv_log_action)
            val tvTime: TextView = v.findViewById(R.id.tv_log_time)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_skip_log, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val log = items[pos]
            h.tvRule.text = log.ruleName.ifEmpty { "基础识别" }
            h.tvResult.text = if (log.success) "✓ 成功" else "✗ 失败"
            h.tvResult.setTextColor(
                h.itemView.context.getColor(if (log.success) R.color.status_on else R.color.danger_red)
            )
            h.tvPackage.text = log.packageName
            h.tvAction.text = log.actionType
            h.tvTime.text = sdf.format(Date(log.timestamp))
        }
    }
}
