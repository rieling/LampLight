package com.example.bibletest

import android.annotation.SuppressLint
import android.content.Intent
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
import android.view.MotionEvent
import android.widget.Toast
import android.view.ViewTreeObserver
import android.widget.Button
import androidx.core.view.GravityCompat
import com.example.bibletest.VerseRenderer

class MainActivity : AppCompatActivity() {

    private var userScrolling = false

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
        val chapterTitle = findViewById<TextView>(R.id.btn_chapter_title)
        chapterTitle.text = getString(R.string.chapter_display, bookName, chapter)

        // Verses in chapter
        val versesInChapter = kjvData.verses.filter { it.bookName == bookName && it.chapter == chapter }

        // --- NEW: Use VerseRenderer to build Spannable ---
        val renderer = VerseRenderer(
            context = this,
            verses = versesInChapter,
            isParagraphMode = isParagraphMode,
            highlightedVerses = highlightedVerses,
            redLetterColor = redLetterColor,
            tempHighlightVerse = scrollToVerse
        )
        val builder = renderer.buildChapterSpannable()

        // Remember offsets for scrolling
        val selectedVerseStart = renderer.selectedVerseStart

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

                // Delay reset of ignoreNextScroll to allow scroll to complete
                scrollView.postDelayed({
                    ignoreNextScroll = false
                }, 200) // 200ms is usually enough for one-screen scroll
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



    // Called when the activity starts
    @SuppressLint("ClickableViewAccessibility")
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
                val intent = Intent(this, FontSettingsActivity::class.java)
                startActivity(intent)
        }

        findViewById<View>(R.id.btn_version).setOnClickListener {
            Toast.makeText(this, "Version selector clicked", Toast.LENGTH_SHORT).show()
        }

        val scrollView = findViewById<ScrollView>(R.id.verse_scroll_view)
        val topBar = findViewById<View>(R.id.tool_bar)
        val bottomBar = findViewById<View>(R.id.bottom_navigation)

        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> userScrolling = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> userScrolling = false
            }
            false
        }

        var lastScrollYForBars = 0
        var lastScrollDirectionUp = true

        scrollView.viewTreeObserver.addOnScrollChangedListener {


            val contentHeight = scrollView.getChildAt(0).height
            val scrollViewHeight = scrollView.height
            val scrollY = scrollView.scrollY

            val isAtTop = scrollY <= 0
            val isAtBottom = scrollY + scrollViewHeight >= contentHeight - 10
            val isScrollingUp = scrollY < lastScrollYForBars
            val isScrollingDown = scrollY > lastScrollYForBars

            // Update last scroll direction
            lastScrollDirectionUp = isScrollingUp

            // Only clear temp highlight if user is actually scrolling
            if (!ignoreNextScroll && userScrolling && selectedVerse != null) {
                selectedVerse = null
                displayChapter(
                    selectedBook ?: return@addOnScrollChangedListener,
                    selectedChapter ?: return@addOnScrollChangedListener,
                    null
                )
            }

            when {
                isAtTop -> { // fully at top
                    topBar.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                }
                isAtBottom || isScrollingUp -> { // scrolling up or at bottom
                    topBar.visibility = View.VISIBLE
                    bottomBar.visibility = View.VISIBLE
                }
                isScrollingDown && !isAtBottom -> { // scrolling down somewhere in the middle
                    topBar.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                }
            }

            lastScrollYForBars = scrollY
        }


        val chapterTitle = findViewById<TextView>(R.id.btn_chapter_title)
        val prevChapter = findViewById<TextView>(R.id.prev_chapter)
        val nextChapter = findViewById<TextView>(R.id.next_chapter)

        chapterTitle.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)

            // Scroll drawer to the right book
            scrollToCurrentBook(selectedBook ?: return@setOnClickListener, selectedChapter ?: return@setOnClickListener)
        }

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