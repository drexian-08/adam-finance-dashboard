package my.adam.financewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.Locale

class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        GoldRefreshWorker.enqueue(context)
    }
    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, FinanceWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }
        private fun rm(v: Double): String =
            NumberFormat.getCurrencyInstance(Locale("ms", "MY")).format(v)

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val p = context.getSharedPreferences("adam_widget", Context.MODE_PRIVATE)
            val cash = p.getString("savings", "0")?.toDoubleOrNull() ?: 0.0
            val grams = p.getString("grams", "0")?.toDoubleOrNull() ?: 0.0
            val price = p.getFloat("gold_price", 0f).toDouble()
            val views = RemoteViews(context.packageName, R.layout.finance_widget)
            views.setTextViewText(R.id.savings, rm(cash))
            views.setTextViewText(R.id.gold_price, if (price > 0) rm(price) else "RM —")
            views.setTextViewText(R.id.gold_value, rm(price * grams))
            views.setTextViewText(R.id.gold_grams, String.format(Locale.US, "%.2f g dimiliki", grams))
            val stamp = p.getString("updated", "") ?: ""
            views.setTextViewText(R.id.updated, if (stamp.isNotBlank()) "UPDATED $stamp" else "MENUNGGU DATA")
            manager.updateAppWidget(id, views)
        }
    }
}