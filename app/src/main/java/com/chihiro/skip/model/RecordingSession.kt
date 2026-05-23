package com.chihiro.skip.model

import java.util.UUID

data class RecordingSession(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String = "",
    val activityName: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val capturedAt: Long = System.currentTimeMillis(),
    val candidates: List<CandidateNode> = emptyList(),
    val selectedNode: CandidateNode? = null,
    val isCoordinateCapture: Boolean = false
)
