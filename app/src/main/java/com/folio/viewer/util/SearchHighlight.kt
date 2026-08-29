package com.folio.viewer.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Highlight all occurrences of [query] inside [source]. The [currentHitOffset]
 * (a character index inside [source], or -1) gets a stronger tint so the user
 * can spot the active hit while stepping through matches.
 */
object SearchHighlight {

    val Base = Color(0x66FFB547)          // amber @ 40%
    val Active = Color(0xCCFF9820)         // amber @ 80%

    fun apply(source: AnnotatedString, query: String, currentHitOffset: Int): AnnotatedString {
        if (query.isBlank()) return source
        val text = source.text
        val hits = findAll(text, query)
        if (hits.isEmpty()) return source
        return buildAnnotatedString {
            append(source)
            hits.forEach { range ->
                val start = range.first
                val end = range.last
                val isActive = start == currentHitOffset
                addStyle(
                    SpanStyle(background = if (isActive) Active else Base),
                    start, end
                )
            }
        }
    }

    fun findAll(text: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val q = query.lowercase()
        val src = text.lowercase()
        val out = mutableListOf<IntRange>()
        var i = 0
        while (i <= src.length - q.length) {
            val found = src.indexOf(q, i)
            if (found < 0) break
            out.add(found..(found + q.length))
            i = found + q.length
        }
        return out
    }
}
