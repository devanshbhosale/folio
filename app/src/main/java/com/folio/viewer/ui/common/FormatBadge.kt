package com.folio.viewer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.viewer.domain.DocumentFormat

@Composable
fun FormatBadge(fmt: DocumentFormat, big: Boolean = false) {
    val bg = when (fmt) {
        DocumentFormat.PDF -> Color(0xFFD64545)
        DocumentFormat.WORD -> Color(0xFF2B579A)
        DocumentFormat.EXCEL -> Color(0xFF217346)
        DocumentFormat.POWERPOINT -> Color(0xFFB7472A)
        DocumentFormat.CSV -> Color(0xFF3B7A57)
        DocumentFormat.TEXT -> Color(0xFF555555)
        DocumentFormat.RTF -> Color(0xFF6E4B8B)
        DocumentFormat.ODT -> Color(0xFF0E7C86)
        DocumentFormat.NUMBERS -> Color(0xFF35B34A)
        DocumentFormat.PAGES -> Color(0xFFE07C24)
        DocumentFormat.UNSUPPORTED -> Color(0xFF888888)
    }
    Text(
        text = fmt.display.uppercase(),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = if (big) 12.dp else 6.dp, vertical = if (big) 6.dp else 2.dp),
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (big) 16.sp else 10.sp,
        style = MaterialTheme.typography.labelMedium
    )
}
