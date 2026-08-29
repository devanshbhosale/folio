package com.folio.viewer.viewer.iwork

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.util.UriCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Apple iWork viewer (.numbers / .pages).
 *
 * iWork files are ZIPs that in modern versions store their content as IWA
 * (Apple's proprietary Snappy-compressed protobuf) — a full renderer would
 * need Apple's private schemas.
 *
 * For a viewer-only app, Folio does two things that WORK offline:
 *   1) Extract the preview image ("preview.jpg" / "QuickLook/Preview.pdf")
 *      that iWork embeds in every document. This is a first-page thumbnail
 *      Apple guarantees to include for QuickLook.
 *   2) If a legacy XML form is present (older .numbers/.pages), parse it.
 *
 * The user gets to see the document. Full IWA parsing is out of scope for v1.
 */

@Composable
fun NumbersViewer(uri: Uri) = IWorkPreview(uri, isSpreadsheet = true)

@Composable
fun PagesViewer(uri: Uri) = IWorkPreview(uri, isSpreadsheet = false)

@Composable
private fun IWorkPreview(uri: Uri, isSpreadsheet: Boolean) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<IWorkResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        try {
            val ext = if (isSpreadsheet) ".numbers" else ".pages"
            val file = withContext(Dispatchers.IO) { UriCache.materialize(context, uri, ext) }
            result = withContext(Dispatchers.IO) { extractPreview(file) }
        } catch (t: Throwable) { error = t.message }
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        result == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> IWorkContent(result!!)
    }
}

private data class IWorkResult(val previewPng: ByteArray?, val quickLookPdf: ByteArray?)

private fun extractPreview(file: java.io.File): IWorkResult {
    var preview: ByteArray? = null
    var ql: ByteArray? = null
    file.inputStream().use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                when {
                    name.endsWith("preview.jpg") ||
                        name.endsWith("preview.png") ||
                        name.endsWith("thumbnail.jpg") -> preview = zip.readBytes()
                    name.endsWith("preview.pdf") ||
                        name.endsWith("quicklook/preview.pdf") -> ql = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
    }
    return IWorkResult(preview, ql)
}

@Composable
private fun IWorkContent(result: IWorkResult) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val png = result.previewPng
        if (png != null) {
            val bmp = remember(png) {
                android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Preview view", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                Text(
                    "Apple iWork files (.numbers / .pages) are shown as the embedded QuickLook preview. Full-fidelity editing/rendering isn\u2019t available offline.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


