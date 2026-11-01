package my.adam.financewidget

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("adam_widget", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad); setBackgroundColor(0xFF101A2B.toInt()) }
        fun label(s:String) = TextView(this).apply { text=s; textSize=14f; setTextColor(0xFFFFFFFF.toInt()) }
        fun field(hint:String,value:String):EditText = EditText(this).apply { this.hint=hint; setSingleLine(); setText(value); setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9EACC2.toInt()) }
        root.addView(label("ADAM FINANCE • Tetapan widget"))
        root.addView(label("Masukkan jumlah tabungan tunai (RM)"))
        val savings=field("Contoh 6500",prefs.getString("savings","") ?: "")
        root.addView(savings,LinearLayout.LayoutParams(-1,-2))
        root.addView(label("Berat emas 999 yang dimiliki (gram)"))
        val grams=field("Contoh 1.5",prefs.getString("grams","") ?: "")
        root.addView(grams,LinearLayout.LayoutParams(-1,-2))
        val note=label("Harga emas diambil daripada dashboard ADAM dan dikemas kini berkala. Widget Android tertakluk pada penjimatan bateri sistem.")
        root.addView(note)
        val save=Button(this).apply { text="Simpan & kemas kini widget" }
        root.addView(save,LinearLayout.LayoutParams(-1,-2))
        save.setOnClickListener {
            prefs.edit().putString("savings",savings.text.toString()).putString("grams",grams.text.toString()).apply()
            FinanceWidgetProvider.updateAll(this)
            GoldRefreshWorker.enqueue(this)
            note.text="Disimpan. Widget sedang dikemas kini."
        }
        setContentView(root)
        GoldRefreshWorker.enqueue(this)
        FinanceWidgetProvider.updateAll(this)
    }
}
