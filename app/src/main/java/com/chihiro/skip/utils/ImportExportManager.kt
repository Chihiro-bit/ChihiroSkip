package com.chihiro.skip.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chihiro.skip.repository.RuleRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object ImportExportManager {

    private const val TAG = "ImportExportManager"

    /**
     * 从 Uri 读取 JSON 并导入规则，返回导入结果。
     * 必须在后台线程调用。
     */
    fun importFromUri(context: Context, uri: Uri): RuleRepository.ImportResult {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: return RuleRepository.ImportResult(0, 1, listOf("无法读取文件"))

            // 基础安全检查：只接受 JSON 对象
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) {
                return RuleRepository.ImportResult(0, 1, listOf("文件不是合法的 JSON 对象"))
            }
            if (trimmed.length > 5 * 1024 * 1024) {
                return RuleRepository.ImportResult(0, 1, listOf("文件过大，超过 5 MB 限制"))
            }

            RuleRepository.getInstance(context).importRules(json)
        } catch (e: Exception) {
            Log.e(TAG, "importFromUri failed", e)
            RuleRepository.ImportResult(0, 1, listOf("导入异常：${e.message}"))
        }
    }

    /**
     * 将规则导出到 Uri 指向的文件。
     * 必须在后台线程调用。
     */
    fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = RuleRepository.getInstance(context).exportRules()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { it.write(json) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "exportToUri failed", e)
            false
        }
    }
}
