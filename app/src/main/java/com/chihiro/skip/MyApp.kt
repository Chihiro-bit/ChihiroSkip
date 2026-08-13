package com.chihiro.skip

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.chihiro.skip.accessibility.FastAccessibilityService
import com.chihiro.skip.manager.LanguageHelper
import com.chihiro.skip.service.MyAccessibilityService

class MyApp : Application() {
    companion object {
        lateinit var instance: Application
        const val NOTIF_CHANNEL_ID = "chihiro_skip_running"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        FastAccessibilityService.init(
            instance, MyAccessibilityService::class.java, arrayListOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 用应用内所选语言的上下文取文案（Application 不在 attachBaseContext 阶段 wrap）
            val ctx = LanguageHelper.wrap(this)
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
