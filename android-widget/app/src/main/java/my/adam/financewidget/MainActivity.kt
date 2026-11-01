package my.adam.financewidget

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var note: TextView
    private lateinit var savings: EditText
    private lateinit var grams: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private val prefs by lazy { getSharedPreferences("adam_widget", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val API_KEY = "AIzaSyBoxjdpEuVaHPjc2MvSmb_45R42y8c5vKw"
        private const val PROJECT_ID = "adam-finance-private"
        private const val AUTH_BASE = "https://identitytoolkit.googleapis.com/v1"
        private const val FIRESTORE_BASE = "https://firestore.googleapis.com/v1"
    }

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
        fun field(hintText: String, value: String = "", secret: Boolean = false) = EditText(this).apply {
            hint = hintText; setSingleLine(); setText(value)
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9EACC2.toInt())
            if (secret) inputType = 129
        }

        root.addView(label("ADAM FINANCE • Cloud & Widget"))
        root.addView(label("Akaun Firebase (email)"))
        email = field("Email Firebase", prefs.getString("cloud_email", "") ?: "")
        root.addView(email, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("Password"))
        password = field("Password Firebase", secret = true)
        root.addView(password, LinearLayout.LayoutParams(-1, -2))

        val login = Button(this).apply { text = "Log masuk / daftar akaun" }
        root.addView(login, LinearLayout.LayoutParams(-1, -2))
        login.setOnClickListener { authenticate() }

        val sync = Button(this).apply { text = "↻ Sync cloud sekarang" }
        root.addView(sync, LinearLayout.LayoutParams(-1, -2))
        sync.setOnClickListener { syncCloud() }

        root.addView(label("Jumlah tabungan tunai (RM)"))
        savings = field("Contoh 6500", prefs.getString("savings", "") ?: "")
        root.addView(savings, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("Berat emas 999 (gram)"))
        grams = field("Contoh 1.5", prefs.getString("grams", "") ?: "")
        root.addView(grams, LinearLayout.LayoutParams(-1, -2))

        val save = Button(this).apply { text = "Simpan nilai manual & kemas kini widget" }
        root.addView(save, LinearLayout.LayoutParams(-1, -2))
        save.setOnClickListener {
            prefs.edit().putString("savings", savings.text.toString())
                .putString("grams", grams.text.toString()).apply()
            refreshWidget()
            note.text = "Disimpan pada peranti."
        }

        val import = Button(this).apply { text = "Import backup JSON (pilihan)" }
        root.addView(import, LinearLayout.LayoutParams(-1, -2))
        import.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            }
            startActivityForResult(intent, 4102)
        }
        note = label("Log masuk menggunakan akaun Firebase yang sama seperti dashboard. Sync cloud akan kemas kini widget.")
        note.textSize = 12f
        root.addView(note)
        root.addView(label("Harga emas dikemas kini berkala melalui internet."))
        setContentView(root)
        GoldRefreshWorker.enqueue(this)
        FinanceWidgetProvider.updateAll(this)
        if (prefs.getString("refresh_token", null) != null) syncCloud()
    }

    private fun authenticate() {
        val userEmail = email.text.toString().trim()
        val userPassword = password.text.toString()
        if (userEmail.isBlank() || userPassword.length < 6) {
            note.text = "Masukkan email dan password sekurang-kurangnya 6 aksara."; return
        }
        note.text = "Sedang log masuk…"
        executor.execute {
            try {
                var result = authRequest("accounts:signInWithPassword", userEmail, userPassword)
                if (result == null) result = authRequest("accounts:signUp", userEmail, userPassword)
                if (result == null) throw IllegalStateException("Log masuk/daftar gagal. Semak email, password dan Firebase Authentication.")
                prefs.edit().putString("cloud_email", userEmail)
                    .putString("refresh_token", result.getString("refreshToken"))
                    .putString("id_token", result.getString("idToken"))
                    .putString("uid", result.getString("localId")).apply()
                runOnUiThread { note.text = "Akaun berjaya disambungkan. Sedang sync…"; syncCloud() }
            } catch (e: Exception) {
                runOnUiThread { note.text = "Ralat akaun: ${e.message}" }
            }
        }
    }

    private fun authRequest(endpoint: String, userEmail: String, userPassword: String): JSONObject? {
        val body = JSONObject().put("email", userEmail).put("password", userPassword).put("returnSecureToken", true)
        return try { requestJson("$AUTH_BASE/$endpoint?key=$API_KEY", "POST", body, null) } catch (_: Exception) { null }
    }

    private fun validIdToken(): String {
        val current = prefs.getString("id_token", null)
        if (!current.isNullOrBlank()) return current
        val refresh = prefs.getString("refresh_token", null) ?: throw IllegalStateException("Log masuk dahulu.")
        val body = "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(refresh, "UTF-8")
        val response = requestText("https://securetoken.googleapis.com/v1/token?key=$API_KEY", "POST", body, "application/x-www-form-urlencoded", null)
        val json = JSONObject(response)
        val token = json.getString("id_token")
        prefs.edit().putString("id_token", token).putString("refresh_token", json.getString("refresh_token")).apply()
        return token
    }

    private fun syncCloud() {
        note.text = "Sedang mendapatkan data cloud…"
        executor.execute {
            try {
                val token = validIdToken()
                val uid = prefs.getString("uid", null) ?: throw IllegalStateException("UID akaun tiada. Log masuk semula.")
                val url = "$FIRESTORE_BASE/projects/$PROJECT_ID/databases/(default)/documents/users/$uid/private/finance"
                val response = requestText(url, "GET", null, null, token)
                val doc = JSONObject(response)
                val fields = doc.optJSONObject("fields")
                val raw = fields?.optJSONObject("payloadJson")?.optString("stringValue")
                if (!raw.isNullOrBlank()) {
                    val payload = JSONObject(raw)
                    val cash = payload.optDouble("cash", 0.0)
                    val goldGrams = payload.optDouble("goldGrams", 0.0)
                    prefs.edit().putString("savings", cash.toString())
                        .putString("grams", goldGrams.toString()).apply()
                    runOnUiThread {
                        savings.setText(cash.toString()); grams.setText(goldGrams.toString())
                        refreshWidget(); note.text = "Cloud sync berjaya. Widget dikemas kini."
                    }
                } else {
                    uploadCurrentValues(url, token)
                }
            } catch (e: Exception) {
                val message = e.message ?: "Ralat tidak diketahui"
                if (message.contains("HTTP 404")) {
                    try {
                        val token = validIdToken()
                        val uid = prefs.getString("uid", null) ?: throw IllegalStateException("UID tiada")
                        val url = "$FIRESTORE_BASE/projects/$PROJECT_ID/databases/(default)/documents/users/$uid/private/finance"
                        uploadCurrentValues(url, token)
                    } catch (inner: Exception) { runOnUiThread { note.text = "Sync gagal: ${inner.message}" } }
                } else runOnUiThread { note.text = "Sync gagal: $message" }
            }
        }
    }

    private fun uploadCurrentValues(url: String, token: String) {
        val cash = savings.text.toString().toDoubleOrNull() ?: prefs.getString("savings", "0")?.toDoubleOrNull() ?: 0.0
        val gold = grams.text.toString().toDoubleOrNull() ?: prefs.getString("grams", "0")?.toDoubleOrNull() ?: 0.0
        // Preserve the full dashboard payload; only update the two widget-owned values.
        val payload = try {
            val existing = JSONObject(requestText(url, "GET", null, null, token))
            val raw = existing.optJSONObject("fields")?.optJSONObject("payloadJson")?.optString("stringValue")
            if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
        } catch (_: Exception) { JSONObject() }
        payload.put("cash", cash).put("goldGrams", gold)
        val fields = JSONObject().put("payloadJson", JSONObject().put("stringValue", payload.toString()))
        requestJson(url, "PATCH", JSONObject().put("fields", fields), token)
        prefs.edit().putString("savings", cash.toString()).putString("grams", gold.toString()).apply()
        runOnUiThread {
            savings.setText(cash.toString()); grams.setText(gold.toString())
            refreshWidget(); note.text = "Tabungan dan emas widget disegerakkan tanpa memadam data dashboard lain."
        }
    }

    private fun requestJson(url: String, method: String, body: JSONObject, token: String?): JSONObject {
        return JSONObject(requestText(url, method, body.toString(), "application/json", token))
    }

    private fun requestJson(url: String, method: String, body: JSONObject, token: String?, unused: Boolean = false): JSONObject =
        requestJson(url, method, body, token)

    private fun requestText(url: String, method: String, body: String?, contentType: String?, token: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15000; readTimeout = 15000
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $text")
        return text
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
            if (!backup.has("cash") || !backup.has("goldGrams")) throw IllegalArgumentException("Fail bukan backup ADAM Finance")
            val cash = backup.optDouble("cash", 0.0); val goldGrams = backup.optDouble("goldGrams", 0.0)
            if (cash < 0 || goldGrams < 0) throw IllegalArgumentException("Nilai tidak sah")
            savings.setText(cash.toString()); grams.setText(goldGrams.toString())
            prefs.edit().putString("savings", cash.toString()).putString("grams", goldGrams.toString()).apply()
            refreshWidget(); note.text = "Backup berjaya diimport."
        } catch (e: Exception) { note.text = "Import gagal: " + (e.message ?: "Fail tidak sah") }
    }

    private fun refreshWidget() {
        FinanceWidgetProvider.updateAll(this)
        GoldRefreshWorker.enqueue(this)
    }
}
