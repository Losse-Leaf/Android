package com.looseleaf.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.looseleaf.app.MainActivity
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 城市印记与状态小组件 (Status & City Widget - 2x2) - docs/mobile.md §3.5
 * 显示主理人当前所在城市与自白信条（严格从后端动态拉取，严禁硬编码任何城市地名与人名）
 */
class CityStatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val apiClient = APIClient.getInstance(context)
            val database = AppDatabase.getInstance(context)

            val profile = runCatching { apiClient.fetchCuratorProfile() }.getOrNull()
            val txs = database.transactionDao().getAllTransactions()
            val todayCents = txs.filter { it.type == "expense" }.sumOf { it.amount }

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_city_status)

                val cityName = profile?.currentCity?.ifBlank { profile.location }?.ifBlank { "旅居漫步中" } ?: "散页手账"
                val motto = profile?.motto?.ifBlank { "真实记录生活自留地" } ?: "真实记录生活自留地"

                views.setTextViewText(R.id.widget_city, cityName)
                views.setTextViewText(R.id.widget_motto, "“ $motto ”")
                views.setTextViewText(R.id.widget_today_expense, "累计记账: ${FormatUtils.centsToYuanString(todayCents)}")

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
