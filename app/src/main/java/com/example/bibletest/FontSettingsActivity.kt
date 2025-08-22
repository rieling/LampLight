package com.example.bibletest

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.io.InputStreamReader

class FontSettingsActivity : AppCompatActivity() {

    private lateinit var previewText: TextView
    private lateinit var fontSizeValue: TextView
    private lateinit var paragraphSwitch: Switch
    private lateinit var fontSelector: TextView
    private lateinit var themeSelectorButton: Button // NEW

    private var fontSize: Float = 18f
    private var paragraphMode: Boolean = true
    private var fontName: String = "serif"

    private lateinit var kjvData: BibleData
    private val emptyBibleData = BibleData(Metadata("Empty Bible", "EMPTY"), listOf())

    private val prebuiltThemes = listOf(
        AppTheme("Light", 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFFEEEEEE.toInt(), 0xFFDDDDDD.toInt()),
        AppTheme("Dark", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF5555.toInt(), 0xFF222222.toInt(), 0xFF333333.toInt())
    )

    private lateinit var allThemes: MutableList<AppTheme>
    private lateinit var selectedTheme: AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_font_settings)

        previewText = findViewById(R.id.font_preview_text)
        fontSizeValue = findViewById(R.id.font_size_value)
        paragraphSwitch = findViewById(R.id.paragraph_mode_switch)
        fontSelector = findViewById(R.id.font_selector)
        themeSelectorButton = findViewById(R.id.btn_theme_selector)

        // Load saved settings
        fontSize = SettingsManager.getFontSize(this)
        paragraphMode = SettingsManager.getParagraphMode(this)
        fontName = SettingsManager.getFontName(this)

        fontSizeValue.text = fontSize.toInt().toString()
        paragraphSwitch.isChecked = paragraphMode
        fontSelector.text = "Font: ${fontName.capitalize()}"


        // Load Bible data
        kjvData = try {
            val stream = assets.open("kjv.json")
            Gson().fromJson(InputStreamReader(stream), BibleData::class.java)
        } catch (e: Exception) {
            Log.e("FontSettings", "Failed to load kjv.json", e)
            emptyBibleData
        }

        // Combine prebuilt + custom
        allThemes = (prebuiltThemes + SettingsManager.getCustomThemes(this)).toMutableList()
        val savedThemeName = SettingsManager.getCurrentTheme(this)
        selectedTheme = allThemes.firstOrNull { it.name == savedThemeName } ?: prebuiltThemes.first()

        applyPreview()
        setupButtons()

        // Buttons for font size
        findViewById<Button>(R.id.font_size_increase).setOnClickListener {
            fontSize = (fontSize + 1f).coerceAtMost(40f)
            fontSizeValue.text = fontSize.toInt().toString()
            applyPreview()
        }
        findViewById<Button>(R.id.font_size_decrease).setOnClickListener {
            fontSize = (fontSize - 1f).coerceAtLeast(8f)
            fontSizeValue.text = fontSize.toInt().toString()
            applyPreview()
        }

        // Paragraph mode toggle
        paragraphSwitch.setOnCheckedChangeListener { _, isChecked ->
            paragraphMode = isChecked
            applyPreview()
        }

        // Font selector
        fontSelector.setOnClickListener { showFontDrawer() }

        applyPreview()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.font_size_increase).setOnClickListener {
            fontSize = (fontSize + 1f).coerceAtMost(40f)
            fontSizeValue.text = fontSize.toInt().toString()
            applyPreview()
        }
        findViewById<Button>(R.id.font_size_decrease).setOnClickListener {
            fontSize = (fontSize - 1f).coerceAtLeast(8f)
            fontSizeValue.text = fontSize.toInt().toString()
            applyPreview()
        }

        paragraphSwitch.setOnCheckedChangeListener { _, isChecked ->
            paragraphMode = isChecked
            applyPreview()
        }

        fontSelector.setOnClickListener { showFontDrawer() }

        themeSelectorButton.setOnClickListener { showThemeSelector() }

        findViewById<Button>(R.id.apply_button).setOnClickListener {
            SettingsManager.setFontSize(this, fontSize)
            SettingsManager.setParagraphMode(this, paragraphMode)
            SettingsManager.setFontName(this, fontName)
            SettingsManager.setCurrentTheme(this, selectedTheme.name)
            SettingsManager.saveCustomThemes(this, allThemes.filter { !prebuiltThemes.contains(it) })

            val intent = Intent()
            intent.putExtra("refresh", true)
            setResult(Activity.RESULT_OK, intent)

            finish()
        }
    }

    private fun applyPreview() {
        // Set typeface
        previewText.typeface = when (fontName) {
            "sans-serif" -> Typeface.SANS_SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SERIF
        }

        // Set font size
        previewText.textSize = fontSize

        // Background + text color from current theme
        previewText.setBackgroundColor(selectedTheme.backgroundColor)
        previewText.setTextColor(selectedTheme.textColor)

        // Paragraph justification (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            previewText.justificationMode =
                if (paragraphMode) LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                else LineBreaker.JUSTIFICATION_MODE_NONE
        }

        // --- Use VerseRenderer for John 1 ---
        val john1Verses = kjvData.verses.filter { it.bookName == "John" && it.chapter == 1 }
        val renderer = VerseRenderer(
            context = this,
            verses = john1Verses,
            isParagraphMode = paragraphMode,
            highlightedVerses = mutableSetOf(), // no highlights in preview
            redLetterColor = ContextCompat.getColor(this, R.color.red_letter_color),
            tempHighlightVerse = null,
        )
        previewText.text = renderer.buildChapterSpannable()
        previewText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun applyThemePreview() {
        previewText.setTextColor(selectedTheme.textColor)
        previewText.setBackgroundColor(selectedTheme.backgroundColor)
    }

    private fun showFontDrawer() {
        val fonts = listOf("serif", "sans-serif", "monospace")
        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, fonts)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(fonts.indexOf(fontName), true)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Select Font")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            fontName = fonts[position]
            fontSelector.text = "Font: ${fontName.capitalize()}"
            applyPreview()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showThemeSelector() {
        val themeNames = allThemes.map { it.name }
        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, themeNames)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(themeNames.indexOf(selectedTheme.name), true)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Select Theme")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add Custom") { _, _ ->
                // Example: Add a custom theme (user-defined color pickers can be added)
                val newTheme = AppTheme(
                    "Custom ${allThemes.size + 1}",
                    0xFFCCCCCC.toInt(),
                    0xFF111111.toInt(),
                    0xFFFF0000.toInt(),
                    0xFFAAAAAA.toInt(),
                    0xFF888888.toInt()
                )
                allThemes.add(newTheme)
                showThemeSelector() // reopen dialog with new theme
            }
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedTheme = allThemes[position]
            applyThemePreview()
            dialog.dismiss()
        }

        dialog.show()
    }
}
