package com.folio.viewer.viewer.office

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.folio.viewer.util.UriCache
import com.folio.viewer.viewer.spreadsheet.SpreadsheetGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.FileInputStream

data class SheetPreview(val name: String, val rows: List<List<String>>)

@Composable
fun ExcelViewer(uri: Uri, displayName: String) {
    val context = LocalContext.current
    var sheets by remember { mutableStateOf<List<SheetPreview>?>(null) }
    var current by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            val ext = displayName.substringAfterLast('.', "xlsx").lowercase()
            val file = withContext(Dispatchers.IO) { UriCache.materialize(context, uri, ".$ext") }
            sheets = withContext(Dispatchers.IO) {
                FileInputStream(file).use { input ->
                    WorkbookFactory.create(input).use { wb ->
                        val fmt = DataFormatter(true)
                        val out = mutableListOf<SheetPreview>()
                        for (i in 0 until wb.numberOfSheets) {
                            val sheet = wb.getSheetAt(i)
                            val rows = mutableListOf<List<String>>()
                            val maxCol = (0..sheet.lastRowNum).maxOfOrNull { r ->
                                sheet.getRow(r)?.lastCellNum?.toInt() ?: 0
                            } ?: 0
                            for (r in 0..sheet.lastRowNum) {
                                val row = sheet.getRow(r)
                                val cells = if (row == null) List(maxCol) { "" } else {
                                    (0 until maxCol).map { c ->
                                        try { fmt.formatCellValue(row.getCell(c)) } catch (_: Throwable) { "" }
                                    }
                                }
                                rows.add(cells)
                                if (rows.size > 5000) break
                            }
                            out.add(SheetPreview(sheet.sheetName, rows))
                        }
                        out
                    }
                }
            }
        } catch (t: Throwable) { error = t.message }
    }

    LaunchedEffect(sheets, search.query, current) {
        val s = sheets ?: return@LaunchedEffect
        val q = search.query
        if (q.isBlank()) { search.totalHits = 0; return@LaunchedEffect }
        val qL = q.lowercase()
        val n = s.getOrNull(current)?.rows?.sumOf { row ->
            row.count { it.lowercase().contains(qL) }
        } ?: 0
        search.totalHits = n
        if (search.currentHit >= n) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        sheets == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    SpreadsheetGrid(
                        sheets!!.getOrNull(current)?.rows ?: emptyList(),
                        highlightQuery = search.query
                    )
                }
                AnimatedVisibility(visible = sheets!!.size > 1) {
                    SheetTabs(sheets!!, current) { current = it }
                }
            }
        }
    }
}

@Composable
private fun SheetTabs(sheets: List<SheetPreview>, current: Int, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 8.dp)) {
            sheets.forEachIndexed { i, sheet ->
                val selected = i == current
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        sheet.name,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
