package com.umbrella.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private lateinit var heardView: TextView
    private lateinit var statusView: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var always = false
    private var listening = false
    private var ttsReady = false
    private var wake = "umbrella"
    private var cc = "91"
    private var lang = "en-IN"
    private var speak = true
    private val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) { ttsReady = true; applyPrefs() } }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F0F26"))
            setPadding(48, 48, 48, 48)
        }
        fun label(size: Float, color: String) = TextView(this).apply {
            textSize = size; setTextColor(Color.parseColor(color)); gravity = Gravity.CENTER
        }
        root.addView(label(44f, "#ECE9FF").apply { text = "Umbrella" })
        statusView = label(16f, "#9A97C4").apply { text = "Ready" }
        heardView = label(22f, "#ECE9FF")
        val talk = Button(this).apply { text = "Tap to talk"; setOnClickListener { listen() } }
        val sw = Switch(this).apply {
            text = "Always listening (use your wake word)"
            setTextColor(Color.parseColor("#9A97C4"))
            setOnCheckedChangeListener { _, on -> always = on; if (on) listen() }
        }
        val cfg = Button(this).apply { text = "Settings"; setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) } }
        root.addView(statusView); root.addView(heardView); root.addView(talk); root.addView(sw); root.addView(cfg)
        setContentView(root)
        requestPermissions(perms, 1)
    }

    override fun onResume() { super.onResume(); applyPrefs() }

    private fun applyPrefs() {
        val p = getSharedPreferences("umbrella", MODE_PRIVATE)
        wake = (p.getString("wake", "umbrella") ?: "umbrella").lowercase(Locale.ROOT).trim().ifEmpty { "umbrella" }
        cc = p.getString("cc", "91") ?: "91"
        lang = p.getString("lang", "en-IN") ?: "en-IN"
        speak = p.getBoolean("speak", true)
        if (ttsReady) {
            tts.language = Locale.forLanguageTag(lang)
            tts.setSpeechRate(p.getFloat("rate", 1f))
            tts.setPitch(p.getFloat("pitch", 1f))
        }
    }

    private fun say(t: String) {
        statusView.text = t
        if (speak) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u")
    }

    private fun listen() {
        if (listening) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("Allow the microphone, then tap again."); requestPermissions(perms, 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("Speech recognition isn’t available on this phone."); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(res: Bundle?) {
                    listening = false
                    val text = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text != null) handle(text)
                    again()
                }
                override fun onError(e: Int) {
                    listening = false
                    if (!always) statusView.text = "Didn’t catch that. Tap and try again."
                    again()
                }
                override fun onReadyForSpeech(p: Bundle?) { statusView.text = "Listening…" }
                override fun onPartialResults(p: Bundle?) {
                    p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heardView.text = it }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            )
        }
        listening = true
    }

    private fun again() { if (always) ui.postDelayed({ listen() }, 2500) }

    private fun number(s: String): String? {
        val d = Regex("\\+?\\d[\\d\\s-]{5,}\\d").find(s)?.value?.replace(Regex("[\\s-]"), "") ?: return null
        return d
    }

    private fun handle(raw: String) {
        heardView.text = raw
        var t = raw.lowercase(Locale.ROOT).trim()
        if (always && !t.contains(wake)) return
        t = t.replace(Regex("^(hey |ok )?" + Regex.escape(wake) + "[,.! ]*"), "").trimEnd('.', '!', '?')
        if (t.isEmpty()) { say("I’m listening."); return }

        Regex("^whatsapp\\s+(.+?)\\s+to\\s+(.+)$").find(t)?.let { m ->
            var n = number(m.groupValues[2]) ?: run { say("I need a phone number."); return }
            n = n.replace("+", "")
            if (n.length == 10) n = "$cc$n"
            val q = URLEncoder.encode(m.groupValues[1], "UTF-8")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n?text=$q")))
            say("Opening WhatsApp. Tap send there."); return
        }
        Regex("^(?:send|text|message|sms)\\s+(.+?)\\s+to\\s+(.+)$").find(t)?.let { m ->
            val n = number(m.groupValues[2]) ?: run { say("I need a phone number."); return }
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                say("Allow SMS permission first."); requestPermissions(perms, 1); return
            }
            @Suppress("DEPRECATION") val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(n, null, sm.divideMessage(m.groupValues[1]), null, null)
            say("Sent “${m.groupValues[1]}” to $n."); return
        }
        Regex("^(?:call|dial|phone)\\s+(.+)$").find(t)?.let { m ->
            val n = number(m.groupValues[1]) ?: run { say("I need a phone number."); return }
            val ok = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            startActivity(Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$n")))
            say("Calling $n."); return
        }
        Regex("^(?:google|search(?: google)?(?: for)?|look up)\\s+(?:about\\s+)?(.+)$").find(t)?.let { m ->
            val q = URLEncoder.encode(m.groupValues[1], "UTF-8")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")))
            say("Searching Google for ${m.groupValues[1]}."); return
        }
        Regex("^open\\s+(.+)$").find(t)?.let { m ->
            val name = m.groupValues[1].replace(Regex("^the\\s+|\\s+app$"), "")
            val pm = packageManager
            val app = pm.getInstalledApplications(0).firstOrNull {
                pm.getLaunchIntentForPackage(it.packageName) != null &&
                    pm.getApplicationLabel(it).toString().lowercase(Locale.ROOT).contains(name)
            }
            if (app == null) { say("I couldn’t find an app called $name."); return }
            startActivity(pm.getLaunchIntentForPackage(app.packageName))
            say("Opening $name."); return
        }
        if (Regex("\\bblock\\b").containsMatchIn(t) || Regex("\\breel\\b").containsMatchIn(t)) {
            say("I can’t do that one yet. It needs screen control, which is the next upgrade."); return
        }
        if (Regex("\\btime\\b").containsMatchIn(t)) { say("It’s " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."); return }
        say("I didn’t understand. Try: send hi to a number, call a number, google something, or open an app.")
    }

    override fun onDestroy() { recognizer?.destroy(); tts.shutdown(); super.onDestroy() }
}
