package com.chihiro.skip.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chihiro.skip.R
import com.chihiro.skip.engine.RuleGenerator
import com.chihiro.skip.engine.RuleRecorderManager
import com.chihiro.skip.manager.LanguageHelper
import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.model.CandidateNode
import com.chihiro.skip.repository.RuleRepository
import com.chihiro.skip.ui.CandidateHighlightView
import com.chihiro.skip.ui.RulePreviewActivity

class FloatingRecorderService : Service() {

    companion object {
        const val ACTION_STOP = "com.chihiro.skip.STOP_RECORDER"

        @Volatile
        var liveInstance: FloatingRecorderService? = null
            private set
    }

    private val TAG = "FloatingRecorder"
    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var tvStatus: TextView
    private lateinit var tvPackage: TextView
    private lateinit var rvCandidates: RecyclerView
    private lateinit var candidateAdapter: CandidateAdapter
    private lateinit var manager: RuleRecorderManager

    // ── 全屏高亮层（先于面板 addView，保证面板 z 序在上）────────
    private var highlightOverlay: CandidateHighlightView? = null
    private var currentCandidates: List<CandidateNode> = emptyList()

    private val candidateListener: (List<CandidateNode>) -> Unit = { candidates ->
        floatView.post {
            currentCandidates = candidates.take(8)
            candidateAdapter.submitList(candidates)
            tvStatus.text = getString(R.string.recorder_found_candidates, candidates.size)
            highlightOverlay?.render(currentCandidates, manager.getSelectedNodes().toSet())
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        liveInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        manager = RuleRecorderManager.getInstance(this)
        manager.addCandidateListener(candidateListener)
        createHighlightOverlay()
        createFloatingWindow()
    }

    /** 全屏透明高亮层：先 addView（z 序在下），面板后 add 压在其上 */
    private fun createHighlightOverlay() {
        highlightOverlay = CandidateHighlightView(this)
        windowManager.addView(
            highlightOverlay,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
        )
    }

    private fun removeHighlightOverlay() {
        highlightOverlay?.let {
            runCatching { windowManager.removeView(it) }
            highlightOverlay = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        liveInstance = null
        manager.removeCandidateListener(candidateListener)
        manager.stopRecording()
        removeHighlightOverlay()
        try { windowManager.removeView(floatView) } catch (_: Exception) {}
    }

    // ── Floating window ──────────────────────────────────────
    private fun createFloatingWindow() {
        floatView = LayoutInflater.from(this).inflate(R.layout.layout_floating_recorder, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 200
        }

        tvStatus = floatView.findViewById(R.id.tv_recorder_status)
        tvPackage = floatView.findViewById(R.id.tv_recorder_package)
        rvCandidates = floatView.findViewById(R.id.rv_candidates)

        candidateAdapter = CandidateAdapter { node ->
            manager.toggleSelect(node)
            candidateAdapter.notifyDataSetChanged()
            tvStatus.text = getString(R.string.recorder_selected_count, manager.getSelectedNodes().size)
            highlightOverlay?.post {
                highlightOverlay?.render(currentCandidates, manager.getSelectedNodes().toSet())
            }
        }
        rvCandidates.layoutManager = LinearLayoutManager(this)
        rvCandidates.adapter = candidateAdapter

        floatView.findViewById<View>(R.id.btn_recorder_scan).setOnClickListener {
            tvStatus.text = getString(R.string.recorder_scanning)
            val svc = MyAccessibilityService.liveInstance
            if (svc != null) svc.requestScan()
            else Toast.makeText(this, R.string.recorder_need_service, Toast.LENGTH_SHORT).show()
        }

        floatView.findViewById<View>(R.id.btn_recorder_generate).setOnClickListener {
            val session = manager.getCurrentSession() ?: return@setOnClickListener
            if (manager.getSelectedNodes().isEmpty()) {
                Toast.makeText(this, R.string.recorder_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val rule = RuleGenerator().generateRule(session) ?: return@setOnClickListener
            removeHighlightOverlay()
            launchPreview(rule)
        }

        floatView.findViewById<View>(R.id.btn_recorder_close).setOnClickListener {
            stopSelf()
        }

        setupDrag(floatView.findViewById(R.id.drag_handle))
        windowManager.addView(floatView, layoutParams)
        tvStatus.text = getString(R.string.recorder_ready)
    }

    fun onCandidatesReady(pkg: String, activityName: String) {
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        manager.startRecording(pkg, activityName, sw, sh)
        floatView.post { tvPackage.text = pkg.substringAfterLast('.') }
    }

    private fun launchPreview(rule: AdSkipRule) {
        RuleRepository.getInstance(this).addRule(rule)
        val intent = Intent(this, RulePreviewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(RulePreviewActivity.EXTRA_RULE_ID, rule.id)
        }
        startActivity(intent)
    }

    private fun setupDrag(dragHandle: View) {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x; initialY = layoutParams.y
                    touchX = event.rawX; touchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchX).toInt()
                    layoutParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(floatView, layoutParams); true
                }
                else -> false
            }
        }
    }

    // ── Candidate RecyclerView adapter ───────────────────────
    inner class CandidateAdapter(
        private val onSelect: (CandidateNode) -> Unit
    ) : RecyclerView.Adapter<CandidateAdapter.VH>() {

        private var items = listOf<CandidateNode>()

        fun submitList(list: List<CandidateNode>) {
            items = list.take(8)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_candidate_node, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvLabel: TextView = itemView.findViewById(R.id.tv_candidate_label)
            private val tvScore: TextView = itemView.findViewById(R.id.tv_candidate_score)
            private val tvReason: TextView = itemView.findViewById(R.id.tv_candidate_reason)

            fun bind(node: CandidateNode) {
                tvLabel.text = when {
                    node.text.isNotEmpty() -> node.text
                    node.contentDescription.isNotEmpty() -> node.contentDescription
                    node.viewId.isNotEmpty() -> node.viewId.substringAfterLast('/')
                    else -> "(${node.centerX},${node.centerY})"
                }
                tvScore.text = getString(R.string.candidate_score, node.confidenceScore)
                tvReason.text = node.reason
                itemView.isSelected = manager.getSelectedNodes().contains(node)
                itemView.setOnClickListener { onSelect(node) }
            }
        }
    }
}
