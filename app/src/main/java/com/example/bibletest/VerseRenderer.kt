package com.example.bibletest

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.view.View

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

class VerseRenderer(
    private val context: Context,
    private val verses: List<Verse>,
    private val isParagraphMode: Boolean,
    private val highlightedVerses: Set<Triple<String, Int, Int>>,
    private val redLetterColor: Int,
    private val tempHighlightVerse: Int?,
    private val onVerseClick: ((Int) -> Unit)? = null       // callback for click
) {

    var selectedVerseStart: Int = -1

    fun buildChapterSpannable(): SpannableStringBuilder {
        val builder = SpannableStringBuilder()

        // Header: Book name and chapter number
        val bookTitle = "${verses.firstOrNull()?.bookName ?: ""}\n"
        val chapterNumber = "${verses.firstOrNull()?.chapter ?: ""}\n"

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

        for (v in verses) {
            val parsed = parseVerseText(v.text)
            val text = parsed.text
            val redRanges = parsed.redRanges
            val supleRanges = parsed.supleRanges
            val endsParagraph = parsed.endsParagraph

            val start = builder.length

            if (!isParagraphMode) {
                builder.append("[${v.verse}] ")
            } else {
                val verseStr = v.verse.toString()
                builder.append(verseStr)
                builder.setSpan(
                    android.text.style.RelativeSizeSpan(0.6f),
                    builder.length - verseStr.length,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    SuperscriptSpan(),
                    builder.length - verseStr.length,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(" ")
            }

            val verseStart = builder.length
            builder.append(text)
            val verseEnd = builder.length

            if (v.verse == tempHighlightVerse) {
                selectedVerseStart = verseStart
            }

            // Red letters
            for (range in redRanges) {
                builder.setSpan(
                    ForegroundColorSpan(redLetterColor),
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
                        StyleSpan(Typeface.ITALIC),
                        spanStart,
                        spanEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Clickable span
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onVerseClick?.invoke(v.verse)
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.isUnderlineText = false
                }
            }
            builder.setSpan(clickableSpan, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Underline highlight
            if (highlightedVerses.contains(Triple(verses.first().bookName, verses.first().chapter, v.verse)) ||
                (tempHighlightVerse != null && v.verse == tempHighlightVerse)
            ) {
                builder.setSpan(
                    UnderlineSpan(),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            builder.append(if (endsParagraph || !isParagraphMode) "\n\n" else " ")
        }

        return builder
    }
}