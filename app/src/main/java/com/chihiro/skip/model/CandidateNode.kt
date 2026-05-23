package com.chihiro.skip.model

import android.graphics.Rect

data class CandidateNode(
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val className: String = "",
    val bounds: Rect = Rect(),
    val centerX: Int = 0,
    val centerY: Int = 0,
    val xRatio: Float = 0f,
    val yRatio: Float = 0f,
    val clickable: Boolean = false,
    val confidenceScore: Int = 0,
    val reason: String = ""
)
