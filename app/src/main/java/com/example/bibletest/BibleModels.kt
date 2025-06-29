package com.example.bibletest

import com.google.gson.annotations.SerializedName

// Top-level data class for the entire JSON structure
data class BibleData(
    val metadata: Metadata, // Bible title, description, etc.
    val verses: List<Verse> // All verses in the Bible (Genesis 1:1 onward)
)
// Metadata about the Bible version
data class Metadata(
    val name: String,
    val shortname: String
)

// Represents a single verse in the Bible
data class Verse(
    //Kotlin Prefers camelCase so _ are bad so we are converting the Json book_name to a kotlin preferred variable bookName
    @SerializedName("book_name") val bookName: String,  // Full book name like "Genesis"
    val book: Int,                                      // Book number (1 = Genesis)
    val chapter: Int,                                   // Chapter number
    val verse: Int,                                     // Verse number
    val text: String                                    // Verse text content
)

