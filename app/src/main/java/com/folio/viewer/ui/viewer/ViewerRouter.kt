package com.folio.viewer.ui.viewer

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folio.viewer.R
import com.folio.viewer.domain.DocumentFormat
import com.folio.viewer.viewer.office.ExcelViewer
import com.folio.viewer.viewer.office.PowerPointViewer
import com.folio.viewer.viewer.office.WordViewer
import com.folio.viewer.viewer.pdf.PdfViewer
import com.folio.viewer.viewer.rtf.RtfViewer
import com.folio.viewer.viewer.spreadsheet.CsvViewer
import com.folio.viewer.viewer.text.TextViewer
import com.folio.viewer.viewer.odt.OdtViewer
import com.folio.viewer.viewer.iwork.NumbersViewer
import com.folio.viewer.viewer.iwork.PagesViewer

@Composable
fun ViewerRouter(uri: Uri, format: DocumentFormat, displayName: String) {
    when (format) {
        DocumentFormat.PDF -> PdfViewer(uri)
        DocumentFormat.WORD -> WordViewer(uri, displayName)
        DocumentFormat.EXCEL -> ExcelViewer(uri, displayName)
        DocumentFormat.POWERPOINT -> PowerPointViewer(uri, displayName)
        DocumentFormat.CSV -> CsvViewer(uri)
        DocumentFormat.TEXT -> TextViewer(uri)
        DocumentFormat.RTF -> RtfViewer(uri)
        DocumentFormat.ODT -> OdtViewer(uri)
        DocumentFormat.NUMBERS -> NumbersViewer(uri)
        DocumentFormat.PAGES -> PagesViewer(uri)
        DocumentFormat.UNSUPPORTED -> UnsupportedState()
    }
}

@Composable
private fun UnsupportedState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.viewer_unsupported),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
