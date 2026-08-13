package com.chihiro.skip.engine

import com.chihiro.skip.model.AdSkipRule
import com.chihiro.skip.model.CandidateNode
import com.chihiro.skip.model.MatchCondition
import com.chihiro.skip.model.RecordingSession
import com.chihiro.skip.model.RuleAction

class RuleGenerator {

    fun generateRule(session: RecordingSession): AdSkipRule? {
        val nodes = session.selectedNodes
        if (nodes.isEmpty()) return null
        val primary = nodes.first()
        return AdSkipRule(
            name = buildRuleName(primary, session),
            packageName = session.packageName,
            matchCondition = buildMatchCondition(session, primary),
            action = buildPrimaryAction(primary),
            relativeAction = buildRelativeAction(primary, session),
            candidateActions = nodes.map { buildPrimaryAction(it) },
            allowCoordinateClick = session.isCoordinateCapture,
            recordedScreenWidth = session.screenWidth,
            recordedScreenHeight = session.screenHeight,
            delayMs = 300L,
            cooldownMs = 1500L
        )
    }

    private fun buildPrimaryAction(node: CandidateNode): RuleAction = when {
        // OCR 节点不在无障碍树里，clickNode 永远点不到 → 强制坐标动作
        node.isFromOcr -> RuleAction(
            type = "clickRelativeCoordinate", clickBy = "relativeCoordinate",
            xRatio = node.xRatio, yRatio = node.yRatio
        )
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
        // OCR 文本与无障碍树无关，textEquals 只会造成假匹配
        val textList = if (!node.isFromOcr && node.text.isNotEmpty()) listOf(node.text) else emptyList()
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
