package com.chihiro.skip.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.chihiro.skip.model.CandidateNode

/**
 * 录制助手的全屏透明高亮层：把候选节点位置画框标出（含序号 badge）。
 * bounds 为屏幕绝对坐标（getBoundsInScreen），全屏 0,0 原点直接绘制。
 * 窗口参数为 FLAG_NOT_TOUCHABLE，触摸穿透到下层。
 */
class CandidateHighlightView(context: Context) : View(context) {

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.parseColor("#FF6C63FF")
    }
    private val selectedFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.parseColor("#FF00D1FF")
    }
    private val selectedFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#336C63FF")
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF6C63FF")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var candidates: List<CandidateNode> = emptyList()
    private var selected: Set<CandidateNode> = emptySet()

    fun render(list: List<CandidateNode>, selected: Set<CandidateNode>) {
        candidates = list
        this.selected = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        candidates.forEachIndexed { index, node ->
            val rect: Rect = node.bounds
            val isSelected = node in selected
            if (isSelected) canvas.drawRect(rect, selectedFillPaint)
            canvas.drawRect(rect, if (isSelected) selectedFramePaint else framePaint)
            drawBadge(canvas, rect, index + 1, isSelected)
        }
    }

    private fun drawBadge(canvas: Canvas, rect: Rect, number: Int, isSelected: Boolean) {
        val r = 10f * resources.displayMetrics.density
        val cx = rect.left.toFloat()
        val cy = rect.top.toFloat()
        val paint = Paint(badgePaint).apply {
            color = if (isSelected) Color.parseColor("#FF00D1FF") else Color.parseColor("#FF6C63FF")
        }
        canvas.drawCircle(cx, cy, r, paint)
        val y = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(number.toString(), cx, y, badgeTextPaint)
    }
}
