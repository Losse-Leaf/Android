package com.looseleaf.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.looseleaf.app.MainActivity
import com.looseleaf.app.R

/**
 * 桌面极速记账与暗房快捷方式小组件 (Quick Capture Widget) - docs/mobile.md §3.5
 * 单触直达「触觉记账小键盘」或「随行暗房直拍」
 */
class QuickLaunchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_launch)

            // 直达记账意图
            val ledgerIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("shortcut_route", "ledger")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val ledgerPending = PendingIntent.getActivity(
                context, 101, ledgerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_launch_ledger, ledgerPending)

            // 直达暗房意图
            val darkroomIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("shortcut_route", "darkroom")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val darkroomPending = PendingIntent.getActivity(
                context, 102, darkroomIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_launch_darkroom, darkroomPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
