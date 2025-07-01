package com.example.bibletest

import android.graphics.Color
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


class MainActivity : AppCompatActivity() {

    var selectedBook: String? = null
    var selectedChapter: Int? = null
    var selectedVerse: Int? = null

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
    fun displayChapter(bookName: String, chapter: Int, scrollToVerse: Int?, tempHighlight: Boolean = true) {
        selectedBook = bookName
        selectedChapter = chapter
        selectedVerse = scrollToVerse

        // Chapter title set here
        val chapterTitle = findViewById<TextView>(R.id.chapter_title)
        chapterTitle.text = getString(R.string.chapter_display, bookName, chapter)

        // the variable that holds the verses
        val versesInChapter = kjvData.verses.filter { it.bookName == bookName && it.chapter == chapter }
        val builder = SpannableStringBuilder()

        // Create and style the header
        val bookTitle = "$bookName\n"
        val chapterNumber = "$chapter\n"

        // Append book name (smaller and less prominent)
        val bookStart = builder.length
        builder.append(bookTitle)
        val bookEnd = builder.length
        builder.setSpan(android.text.style.RelativeSizeSpan(1.2f), bookStart, bookEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), bookStart, bookEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), bookStart, bookEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Append chapter number (big and bold)
        val chapterStart = builder.length
        builder.append(chapterNumber)
        val chapterEnd = builder.length
        builder.setSpan(android.text.style.RelativeSizeSpan(6.0f), chapterStart, chapterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), chapterStart, chapterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), chapterStart, chapterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // generate the clickable in underlineable verses
        for (v in versesInChapter) {
            val verseLabel = "[${v.verse}] "
            val verseText = "${v.text}\n\n"
            val start = builder.length
            builder.append(verseLabel).append(verseText)
            val end = builder.length

            // Clickable span toggling underline on this verse
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    toggleVerseHighlight(v.verse)
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false     // disable automatic underline
                    ds.color = Color.BLACK         // make it plain black
                }
            }
            builder.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Apply underline if this verse is highlighted or is the selectedVerse
            if (highlightedVerses.contains(Triple(bookName, chapter, v.verse)) || (tempHighlight && v.verse == selectedVerse)) {
                builder.setSpan(
                    UnderlineSpan(),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        // textView and scrollView get set to all the variables below including the builder which we just built above
        val textView = findViewById<TextView>(R.id.verse_text_view)
        val scrollView = findViewById<ScrollView>(R.id.verse_scroll_view)

        textView.setTextColor(Color.BLACK)
        textView.setBackgroundColor(Color.WHITE)
        textView.text = builder
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
        textView.isClickable = true
        textView.isFocusable = true

        if (selectedVerse != null) {
            scrollView.post {
                val layout = textView.layout
                if (layout != null) {
                    val lineStart = builder.indexOf("[$selectedVerse]")
                    val line = layout.getLineForOffset(lineStart)
                    val y = layout.getLineTop(line)
                    scrollView.smoothScrollTo(0, y)
                }
            }
        }

        if (selectedVerse != null) {
            ignoreNextScroll = true  // <-- set this flag BEFORE programmatic scroll
            scrollView.post {
                val layout = textView.layout
                if (layout != null) {
                    val lineStart = builder.indexOf("[$selectedVerse]")
                    val line = layout.getLineForOffset(lineStart)
                    val y = layout.getLineTop(line)
                    scrollView.smoothScrollTo(0, y)
                }
            }
        }
        //save to shared preferences
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
        //scroll to the verse selected
        var lastScrollY = 0

        val scrollView = findViewById<ScrollView>(R.id.verse_scroll_view)
        // clear the temp highlighted verses
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (ignoreNextScroll) {
                ignoreNextScroll = false  // ignore this scroll event caused by programmatic scrolling
                return@addOnScrollChangedListener
            }
            if (selectedVerse != null) {
                selectedVerse = null
                displayChapter(selectedBook ?: return@addOnScrollChangedListener, selectedChapter ?: return@addOnScrollChangedListener, null)
            }
        }
        //display the navigation bar when scrolling up or when at the bottom of a chapter
        val bottomBar = findViewById<View>(R.id.bottom_navigation)

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY
            val contentHeight = scrollView.getChildAt(0).height
            val scrollViewHeight = scrollView.height

            val isAtBottom = scrollY + scrollViewHeight >= contentHeight - 10
            val isScrollingUp = scrollY < lastScrollY

            if (isAtBottom || isScrollingUp) {
                bottomBar.visibility = View.VISIBLE
            } else {
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