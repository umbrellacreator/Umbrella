package com.umbrella.app

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private val langs = listOf("en-IN" to "English (India)", "en-US" to "English (US)", "hi-IN" to "Hindi")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("umbrella", MODE_PRIVATE)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        fun title(s: String) = TextView(this).apply { text = s; textSize = 16f; setPadding(0, 32, 0, 8); col.addView(this) }

        title("Wake word")
        val wake = EditText(this).apply { setText(p.getString("wake", "umbrella")); setSingleLine(); col.addView(this) }
        title("Country code for WhatsApp numbers (digits only)")
        val cc = EditText(this).apply { setText(p.getString("cc", "91")); setSingleLine(); col.addView(this) }
        title("Voice language")
        val lang = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, langs.map { it.second })
            setSelection(langs.indexOfFirst { it.first == p.getString("lang", "en-IN") }.coerceAtLeast(0))
            col.addView(this)
        }
        title("Voice speed")
        val rate = SeekBar(this).apply { max = 150; progress = (p.getFloat("rate", 1f) * 100).toInt() - 50; col.addView(this) }
        title("Voice pitch")
        val pitch = SeekBar(this).apply { max = 150; progress = (p.getFloat("pitch", 1f) * 100).toInt() - 50; col.addView(this) }
        val speak = Switch(this).apply { text = "Speak replies aloud"; isChecked = p.getBoolean("speak", true); setPadding(0, 32, 0, 0); col.addView(this) }

        Button(this).apply {
            text = "Save"
            setOnClickListener {
                p.edit()
                    .putString("wake", wake.text.toString().trim().ifEmpty { "umbrella" })
                    .putString("cc", cc.text.toString().filter { it.isDigit() }.ifEmpty { "91" })
                    .putString("lang", langs[lang.selectedItemPosition].first)
                    .putFloat("rate", (rate.progress + 50) / 100f)
                    .putFloat("pitch", (pitch.progress + 50) / 100f)
                    .putBoolean("speak", speak.isChecked)
                    .apply()
                finish()
            }
            col.addView(this)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
