package my.adam.financewidget

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var note: TextView
    private lateinit var savings: EditText
    private lateinit var grams: EditText
    private val prefs by lazy { getSharedPreferences("adam_widget", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF101A2B.toInt())
        }
        fun label(s: String) = TextView(this).apply {
            text = s; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
        }
        fun field(hintText: String, value: String) = EditText(this).apply {
            hint = hintText; setSingleLine(); setText(value)
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9EACC2.toInt())
        }

        root.addView(label("ADAM FINANCE • Tetapan widget"))
        root.addView(label("Jumlah tabungan tunai (RM)"))
        savings = field("Contoh 6500", prefs.getString("savings", "") ?: "")
        root.addView(savings, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("Berat emas 999 (gram)"))
        grams = field("Contoh 1.5", prefs.getString("grams", "") ?: "")
        root.addView(grams, LinearLayout.LayoutParams(-1, -2))

        val save = Button(this).apply { text = "Simpan & kemas kini widget" }
        root.addView(save, LinearLayout.LayoutParams(-1, -2))
        save.setOnClickListener {
            prefs.edit().putString("savings", savings.text.toString())
                .putString("grams", grams.text.toString()).apply()
            refreshWidget()
            note.text = "Disimpan. Widget sedang dikemas kini."
        }

        val import = Button(this).apply { text = "Import backup dari dashboard" }
        root.addView(import, LinearLayout.LayoutParams(-1, -2))
        import.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, 4102)
        }
        note = label("Tip: Dashboard → Tools → Export. Kemudian import fail JSON di sini.")
        note.textSize = 12f
        root.addView(note)
        root.addView(label("Harga emas dikemas kini berkala melalui internet."))
        setContentView(root)
        GoldRefreshWorker.enqueue(this)
        FinanceWidgetProvider.updateAll(this)
    }

    @Deprecated("Use document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4102 || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val json = contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: throw IllegalStateException("Fail tidak dapat dibaca")
            val backup = JSONObject(json)
            if (!backup.has("cash") || !backup.has("goldGrams")) {
                throw IllegalArgumentException("Fail bukan backup ADAM Finance")
            }
            val cash = backup.optDouble("cash", 0.0)
            val goldGrams = backup.optDouble("goldGrams", 0.0)
            if (cash < 0 || goldGrams < 0) throw IllegalArgumentException("Nilai tidak sah")
            savings.setText(cash.toString())
            grams.setText(goldGrams.toString())
            prefs.edit().putString("savings", cash.toString())
                .putString("grams", goldGrams.toString()).apply()
            refreshWidget()
            note.text = "Backup berjaya diimport. Widget telah dikemas kini."
        } catch (e: Exception) {
            note.text = "Import gagal: " + (e.message ?: "Fail tidak sah")
        }
    }

    private fun refreshWidget() {
        FinanceWidgetProvider.updateAll(this)
        GoldRefreshWorker.enqueue(this)
    }
}
