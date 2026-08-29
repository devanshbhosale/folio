package com.folio.viewer.viewer.rtf

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.ui.viewer.LocalSearchState
import com.folio.viewer.util.SearchHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal RTF text extractor — enough to view the words.
 * A proper RTF renderer is a full parser; this handles common escape codes
 * and control words to produce readable plain text.
 */
@Composable
fun RtfViewer(uri: Uri) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes ->
                    RtfTextExtractor.extract(String(bytes, Charsets.ISO_8859_1))
                } ?: error("open failed")
            }
        } catch (t: Throwable) { error = t.message }
    }

    LaunchedEffect(text, search.query) {
        val t = text ?: return@LaunchedEffect
        val hits = SearchHighlight.findAll(t, search.query)
        search.totalHits = hits.size
        if (search.currentHit >= hits.size) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        text == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> {
            val hits = remember(text, search.query) { SearchHighlight.findAll(text!!, search.query) }
            val highlighted = remember(text, search.query, search.currentHit) {
                val off = hits.getOrNull(search.currentHit)?.first ?: -1
                SearchHighlight.apply(AnnotatedString(text!!), search.query, off)
            }
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    highlighted,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

object RtfTextExtractor {
    /**
     * Strips RTF markup. Handles \'xx (hex byte), \uNNNN (unicode),
     * \par / \line / \tab, and skips control words + destinations like
     * \fonttbl, \colortbl, \stylesheet, \info, \pict.
     */
    fun extract(src: String): String {
        val out = StringBuilder()
        var i = 0
        var depth = 0
        val skipDests = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict",
            "header", "footer", "*", "author", "operator", "company", "themedata",
            "colorschememapping", "latentstyles", "listtable", "revtbl", "rsidtbl")
        var skipDepth = -1
        while (i < src.length) {
            val c = src[i]
            when (c) {
                '{' -> { depth++; i++ }
                '}' -> {
                    if (skipDepth == depth) skipDepth = -1
                    depth--; i++
                }
                '\\' -> {
                    // control word or symbol
                    if (i + 1 < src.length && src[i + 1] == '\'') {
                        // hex byte
                        val hex = src.substring(i + 2, (i + 4).coerceAtMost(src.length))
                        val byte = hex.toIntOrNull(16)
                        if (byte != null) {
                            if (skipDepth < 0) out.append(byteToChar(byte))
                        }
                        i += 4
                    } else if (i + 1 < src.length && (src[i + 1] == '\\' || src[i + 1] == '{' || src[i + 1] == '}')) {
                        if (skipDepth < 0) out.append(src[i + 1])
                        i += 2
                    } else {
                        val start = i + 1
                        var j = start
                        while (j < src.length && (src[j].isLetter() || (j == start && src[j] == '*'))) j++
                        val word = src.substring(start, j)
                        // numeric parameter
                        var k = j
                        while (k < src.length && (src[k] == '-' || src[k].isDigit())) k++
                        val param = src.substring(j, k).toIntOrNull()
                        // consume delimiter space
                        val next = if (k < src.length && src[k] == ' ') k + 1 else k
                        i = next
                        when {
                            word == "u" && param != null -> {
                                val ch = (param and 0xFFFF).toChar()
                                if (skipDepth < 0) out.append(ch)
                                // Skip fallback char
                                if (i < src.length && src[i] == '?') i++
                            }
                            word == "par" || word == "line" -> if (skipDepth < 0) out.append('\n')
                            word == "tab" -> if (skipDepth < 0) out.append('\t')
                            word in skipDests -> if (skipDepth < 0) skipDepth = depth
                            else -> { /* ignore other control words */ }
                        }
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (skipDepth < 0) out.append(c)
                    i++
                }
            }
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun byteToChar(b: Int): Char =
        try { String(byteArrayOf(b.toByte()), Charsets.ISO_8859_1)[0] } catch (_: Throwable) { '?' }
}
