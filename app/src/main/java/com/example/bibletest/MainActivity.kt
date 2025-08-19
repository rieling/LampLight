package com.example.bibletest

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Highlights
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bibletest.databinding.ActivityMainBinding
import java.io.InputStreamReader
import com.google.gson.Gson
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import android.view.ViewTreeObserver

class MainActivity : AppCompatActivity() {

    var selectedBook: String? = null
    var selectedChapter: Int? = null
    var selectedVerse: Int? = null

    var isParagraphMode = true // Default to verse-by-verse mode

    val highlightedVerses = mutableSetOf<Triple<String, Int, Int>>()

    private var ignoreNextScroll = false // Used to avoid looped triggering of scroll listeners

    // View binding object gives access to all views in activity_main.xml and included layouts
    private lateinit var binding: ActivityMainBinding

    // Drawer layout and RecyclerView for your custom drawer (books list)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerRecyclerView: RecyclerView     // Recycler list of books

    // This holds your parsed KJV Bible data
    private lateinit var kjvData: BibleData

    // Saves the user's current book/chapter/verse into local storage (SharedPreferences).
    private fun saveLastRead(book: String, chapter: Int, verse: Int?) {
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        prefs.edit {
            putString("last_book", book)
            putInt("last_chapter", chapter)
            if (verse != null) {
                putInt("last_verse", verse)
            } else {
                remove("last_verse")
            }
        }
    }

    data class ParsedVerseText(
        val text: String,
        val redRanges: List<IntRange>,
        val supleRanges: List<IntRange>,
        val endsParagraph: Boolean
    )

    fun parseVerseText(raw: String): ParsedVerseText {
        val builder = StringBuilder()
        val redRanges = mutableListOf<IntRange>()
        val supleRanges = mutableListOf<IntRange>()


        var inRed = false
        var redStart = 0
        var inBracket = false
        var bracketStart = 0

        var endsParagraph = raw.contains('\u00B6')

        var i = 0
        while (i < raw.length) {
            when (raw[i]) {
                '\u2039' -> { // ‹
                    inRed = true
                    redStart = builder.length
                    i++
                }

                '\u203a' -> { // ›
                    if (inRed) {
                        redRanges.add(redStart until builder.length)
                        inRed = false
                    }
                    i++

                    //skip any spaces while adding them back after you've checked
                    var hadSpace = false
                    while (i < raw.length && raw[i].isWhitespace()) {
                        hadSpace = true
                        i++
                    }
                    if (hadSpace) builder.append(" ")

                    // If next non-space char is [, treat bracketed text as red
                    if (i < raw.length && raw[i] == '[') {
                        val bracketStart = builder.length
                        i++ // skip [

                        while (i < raw.length && raw[i] != ']') {
                            builder.append(raw[i])
                            i++
                        }
                        val bracketEnd = builder.length
                        supleRanges.add(bracketStart until bracketEnd)
                        redRanges.add(bracketStart until bracketEnd)

                        if (i < raw.length && raw[i] == ']') i++ // skip ]
                    }
                }

                '[' -> {
                    val bracketStart = builder.length
                    i++ // skip [

                    while (i < raw.length && raw[i] != ']') {
                        builder.append(raw[i])
                        i++
                    }

                    val bracketEnd = builder.length
                    supleRanges.add(bracketStart until bracketEnd)

                    // After ']', check for ‹ skipping spaces, but preserve space
                    var lookAhead = i
                    var hasSpace = false
                    while (lookAhead < raw.length && raw[lookAhead].isWhitespace()) {
                        hasSpace = true
                        lookAhead++
                    }
                    if (hasSpace) builder.append(" ")
                    if (lookAhead < raw.length && raw[lookAhead] == '\u2039') {
                        redRanges.add(bracketStart until bracketEnd)
                    }

                    if (i < raw.length && raw[i] == ']') i++ // skip ]
                }

                '\u00B6' -> {
                    // Ignore ¶
                    i++
                }

                else -> {
                    builder.append(raw[i])
                    i++
                }
            }
        }

        return ParsedVerseText(
            text = builder.toString(),
            redRanges = redRanges,
            supleRanges = supleRanges,
            endsParagraph = endsParagraph
        )
    }

    // builds and styles the verse text based on chapter/verse.
    fun displayChapter(
        bookName: String,
        chapter: Int,
        scrollToVerse: Int?,
        tempHighlight: Boolean = true
    ) {
        selectedBook = bookName
        selectedChapter = chapter
        selectedVerse = scrollToVerse

        // Colors from colors.xml
        val verseTextColor = ContextCompat.getColor(this, R.color.verse_text_color)
        val verseBackgroundColor = ContextCompat.getColor(this, R.color.verse_background_color)
        val redLetterColor = ContextCompat.getColor(this, R.color.red_letter_color)

        // Chapter title
        val chapterTitle = findViewById<TextView>(R.id.chapter_title)
        chapterTitle.text = getString(R.string.chapter_display, bookName, chapter)

        // Verses in chapter
        val versesInChapter = kjvData.verses.filter { it.bookName == bookName && it.chapter == chapter }
        val builder = SpannableStringBuilder()

        // Header
        val bookTitle = "$bookName\n"
        val chapterNumber = "$chapter\n"

        // Book name style
        val bookStart = builder.length
        builder.append(bookTitle)
        val bookEnd = builder.length
        builder.setSpan(android.text.style.RelativeSizeSpan(1.2f), bookStart, bookEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.StyleSpan(Typeface.NORMAL), bookStart, bookEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), bookStart, bookEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Chapter number style
        val chapterStart = builder.length
        builder.append(chapterNumber)
        val chapterEnd = builder.length
        builder.setSpan(android.text.style.RelativeSizeSpan(6.0f), chapterStart, chapterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.StyleSpan(Typeface.BOLD), chapterStart, chapterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), chapterStart, chapterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Track selected verse offsets
        var selectedVerseStart = -1
        var selectedVerseEnd = -1

        for (v in versesInChapter) {
            val parsed = parseVerseText(v.text)
            val text = parsed.text
            val redRanges = parsed.redRanges
            val supleRanges = parsed.supleRanges
            val endsParagraph = parsed.endsParagraph

            val start = builder.length

            if (!isParagraphMode) {
                // Verse-by-verse with [x]
                builder.append("[${v.verse}] ")
            } else {
                // Paragraph mode: superscript verse number
                val verseStr = v.verse.toString()
                builder.append(verseStr)
                builder.setSpan(
                    android.text.style.RelativeSizeSpan(0.6f),
                    builder.length - verseStr.length,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    android.text.style.SuperscriptSpan(),
                    builder.length - verseStr.length,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(" ")
            }

            // Verse text
            val verseStart = builder.length
            builder.append(text)
            val verseEnd = builder.length

            // Remember selected verse offsets
            if (v.verse == selectedVerse) {
                selectedVerseStart = verseStart
                selectedVerseEnd = verseEnd
            }

            // Red letters
            for (range in redRanges) {
                builder.setSpan(
                    android.text.style.ForegroundColorSpan(redLetterColor),
                    verseStart + range.first,
                    verseStart + range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Superscript/italic ranges
            for (range in supleRanges) {
                val spanStart = verseStart + range.first
                val spanEnd = verseStart + range.last + 1
                if (spanStart in 0 until builder.length && spanEnd in 0..builder.length && spanStart < spanEnd) {
                    builder.setSpan(
                        android.text.style.StyleSpan(Typeface.ITALIC),
                        spanStart,
                        spanEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Clickable span
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    toggleVerseHighlight(v.verse)
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.isUnderlineText = false
                }
            }
            builder.setSpan(clickableSpan, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Apply underline if highlighted or tempHighlight
            if (highlightedVerses.contains(Triple(bookName, chapter, v.verse)) || (tempHighlight && v.verse == selectedVerse)) {
                builder.setSpan(
                    UnderlineSpan(),
                    start, // use 'start' instead of 'verseStart'
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            builder.append(if (endsParagraph || !isParagraphMode) "\n\n" else " ")
        }

        // Set text
        val textView = findViewById<TextView>(R.id.verse_text_view)
        val scrollView = findViewById<ScrollView>(R.id.verse_scroll_view)

        textView.setTextColor(verseTextColor)
        textView.setBackgroundColor(verseBackgroundColor)
        textView.text = builder
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
        textView.isClickable = true
        textView.isFocusable = true

        // Scroll to selected verse
        if (selectedVerseStart >= 0) {
            ignoreNextScroll = true
            scrollView.post {
                val layout = textView.layout
                if (layout != null) {
                    val line = layout.getLineForOffset(selectedVerseStart)
                    val y = layout.getLineTop(line)
                    scrollView.smoothScrollTo(0, y)
                }
            }
        }

        // Save last read
        saveLastRead(bookName, chapter, scrollToVerse)
    }

    //lose the drawer and collapse the lists inside of it
    fun closeDrawer() {
        drawerLayout.closeDrawers()
        // Delay collapse until drawer is actually closed
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerClosed(drawerView: View) {
                val adapter = drawerRecyclerView.adapter as? BookAdapter
                adapter?.collapseAll()
                drawerLayout.removeDrawerListener(this) // Clean up listener
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })
    }
    // temporarily underline verse that was selected
    fun toggleVerseHighlight(verseNumber: Int) {
        val key = Triple(selectedBook ?: return, selectedChapter ?: return, verseNumber)
        if (highlightedVerses.contains(key)) {
            highlightedVerses.remove(key)
        } else {
            highlightedVerses.add(key)
        }

        displayChapter(key.first, key.second, null)

        // Tell adapter to rebuild to update drawer highlights too
        (drawerRecyclerView.adapter as? BookAdapter)?.rebuildList()
    }

    // navigation button code
    fun goToPreviousChapter() {
        val currentBook = selectedBook ?: return
        val currentChapter = selectedChapter ?: return

        val chapters = kjvData.verses
            .filter { it.bookName == currentBook }
            .map { it.chapter }
            .distinct()
            .sorted()

        val index = chapters.indexOf(currentChapter)
        if (index > 0) {
            displayChapter(currentBook, chapters[index - 1], 1, tempHighlight = false)
        } else {
            // Go to previous book
            val books = kjvData.verses.map { it.bookName }.distinct()
            val bookIndex = books.indexOf(currentBook)
            if (bookIndex > 0) {
                val prevBook = books[bookIndex - 1]
                val prevChapters = kjvData.verses.filter { it.bookName == prevBook }
                    .map { it.chapter }
                    .distinct()
                    .sorted()
                displayChapter(prevBook, prevChapters.last(), 1, tempHighlight = false)
            }

        }
    }

    fun goToNextChapter() {
        val currentBook = selectedBook ?: return
        val currentChapter = selectedChapter ?: return

        val chapters = kjvData.verses
            .filter { it.bookName == currentBook }
            .map { it.chapter }
            .distinct()
            .sorted()

        val index = chapters.indexOf(currentChapter)
        if (index < chapters.size - 1) {
            displayChapter(currentBook, chapters[index + 1], 1, tempHighlight = false)
        } else {
            // Optional: go to next book
            val books = kjvData.verses.map { it.bookName }.distinct()
            val bookIndex = books.indexOf(currentBook)
            if (bookIndex < books.size - 1) {
                val nextBook = books[bookIndex + 1]
                val nextChapters = kjvData.verses.filter { it.bookName == nextBook }
                    .map { it.chapter }
                    .distinct()
                    .sorted()
                displayChapter(nextBook, nextChapters.first(), 1, tempHighlight = false)
            }

        }
    }



    // Called when the activity starts
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout using ViewBinding and set it as the screen's content
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // clear button
        findViewById<TextView>(R.id.clear_highlights).setOnClickListener {
            highlightedVerses.clear()
            displayChapter(selectedBook ?: return@setOnClickListener, selectedChapter ?: return@setOnClickListener, null)
        }

        findViewById<View>(R.id.btn_search).setOnClickListener {
            Toast.makeText(this, "Search clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_settings).setOnClickListener {
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_version).setOnClickListener {
            Toast.makeText(this, "Version selector clicked", Toast.LENGTH_SHORT).show()
        }
        val SCROLL_THRESHOLD = 20
        //scroll to the verse selected
        var lastScrollY = 0

        val scrollView = findViewById<ScrollView>(R.id.verse_scroll_view)
        // clear the temp highlighted verses
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY
            if (!ignoreNextScroll && selectedVerse != null && Math.abs(scrollY - lastScrollY) > SCROLL_THRESHOLD) {
                selectedVerse = null
                displayChapter(selectedBook ?: return@addOnScrollChangedListener,
                    selectedChapter ?: return@addOnScrollChangedListener, null)
            }
            lastScrollY = scrollY
        }
        val topBar = findViewById<View>(R.id.tool_bar)
        //display the navigation bar when scrolling up or when at the bottom of a chapter
        val bottomBar = findViewById<View>(R.id.bottom_navigation)

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY
            val contentHeight = scrollView.getChildAt(0).height
            val scrollViewHeight = scrollView.height

            val isAtBottom = scrollY + scrollViewHeight >= contentHeight - 10
            val isScrollingUp = scrollY < lastScrollY

            if (isAtBottom || isScrollingUp) {
                topBar.visibility = View.VISIBLE
                bottomBar.visibility = View.VISIBLE
            } else {
                topBar.visibility = View.GONE
                bottomBar.visibility = View.GONE
            }

            lastScrollY = scrollY
        }


        val chapterTitle = findViewById<TextView>(R.id.chapter_title)
        val prevChapter = findViewById<TextView>(R.id.prev_chapter)
        val nextChapter = findViewById<TextView>(R.id.next_chapter)

        prevChapter.setOnClickListener {
            goToPreviousChapter()
        }

        nextChapter.setOnClickListener {
            goToNextChapter()
        }

        val verseTextView: TextView = findViewById(R.id.verse_text_view)

        // Grab drawer and RecyclerView from the layout
        drawerLayout = binding.drawerLayout
        drawerRecyclerView = findViewById(R.id.bible_drawer_list)

        // Load JSON file from assets and parse with Gson
        val kjvJsonStream = assets.open("kjv.json")
        val gson = Gson()
        kjvData = gson.fromJson(InputStreamReader(kjvJsonStream), BibleData::class.java)

        // Get a list of distinct book names (Genesis, Exodus, etc.)
        val bookNames = kjvData.verses.map { it.bookName }.distinct()

        // Setup RecyclerView (vertical list) and plug in adapter to show book names
        drawerRecyclerView.layoutManager = LinearLayoutManager(this)
        drawerRecyclerView.adapter = BookAdapter(kjvData)

        // on start, this is loading chapters or verses
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        val lastBook = prefs.getString("last_book", "Genesis")!!
        val lastChapter = prefs.getInt("last_chapter", 1)
        val lastVerse = if (prefs.contains("last_verse")) prefs.getInt("last_verse", 1) else null

        displayChapter(lastBook, lastChapter, lastVerse)
    }
}