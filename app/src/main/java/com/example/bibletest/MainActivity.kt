package com.example.bibletest
// Package = your app namespace. Tells Android this class belongs to com.example.bibletest.

// ----------- Imports: these pull in Android + app classes you use ------------
import android.annotation.SuppressLint
import android.content.Intent          // lets you launch other Activities (settings, etc.)
import android.content.res.ColorStateList
import android.graphics.Color         // colors for text/highlight
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle              // used in onCreate lifecycle
import android.text.Layout
import android.text.method.LinkMovementMethod // allows clickable text spans
import android.view.View              // generic View class
import android.widget.*               // ScrollView, TextView, Button, Toast etc.
import androidx.appcompat.app.AppCompatActivity // base Activity with support features
import androidx.drawerlayout.widget.DrawerLayout // side drawer layout
import androidx.recyclerview.widget.* // RecyclerView + layout managers
import com.example.bibletest.databinding.ActivityMainBinding // auto-generated binding class for activity_main.xml
import java.io.InputStreamReader      // read JSON file from assets
import com.google.gson.Gson           // JSON parser
import androidx.core.content.edit     // extension for SharedPreferences edit {}
import androidx.core.content.ContextCompat // safe way to load colors
import android.view.MotionEvent       // used for scroll/touch detection
import androidx.core.view.GravityCompat // constants for drawer direction

// ---------------- Main Activity -----------------
class MainActivity : AppCompatActivity() {

    private var userScrolling = false
    // Flag: true when user actively drags screen, false when idle.

    var selectedBook: String? = null
    var selectedChapter: Int? = null
    var selectedVerse: Int? = null
    // Track which book/chapter/verse you are currently viewing.

    var isParagraphMode = true // Default to verse-by-verse mode
    // Flag for layout mode (paragraph vs verse-by-verse).

    val highlightedVerses = mutableSetOf<Triple<String, Int, Int>>()
    // Holds which verses are highlighted (Book, Chapter, Verse).

    private var ignoreNextScroll = false // Used to avoid looped triggering of scroll listeners

    // View binding object gives access to all views in activity_main.xml and included layouts
    private lateinit var binding: ActivityMainBinding

    // Drawer layout and RecyclerView for your custom drawer (books list)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerRecyclerView: RecyclerView     // Recycler list of books

    // This holds your parsed KJV Bible data
    private lateinit var kjvData: BibleData

    // ---------------- Save reading progress ----------------
    // Saves the user's current book/chapter/verse into local storage (SharedPreferences).
    private fun saveLastRead(book: String, chapter: Int, verse: Int?) {
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        // "bible_prefs" is your private app settings file.
        prefs.edit {
            putString("last_book", book)
            putInt("last_chapter", chapter)
            if (verse != null) {
                putInt("last_verse", verse)
            } else {
                remove("last_verse")
            }
        }
        // Called inside displayChapter() to remember where the user was.
    }

    // ---------------- Display chapter/verse content ----------------
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

        // --- Load settings dynamically ---
        val fontSize = SettingsManager.getFontSize(this)
        val fontName = SettingsManager.getFontName(this)
        isParagraphMode = SettingsManager.getParagraphMode(this)
        val bgColor = SettingsManager.getThemeBackground(this)
        val textColor = SettingsManager.getThemeTextColor(this)
        val redLetterColor = SettingsManager.getThemeRedLetterColor(this)

        // --- Chapter title ---
        val chapterTitle = findViewById<TextView>(R.id.btn_chapter_title)
        chapterTitle.text = getString(R.string.chapter_display, bookName, chapter)
        chapterTitle.setTextColor(textColor)

        // --- Verse TextView ---
        val verseTextView = findViewById<TextView>(R.id.verse_text_view)
        val scrollView = findViewById<ScrollView>(R.id.verse_scroll_view)
        val mainContent = findViewById<FrameLayout>(R.id.main_content)

        val versesInChapter = kjvData.verses.filter { it.bookName == bookName && it.chapter == chapter }

        val renderer = VerseRenderer(
            context = this,
            verses = versesInChapter,
            isParagraphMode = isParagraphMode,
            highlightedVerses = highlightedVerses,
            redLetterColor = redLetterColor,
            tempHighlightVerse = scrollToVerse,
            onVerseClick = { verse ->
                val key = Triple(bookName, chapter, verse)
                if (highlightedVerses.contains(key)) highlightedVerses.remove(key)
                else highlightedVerses.add(key)
                displayChapter(bookName, chapter, scrollToVerse = null)
            }
        )

        // --- Apply text and styles ---
        verseTextView.text = renderer.buildChapterSpannable()
        verseTextView.setTextColor(textColor)
        verseTextView.setBackgroundColor(bgColor)
        scrollView.setBackgroundColor(bgColor)
        mainContent.setBackgroundColor(bgColor)
        verseTextView.textSize = fontSize
        verseTextView.typeface = when (fontName) {
            "serif" -> Typeface.SERIF
            "sans-serif" -> Typeface.SANS_SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        verseTextView.movementMethod = LinkMovementMethod.getInstance()
        verseTextView.highlightColor = Color.TRANSPARENT
        verseTextView.isClickable = true
        verseTextView.isFocusable = true

        // --- Paragraph justification ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verseTextView.justificationMode =
                if (isParagraphMode) LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                else LineBreaker.JUSTIFICATION_MODE_NONE
        }

        // --- Scroll to selected verse ---
        val selectedVerseStart = renderer.selectedVerseStart
        if (selectedVerseStart >= 0) {
            ignoreNextScroll = true
            scrollView.post {
                val layout = verseTextView.layout
                if (layout != null) {
                    val line = layout.getLineForOffset(selectedVerseStart)
                    val y = layout.getLineTop(line)
                    scrollView.smoothScrollTo(0, y)
                }
                scrollView.postDelayed({ ignoreNextScroll = false }, 200)
            }
        }

        // --- Save reading progress ---
        saveLastRead(bookName, chapter, scrollToVerse)
    }



    // ---------------- Drawer controls ----------------
    //close the drawer and collapse the lists inside of it
    fun closeDrawer() {
        drawerLayout.closeDrawers()
        // Delay collapse until drawer is actually closed
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerClosed(drawerView: View) {
                val adapter = drawerRecyclerView.adapter as? BookAdapter
                adapter?.collapseAll() // collapse expanded book lists
                drawerLayout.removeDrawerListener(this) // Clean up listener
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    // -------------------------- navigation buttons -------------------------
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

    // MainActivity: handle result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            applyTheme()
            displayChapter(selectedBook ?: return, selectedChapter ?: return, selectedVerse)
        }
    }

    private fun applyTheme() {
        val bgColor = SettingsManager.getThemeBackground(this)
        val textColor = SettingsManager.getThemeTextColor(this)

        val scrollView: ScrollView = findViewById(R.id.verse_scroll_view)
        val verseTextView: TextView = findViewById(R.id.verse_text_view)
        val topBar: View = findViewById(R.id.tool_bar)
        val bottomBar: View = findViewById(R.id.bottom_navigation)
        val chapterTitle: TextView = findViewById(R.id.btn_chapter_title)
        val prevChapter: TextView = findViewById(R.id.prev_chapter)
        val nextChapter: TextView = findViewById(R.id.next_chapter)
        val clearHighlights: TextView = findViewById(R.id.clear_highlights)
        val btnSearch: ImageButton = findViewById(R.id.btn_search)
        val btnSettings: ImageButton = findViewById(R.id.btn_settings)
        val btnVersion: LinearLayout = findViewById(R.id.btn_version)
        val versionIcon: ImageView = btnVersion.findViewById(R.id.version_icon)
        val versionText: TextView = btnVersion.findViewById(R.id.version_title)
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val mainContent: View = findViewById(R.id.main_content)

        val bibleBook: RecyclerView = findViewById(R.id.bible_drawer_list)

        val searchicon = ContextCompat.getDrawable(this, R.drawable.ic_search)?.mutate()
        val settingsicon = ContextCompat.getDrawable(this, R.drawable.ic_settings)?.mutate()
        val versionicon = ContextCompat.getDrawable(this, R.drawable.ic_version)?.mutate()

        val drawerContainer: View = (drawerLayout.getChildAt(1)) // the drawer side panel
        drawerContainer.setBackgroundColor(bgColor)

        drawerLayout.setBackgroundColor(bgColor)
        mainContent.setBackgroundColor(bgColor)

        scrollView.setBackgroundColor(bgColor)

        verseTextView.setBackgroundColor(bgColor)
        verseTextView.setTextColor(textColor)

        topBar.setBackgroundColor(bgColor)
        bottomBar.setBackgroundColor(bgColor)
        chapterTitle.setTextColor(textColor)
        prevChapter.setTextColor(textColor)
        nextChapter.setTextColor(textColor)
        clearHighlights.setTextColor(textColor)

        searchicon?.setTint(SettingsManager.getThemeTextColor(this)) // Apply theme color
        settingsicon?.setTint(SettingsManager.getThemeTextColor(this)) // Apply theme color
        versionicon?.setTint(SettingsManager.getThemeTextColor(this))
        versionText.setTextColor(SettingsManager.getThemeTextColor(this))

        bibleBook.setBackgroundColor(SettingsManager.getThemeBackground(this))

        btnSearch.setImageDrawable(searchicon)
        btnSettings.setImageDrawable(settingsicon)
        versionIcon.setImageDrawable(versionicon)
    }

    private fun scrollToCurrentBook(bookName: String, chapter: Int) {
        val recyclerView = findViewById<RecyclerView>(R.id.bible_drawer_list)
        val adapter = recyclerView.adapter as BookAdapter

        val position = adapter.getBookPosition(bookName)
        if (position != -1) {
            adapter.expandBookAt(bookName, chapter) // expand first so RecyclerView has the right items
            recyclerView.post {
                recyclerView.scrollToPosition(position)
            }
        }
    }


    // ---------------- Lifecycle: onCreate ----------------
    //called when activity starts
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ---------------- Load user settings ----------------
        val fontSize = SettingsManager.getFontSize(this)
        val fontName = SettingsManager.getFontName(this)
        isParagraphMode = SettingsManager.getParagraphMode(this)
        val bgColor = SettingsManager.getThemeBackground(this)
        val textColor = SettingsManager.getThemeTextColor(this)

        // ---------------- Views ----------------
        val verseTextView: TextView = findViewById(R.id.verse_text_view)
        val scrollView: ScrollView = findViewById(R.id.verse_scroll_view)
        val topBar: View = findViewById(R.id.tool_bar)
        val bottomBar: View = findViewById(R.id.bottom_navigation)
        val chapterTitle: TextView = findViewById(R.id.btn_chapter_title)
        val prevChapter: TextView = findViewById(R.id.prev_chapter)
        val nextChapter: TextView = findViewById(R.id.next_chapter)
        val clearHighlights: TextView = findViewById(R.id.clear_highlights)
        val btnSearch: View = findViewById(R.id.btn_search)
        val btnSettings: View = findViewById(R.id.btn_settings)
        val btnVersion: View = findViewById(R.id.btn_version)

        // ---------------- Apply theme and font ----------------
        chapterTitle.setTextColor(textColor)
        prevChapter.setTextColor(textColor)
        nextChapter.setTextColor(textColor)
        clearHighlights.setTextColor(textColor)

        // For simple buttons
        btnSearch.foregroundTintList = ColorStateList.valueOf(textColor)
        btnSettings.foregroundTintList = ColorStateList.valueOf(textColor)
        btnVersion.foregroundTintList = ColorStateList.valueOf(textColor)

        // ---------------- Clear highlights ----------------
        clearHighlights.setOnClickListener {
            highlightedVerses.clear()
            displayChapter(
                selectedBook ?: return@setOnClickListener,
                selectedChapter ?: return@setOnClickListener,
                null
            )
        }

        // ---------------- Search / Settings / Version ----------------
        btnSearch.setOnClickListener {
            Toast.makeText(this, "Search clicked", Toast.LENGTH_SHORT).show()
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, FontSettingsActivity::class.java)
            startActivityForResult(intent, 100)
        }

        btnVersion.setOnClickListener {
            Toast.makeText(this, "Version selector clicked", Toast.LENGTH_SHORT).show()
        }

        // ---------------- Scroll behavior ----------------
        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> userScrolling = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> userScrolling = false
            }
            false
        }

        var lastScrollYForBars = 0
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val contentHeight = scrollView.getChildAt(0).height
            val scrollViewHeight = scrollView.height
            val scrollY = scrollView.scrollY

            val isAtTop = scrollY <= 0
            val isAtBottom = scrollY + scrollViewHeight >= contentHeight - 10
            val isScrollingUp = scrollY < lastScrollYForBars
            val isScrollingDown = scrollY > lastScrollYForBars

            // Clear temp highlight if user scrolls manually
            if (!ignoreNextScroll && userScrolling && selectedVerse != null) {
                selectedVerse = null
                displayChapter(
                    selectedBook ?: return@addOnScrollChangedListener,
                    selectedChapter ?: return@addOnScrollChangedListener,
                    null
                )
            }

            // Show/hide toolbars depending on scroll
            when {
                isAtTop -> {
                    topBar.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                }
                isAtBottom || isScrollingUp -> {
                    topBar.visibility = View.VISIBLE
                    bottomBar.visibility = View.VISIBLE
                }
                isScrollingDown && !isAtBottom -> {
                    topBar.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                }
            }

            lastScrollYForBars = scrollY
        }

        // ---------------- Chapter navigation ----------------
        chapterTitle.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            scrollToCurrentBook(selectedBook ?: return@setOnClickListener, selectedChapter ?: return@setOnClickListener)
        }

        prevChapter.setOnClickListener { goToPreviousChapter() }
        nextChapter.setOnClickListener { goToNextChapter() }

        // ---------------- Drawer ----------------
        drawerLayout = binding.drawerLayout
        drawerRecyclerView = findViewById(R.id.bible_drawer_list)
        drawerRecyclerView.layoutManager = LinearLayoutManager(this)

        // Load KJV JSON
        val kjvJsonStream = assets.open("kjv.json")
        kjvData = Gson().fromJson(InputStreamReader(kjvJsonStream), BibleData::class.java)

        drawerRecyclerView.adapter = BookAdapter(kjvData)

        // ---------------- Load last read ----------------
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        val lastBook = prefs.getString("last_book", "Genesis")!!
        val lastChapter = prefs.getInt("last_chapter", 1)
        val lastVerse = if (prefs.contains("last_verse")) prefs.getInt("last_verse", 1) else null

        applyTheme()
        // Display last chapter with applied settings
        displayChapter(lastBook, lastChapter, lastVerse)
    }
}