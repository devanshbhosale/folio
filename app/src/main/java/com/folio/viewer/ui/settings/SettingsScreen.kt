package com.folio.viewer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folio.viewer.BuildConfig
import com.folio.viewer.FolioApp
import com.folio.viewer.R
import com.folio.viewer.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: FolioApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val theme by app.prefs.themeMode.collectAsState(initial = ThemeMode.System)
    val reader by app.prefs.readerMode.collectAsState(initial = false)
    var showClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            ThemeModePicker(current = theme, onPick = { scope.launch { app.prefs.setThemeMode(it) } })

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_reader_mode))
            SwitchRow(
                title = stringResource(R.string.settings_reader_mode),
                subtitle = stringResource(R.string.settings_reader_mode_body),
                checked = reader,
                onCheckedChange = { scope.launch { app.prefs.setReaderMode(it) } }
            )

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { showClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_clear_recents))
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.settings_clear_recents)) },
            text = { Text("Remove all recents? Your files stay on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    scope.launch { app.documents.clearRecents() }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun ThemeModePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val opts = listOf(
        ThemeMode.System to stringResource(R.string.settings_theme_system),
        ThemeMode.Light to stringResource(R.string.settings_theme_light),
        ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
    )
    Column {
        opts.forEach { (mode, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(selected = current == mode, onClick = { onPick(mode) })
                Text(label)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
