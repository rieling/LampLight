package com.example.bibletest

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import java.io.InputStreamReader

class FontSettingsActivity : AppCompatActivity() {

    private var currentFontSize = 18f
    private var currentFontType = "serif"
    private var isParagraphMode = true

    private lateinit var prefs: SharedPreferences
    private lateinit var previewText: TextView
    private lateinit var fontValue: TextView
    private lateinit var paragraphSwitch: Switch
    private lateinit var fontSelector: TextView
    private lateinit var themeRecyclerView: RecyclerView
    private lateinit var kjvData: BibleData

    val emptyBibleData = BibleData(
        metadata = Metadata(name = "Empty Bible", shortname = "EMPTY"),
        verses = listOf()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_settings)

        previewText = findViewById(R.id.font_preview_text)
        fontValue = findViewById(R.id.font_size_value)
        paragraphSwitch = findViewById(R.id.paragraph_mode_switch)
        fontSelector = findViewById(R.id.font_selector)
        themeRecyclerView = findViewById(R.id.theme_recycler_view)

        kjvData = try {
            val kjvJsonStream = assets.open("kjv.json")
            Gson().fromJson(InputStreamReader(kjvJsonStream), BibleData::class.java)
        } catch (e: Exception) {
            Log.e("FontSettings", "Failed to load kjv.json", e)
            emptyBibleData
        }
        prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)

        // Load saved settings
        currentFontSize = prefs.getFloat("font_size", 18f)
        currentFontType = prefs.getString("font_type", "serif") ?: "serif"
        isParagraphMode = prefs.getBoolean("paragraph_mode", true)

        // Preview setup
        previewText.textSize = currentFontSize
        previewText.typeface = typefaceFromName(currentFontType)
        fontValue.text = currentFontSize.toInt().toString()
        paragraphSwitch.isChecked = isParagraphMode

        // Font increase/decrease
        findViewById<Button>(R.id.font_size_increase).setOnClickListener {
            currentFontSize += 1
            updatePreview()
        }
        findViewById<Button>(R.id.font_size_decrease).setOnClickListener {
            if (currentFontSize > 8) {
                currentFontSize -= 1
                updatePreview()
            }
        }

        // Paragraph toggle
        paragraphSwitch.setOnCheckedChangeListener { _, checked ->
            isParagraphMode = checked
            updatePreview()
        }

        // Font selector click
        fontSelector.setOnClickListener {
            showFontDrawer()
        }

        // Setup theme RecyclerView
        themeRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        themeRecyclerView.adapter = ThemeAdapter(getDefaultThemes()) { selectedTheme ->
            // Update preview background/text color immediately
            val initialTheme = getDefaultThemes()[0]
            previewText.setBackgroundColor(android.graphics.Color.parseColor(selectedTheme.backgroundColor))
            previewText.setTextColor(android.graphics.Color.parseColor(selectedTheme.textColor))
        }

        // Apply saved settings when exiting
        findViewById<Button>(R.id.apply_button)?.setOnClickListener {
            prefs.edit {
                putFloat("font_size", currentFontSize)
                putString("font_type", currentFontType)
                putBoolean("paragraph_mode", isParagraphMode)
            }
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()

        }
        updatePreview()
    }

    private fun updatePreview() {
        fontValue.text = currentFontSize.toInt().toString()

        val allVerses = kjvData.verses
        if (allVerses.isNullOrEmpty()) {
            previewText.text = "Sample text preview"
            previewText.textSize = currentFontSize
            previewText.typeface = typefaceFromName(currentFontType)
            return
        }

        val firstVerse = allVerses.first()
        val chapterVerses = allVerses.filter { it.bookName == firstVerse.bookName && it.chapter == firstVerse.chapter }

        if (chapterVerses.isEmpty()) {
            previewText.text = "Sample text preview"
            previewText.textSize = currentFontSize
            previewText.typeface = typefaceFromName(currentFontType)
            return
        }

        previewText.textSize = currentFontSize
        previewText.typeface = typefaceFromName(currentFontType)

        // Safe: only use VerseRenderer if there is data
        try {
            val renderer = VerseRenderer(
                context = this,
                verses = chapterVerses,
                isParagraphMode = isParagraphMode,
                highlightedVerses = mutableSetOf(),
                redLetterColor = ContextCompat.getColor(this, R.color.red_letter_color),
                tempHighlightVerse = null
            )
            previewText.text = renderer.buildChapterSpannable()
            previewText.movementMethod = LinkMovementMethod.getInstance()
        } catch (e: Exception) {
            Log.e("FontSettings", "VerseRenderer crash", e)
            previewText.text = chapterVerses.joinToString("\n") { "${it.bookName} ${it.chapter}:${it.verse} ${it.text}" }
        }
    }

    private fun typefaceFromName(name: String): Typeface {
        return when (name) {
            "sans-serif" -> Typeface.SANS_SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SERIF
        }
    }

    private fun showFontDrawer() {
        val fonts = listOf("serif", "sans-serif", "monospace")

        // Create simple ListView for fonts
        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, fonts)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(fonts.indexOf(currentFontType), true)

        // Dialog container
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Select Font")
            .setView(listView)
            .setPositiveButton("Add Font") { _, _ ->
                // TODO: implement Add Font functionality later
                Toast.makeText(this, "Add Font clicked", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Handle selection
        listView.setOnItemClickListener { _, _, position, _ ->
            currentFontType = fonts[position]
            updatePreview()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getDefaultThemes(): List<Theme> {
        // Define your basic themes
        return listOf(
            Theme("#FFFFFF", "#000000"), // White bg, black text
            Theme("#000000", "#FFFFFF"), // Black bg, white text
            Theme("#0000FF", "#FFFFFF")  // Blue bg, white text
        )
    }

    data class Theme(val backgroundColor: String, val textColor: String)
}
