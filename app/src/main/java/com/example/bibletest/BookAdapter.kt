package com.example.bibletest

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Represents different types of items in the list
sealed class BibleListItem {
    data class BookItem(val name: String) : BibleListItem()
    data class ChapterItem(val bookName: String, val chapter: Int) : BibleListItem()
    data class VerseItem(val bookName: String, val chapter: Int, val verseNumber: Int) : BibleListItem()
}

// Adapter takes a list of book names and a click callback
class BookAdapter(
    private val kjvData: BibleData
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val expandedBooks = mutableSetOf<String>()
    private val expandedChapters = mutableSetOf<Pair<String, Int>>()

    private var items: List<BibleListItem> = buildListFromData()

    fun rebuildList() {
        items = buildListFromData()
        notifyDataSetChanged()
    }

    fun collapseAll() {
        expandedBooks.clear()
        expandedChapters.clear()
        rebuildList()
    }

    private fun buildListFromData(): List<BibleListItem> {
        val result = mutableListOf<BibleListItem>()
        val verses = kjvData.verses
        val books = verses.map { it.bookName }.distinct()

        for (book in books) {
            result.add(BibleListItem.BookItem(book))
            if (expandedBooks.contains(book)) {
                val chapters = verses.filter { it.bookName == book }.map { it.chapter }.distinct().sorted()
                for (chapter in chapters) {
                    result.add(BibleListItem.ChapterItem(book, chapter))
                    if (expandedChapters.contains(book to chapter)) {
                        val versesInChapter = verses.filter { it.bookName == book && it.chapter == chapter }
                        for (v in versesInChapter) {
                            result.add(BibleListItem.VerseItem(book, chapter, v.verse))
                        }
                    }
                }
            }
        }
        return result
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BibleListItem.BookItem -> 0
            is BibleListItem.ChapterItem -> 1
            is BibleListItem.VerseItem -> 2
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return when (viewType) {
            0 -> BookViewHolder(view)
            1 -> ChapterViewHolder(view)
            else -> VerseViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context  // Get context to use getString()

        when (val item = items[position]) {
            is BibleListItem.BookItem -> {
                (holder as BookViewHolder).textView.text = item.name
                holder.itemView.setBackgroundColor(Color.WHITE)
                holder.itemView.setOnClickListener {
                    if (expandedBooks.contains(item.name)) expandedBooks.remove(item.name)
                    else expandedBooks.add(item.name)
                    rebuildList()
                }
                (holder as BookViewHolder).textView.apply {
                    text = item.name
                    setTextColor(Color.BLACK) // Force text to be black
                }
            }
            is BibleListItem.ChapterItem -> {
                (holder as ChapterViewHolder).textView.text = context.getString(R.string.chapter_title, item.chapter)
                holder.itemView.setBackgroundColor(Color.WHITE)
                holder.itemView.setOnClickListener {
                    val key = item.bookName to item.chapter
                    if (expandedChapters.contains(key)) expandedChapters.remove(key)
                    else expandedChapters.add(key)
                    rebuildList()
                }
                (holder as ChapterViewHolder).textView.apply {
                    text = context.getString(R.string.chapter_title, item.chapter)
                    setTextColor(Color.BLACK)
                }
            }
            is BibleListItem.VerseItem -> {
                holder.itemView.setBackgroundColor(Color.WHITE)
                val context = holder.itemView.context
                val activity = context as? MainActivity ?: return

                val isHighlighted = activity.highlightedVerses.contains(Triple(item.bookName, item.chapter, item.verseNumber))

                val spannable = SpannableString(item.verseNumber.toString())
                if (isHighlighted) {
                    spannable.setSpan(UnderlineSpan(), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                (holder as VerseViewHolder).textView.text = spannable

                holder.itemView.setOnClickListener {
                    if (activity.selectedBook == item.bookName && activity.selectedChapter == item.chapter) {
                        // Always reload even if it's the same chapter
                        activity.selectedVerse = item.verseNumber
                        activity.displayChapter(item.bookName, item.chapter, item.verseNumber)
                        activity.closeDrawer()
                    } else {
                        // New chapter
                        activity.selectedVerse = item.verseNumber
                        activity.displayChapter(item.bookName, item.chapter, item.verseNumber)
                        activity.closeDrawer()
                    }
                }
                (holder as VerseViewHolder).textView.apply {
                    text = spannable
                    setTextColor(Color.BLACK)
                }
            }
        }
    }

    class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    class VerseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }
}