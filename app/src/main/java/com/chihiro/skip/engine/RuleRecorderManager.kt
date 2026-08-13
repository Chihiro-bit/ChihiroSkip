package com.chihiro.skip.engine

import android.content.Context
import com.chihiro.skip.model.CandidateNode
import com.chihiro.skip.model.RecordingSession

class RuleRecorderManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: RuleRecorderManager? = null

        fun getInstance(context: Context): RuleRecorderManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleRecorderManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    private var currentSession: RecordingSession? = null

    private val candidateListeners = mutableListOf<(List<CandidateNode>) -> Unit>()

    fun startRecording(packageName: String, activityName: String, screenWidth: Int, screenHeight: Int) {
        currentSession = RecordingSession(
            packageName = packageName,
            activityName = activityName,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        isRecording = true
    }

    fun stopRecording() {
        isRecording = false
        currentSession = null
        synchronized(candidateListeners) { candidateListeners.clear() }
    }

    fun getCurrentSession(): RecordingSession? = currentSession

    fun updateCandidates(candidates: List<CandidateNode>) {
        currentSession = currentSession?.copy(candidates = candidates)
        val snapshot = synchronized(candidateListeners) { candidateListeners.toList() }
        snapshot.forEach { it(candidates) }
    }

    /** 点候选切换选中状态（多选，生成时按选中顺序作为 candidateActions） */
    fun toggleSelect(node: CandidateNode) {
        currentSession = currentSession?.let { s ->
            if (node in s.selectedNodes) s.copy(selectedNodes = s.selectedNodes - node)
            else s.copy(selectedNodes = s.selectedNodes + node)
        }
    }

    fun clearSelection() {
        currentSession = currentSession?.copy(selectedNodes = emptyList())
    }

    fun getSelectedNodes(): List<CandidateNode> = currentSession?.selectedNodes ?: emptyList()

    fun addCandidateListener(listener: (List<CandidateNode>) -> Unit) {
        synchronized(candidateListeners) { candidateListeners.add(listener) }
    }

    fun removeCandidateListener(listener: (List<CandidateNode>) -> Unit) {
        synchronized(candidateListeners) { candidateListeners.remove(listener) }
    }
}
