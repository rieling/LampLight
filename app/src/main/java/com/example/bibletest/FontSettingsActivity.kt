package com.example.bibletest

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import java.io.InputStreamReader

class FontSettingsActivity : AppCompatActivity() {

    private lateinit var previewText: TextView
    private lateinit var fontSizeValue: TextView
    private lateinit var paragraphSwitch: Switch
    private lateinit var fontSelector: TextView
    private lateinit var themeSelectorRecycler: RecyclerView // NEW

    private var fontSize: Float = 18f
    private var paragraphMode: Boolean = true
    private var fontName: String = "serif"

    private lateinit var kjvData: BibleData
    private val emptyBibleData = BibleData(Metadata("Empty Bible", "EMPTY"), listOf())

    private val prebuiltThemes = listOf(
        AppTheme("Light", 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(), 0x88888888.toInt()),
        AppTheme("Dark", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF5555.toInt(), 0x88888888.toInt())
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
        themeSelectorRecycler = findViewById(R.id.theme_recycler_view)

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

        themeSelectorRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val themeAdapter = ThemeAdapter(
            themes = allThemes,
            onThemeSelected = { theme ->
                selectedTheme = theme
                applyThemePreview()
            },
            onAddTheme = {
                showAddThemeDialog()
            },
            onDeleteTheme = { theme ->
                showDeleteThemeDialog(theme)
            }
        )
        themeSelectorRecycler.adapter = themeAdapter

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

    private fun showAddThemeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_theme, null)

        val nameInput = dialogView.findViewById<EditText>(R.id.theme_name)
        val bgInput = dialogView.findViewById<EditText>(R.id.background_color)
        val textInput = dialogView.findViewById<EditText>(R.id.text_color)
        val redInput = dialogView.findViewById<EditText>(R.id.red_letter_color)
        val borderInput = dialogView.findViewById<EditText>(R.id.border_color)

        android.app.AlertDialog.Builder(this)
            .setTitle("Create Theme")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val newTheme = AppTheme(
                        name = nameInput.text.toString().ifBlank { "Custom ${allThemes.size + 1}" },
                        backgroundColor = Color.parseColor(bgInput.text.toString().ifBlank { "#000000" }),
                        textColor = Color.parseColor(textInput.text.toString().ifBlank { "#FFFFFF" }),
                        redLetterColor = Color.parseColor(redInput.text.toString().ifBlank { "#FF5555" }),
                        borderColor = Color.parseColor(borderInput.text.toString().ifBlank { "#888888" })
                    )
                    allThemes.add(newTheme)
                    SettingsManager.saveCustomThemes(
                        this,
                        allThemes.filter { !prebuiltThemes.contains(it) }
                    )
                    themeSelectorRecycler.adapter?.notifyDataSetChanged()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this, "Invalid color format. Use #RRGGBB.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteThemeDialog(theme: AppTheme) {
        AlertDialog.Builder(this)
            .setTitle("Delete Theme")
            .setMessage("Are you sure you want to delete \"${theme.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                allThemes.remove(theme)
                SettingsManager.saveCustomThemes(
                    this,
                    allThemes.filter { !prebuiltThemes.contains(it) }
                )
                themeSelectorRecycler.adapter?.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        findViewById<LinearLayout>(R.id.settings_container).setBackgroundColor(selectedTheme.backgroundColor)

        findViewById<TextView>(R.id.font_selector).setTextColor(selectedTheme.textColor)

        findViewById<TextView>(R.id.paragraph_mode_label).setTextColor(selectedTheme.textColor)

        findViewById<TextView>(R.id.font_size_value).setTextColor(selectedTheme.textColor)
        findViewById<Button>(R.id.font_size_decrease).setTextColor(selectedTheme.textColor)
        findViewById<Button>(R.id.font_size_increase).setTextColor(selectedTheme.textColor)

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

        val buttons = listOf(
            findViewById<Button>(R.id.font_size_increase),
            findViewById<Button>(R.id.font_size_decrease),
            findViewById<Button>(R.id.apply_button)
        )

        val borderDrawable = ContextCompat.getDrawable(this, R.drawable.box_border)?.mutate()

        buttons.forEach { btn ->
            btn.background = borderDrawable?.constantState?.newDrawable()?.mutate()
            btn.backgroundTintList = null // <-- THIS removes Material tint
            btn.setTextColor(selectedTheme.textColor) // Keep your dynamic text color
        }

    }

    private fun applyThemePreview() {
        previewText.setTextColor(selectedTheme.textColor)
        previewText.setBackgroundColor(selectedTheme.backgroundColor)

        findViewById<LinearLayout>(R.id.settings_container).setBackgroundColor(selectedTheme.backgroundColor)

        findViewById<TextView>(R.id.font_selector).setTextColor(selectedTheme.textColor)

        findViewById<TextView>(R.id.paragraph_mode_label).setTextColor(selectedTheme.textColor)

        findViewById<TextView>(R.id.font_size_value).setTextColor(selectedTheme.textColor)
        findViewById<Button>(R.id.font_size_decrease).setTextColor(selectedTheme.textColor)
        findViewById<Button>(R.id.font_size_increase).setTextColor(selectedTheme.textColor)

        findViewById<Button>(R.id.apply_button).setTextColor(selectedTheme.textColor)

        val buttons = listOf(
            findViewById<Button>(R.id.font_size_increase),
            findViewById<Button>(R.id.font_size_decrease),
            findViewById<Button>(R.id.apply_button)
        )

        val borderDrawable = ContextCompat.getDrawable(this, R.drawable.box_border)?.mutate()

        buttons.forEach { btn ->
            btn.background = borderDrawable?.constantState?.newDrawable()?.mutate()
            btn.backgroundTintList = null // <-- THIS removes Material tint
            btn.setTextColor(selectedTheme.textColor) // Keep your dynamic text color
        }

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
}
