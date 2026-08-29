package com.folio.viewer.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.folio.viewer.FolioApp
import com.folio.viewer.ui.home.HomeScreen
import com.folio.viewer.ui.settings.SettingsScreen
import com.folio.viewer.ui.viewer.ViewerScreen
import java.net.URLDecoder
import java.net.URLEncoder

/** Central nav graph — three destinations. */
@Composable
fun FolioRoot(
    app: FolioApp,
    incomingUri: Uri?,
    onIncomingConsumed: () -> Unit
) {
    val nav = rememberNavController()

    // Auto-open a file received from an external "Open with…" intent.
    LaunchedEffect(incomingUri) {
        val u = incomingUri ?: return@LaunchedEffect
        val encoded = URLEncoder.encode(u.toString(), Charsets.UTF_8.name())
        nav.navigate("viewer/$encoded")
        onIncomingConsumed()
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                app = app,
                onOpenSettings = { nav.navigate("settings") },
                onOpenDocument = { uri ->
                    val encoded = URLEncoder.encode(uri, Charsets.UTF_8.name())
                    nav.navigate("viewer/$encoded")
                }
            )
        }
        composable("settings") {
            SettingsScreen(app = app, onBack = { nav.popBackStack() })
        }
        composable("viewer/{uri}") { backStack ->
            val encoded = backStack.arguments?.getString("uri").orEmpty()
            val decoded = URLDecoder.decode(encoded, Charsets.UTF_8.name())
            ViewerScreen(
                app = app,
                uriString = decoded,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
