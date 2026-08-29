package com.folio.viewer.viewer.spreadsheet

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.ui.viewer.LocalSearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun CsvViewer(uri: Uri) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<List<String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            rows = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        val parser = CsvParser()
                        val out = mutableListOf<List<String>>()
                        reader.forEachLine { out.add(parser.parseLine(it)) }
                        out
                    }
                } ?: error("open failed")
            }
        } catch (t: Throwable) { error = t.message }
    }

    LaunchedEffect(rows, search.query) {
        val r = rows ?: return@LaunchedEffect
        val q = search.query
        if (q.isBlank()) { search.totalHits = 0; return@LaunchedEffect }
        var n = 0
        val qL = q.lowercase()
        r.forEach { row -> row.forEach { cell -> if (cell.lowercase().contains(qL)) n++ } }
        search.totalHits = n
        if (search.currentHit >= n) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        rows == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> SpreadsheetGrid(rows!!, highlightQuery = search.query)
    }
}

@Composable
fun SpreadsheetGrid(
    rows: List<List<String>>,
    freezeHeader: Boolean = true,
    highlightQuery: String = ""
) {
    val horizontalScroll = rememberScrollState()
    val colCount = rows.maxOfOrNull { it.size } ?: 0
    val colWidths = remember(rows) { List(colCount) { col ->
        val maxLen = rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 4
        (maxLen.coerceIn(4, 40) * 8 + 24).dp
    } }
    val header = rows.firstOrNull()
    val body = if (freezeHeader && header != null) rows.drop(1) else rows

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (freezeHeader && header != null) {
            Row(
                Modifier
                    .horizontalScroll(horizontalScroll)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            ) {
                header.forEachIndexed { i, cell ->
                    CellText(
                        cell, width = colWidths.getOrElse(i) { 100.dp },
                        bold = true,
                        highlight = highlightQuery
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(body) { row ->
                Row(Modifier.horizontalScroll(horizontalScroll)) {
                    (0 until colCount).forEach { i ->
                        CellText(row.getOrElse(i) { "" },
                            width = colWidths.getOrElse(i) { 100.dp },
                            highlight = highlightQuery)
                    }
                }
            }
        }
    }
}

@Composable
private fun CellText(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    bold: Boolean = false,
    highlight: String = ""
) {
    val hit = highlight.isNotBlank() && text.contains(highlight, ignoreCase = true)
    Box(
        Modifier
            .width(width)
            .heightIn(min = 36.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (hit) androidx.compose.ui.graphics.Color(0x66FFB547)
                else MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Minimal RFC-4180 CSV parser with quoted-field support. */
private class CsvParser(private val delim: Char = ',') {
    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delim && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
