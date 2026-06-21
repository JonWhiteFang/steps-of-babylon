package com.whitefang.stepsofbabylon.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.MainActivity
import java.text.NumberFormat

class StepWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS = "widget_data"
        private const val KEY_STEPS = "daily_steps"
        private const val KEY_BALANCE = "balance"

        fun saveData(context: Context, dailySteps: Long, balance: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_STEPS, dailySteps)
                .putLong(KEY_BALANCE, balance)
                .apply()
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StepWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val steps = prefs.getLong(KEY_STEPS, 0)
            val balance = prefs.getLong(KEY_BALANCE, 0)
            val fmt = NumberFormat.getNumberInstance()
            val views = RemoteViews(context.packageName, R.layout.widget_step_counter).apply {
                setTextViewText(
                    R.id.widget_daily_steps,
                    context.resources.getQuantityString(
                        R.plurals.widget_steps,
                        steps.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                        fmt.format(steps),
                    ),
                )
                setTextViewText(R.id.widget_balance, context.getString(R.string.widget_balance, fmt.format(balance)))
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
            }
            for (id in ids) manager.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAllWidgets(context)
    }
}
