package com.chihiro.skip.engine

import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.model.CandidateNode
import com.chihiro.skip.model.MatchCondition
import com.chihiro.skip.model.RecordingSession
import com.chihiro.skip.model.RuleAction

class RuleGenerator {

    fun generateRule(session: RecordingSession): AdSkipRule? {
        val node = session.selectedNode ?: return null
        return AdSkipRule(
            name = buildRuleName(node, session),
            packageName = session.packageName,
            matchCondition = buildMatchCondition(session, node),
            action = buildPrimaryAction(node),
            relativeAction = buildRelativeAction(node, session),
            allowCoordinateClick = session.isCoordinateCapture,
            recordedScreenWidth = session.screenWidth,
            recordedScreenHeight = session.screenHeight,
            delayMs = 300L,
            cooldownMs = 1500L
        )
    }

    private fun buildPrimaryAction(node: CandidateNode): RuleAction = when {
        node.viewId.isNotEmpty() -> RuleAction(
            type = "clickNode", clickBy = "viewId", value = node.viewId
        )
        node.text.isNotEmpty() -> RuleAction(
            type = "clickNode", clickBy = "text", value = node.text
        )
        node.contentDescription.isNotEmpty() -> RuleAction(
            type = "clickNode", clickBy = "contentDescription", value = node.contentDescription
        )
        else -> RuleAction(
            type = "clickRelativeCoordinate", clickBy = "relativeCoordinate",
            xRatio = node.xRatio, yRatio = node.yRatio
        )
    }

    private fun buildRelativeAction(node: CandidateNode, session: RecordingSession): RuleAction? {
        if (node.xRatio <= 0f || node.yRatio <= 0f) return null
        return RuleAction(
            type = "clickRelativeCoordinate", clickBy = "relativeCoordinate",
            xRatio = node.xRatio, yRatio = node.yRatio,
            baseWidth = session.screenWidth, baseHeight = session.screenHeight
        )
    }

    private fun buildMatchCondition(session: RecordingSession, node: CandidateNode): MatchCondition {
        val textList = if (node.text.isNotEmpty()) listOf(node.text) else emptyList()
        return MatchCondition(
            activityName = session.activityName,
            textEquals = textList,
            matchType = if (session.activityName.isEmpty()) "packageOnly" else ""
        )
    }

    private fun buildRuleName(node: CandidateNode, session: RecordingSession): String {
        val label = when {
            node.text.isNotEmpty() -> "\"${node.text}\""
            node.contentDescription.isNotEmpty() -> "\"${node.contentDescription}\""
            node.viewId.isNotEmpty() -> node.viewId.substringAfterLast('/')
            else -> "(${node.centerX},${node.centerY})"
        }
        val pkg = session.packageName.substringAfterLast('.')
        return "$pkg $label"
    }
}
