package my.adam.financewidget

import android.content.Context
import androidx.work.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GoldRefreshWorker(ctx:Context, params:WorkerParameters): CoroutineWorker(ctx,params) {
    override suspend fun doWork(): Result = try {
        val url=URL("https://drexian-08.github.io/adam-finance-dashboard/gold-price.json")
        val conn=(url.openConnection() as HttpURLConnection).apply { connectTimeout=10000; readTimeout=10000; setRequestProperty("Cache-Control","no-cache") }
        val json=JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        conn.disconnect()
        val price=json.getDouble("pricePerGram")
        if(price<=0) throw IllegalStateException("Invalid price")
        val stamp=SimpleDateFormat("HH:mm",Locale("ms","MY")).format(Date())
        applicationContext.getSharedPreferences("adam_widget",Context.MODE_PRIVATE).edit().putFloat("gold_price",price.toFloat()).putString("updated",stamp).apply()
        FinanceWidgetProvider.updateAll(applicationContext)
        Result.success()
    } catch(e:Exception) { Result.retry() }
    companion object {
        fun enqueue(context:Context) {
            val request=PeriodicWorkRequestBuilder<GoldRefreshWorker>(30,TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("adam_gold_refresh",ExistingPeriodicWorkPolicy.KEEP,request)
            val once=OneTimeWorkRequestBuilder<GoldRefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork("adam_gold_refresh_now",ExistingWorkPolicy.REPLACE,once)
        }
    }
}
