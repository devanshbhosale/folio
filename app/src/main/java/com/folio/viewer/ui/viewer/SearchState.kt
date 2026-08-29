package com.folio.viewer.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Cross-viewer search coordination. The chrome owns query state; each viewer
 * observes it and highlights / scrolls to hits its own way.
 */
class SearchState {
    var visible by mutableStateOf(false)
    var query by mutableStateOf("")
    var totalHits by mutableIntStateOf(0)
    var currentHit by mutableIntStateOf(0)

    fun reset() {
        visible = false
        query = ""
        totalHits = 0
        currentHit = 0
    }

    fun next() {
        if (totalHits == 0) return
        currentHit = (currentHit + 1) % totalHits
    }

    fun prev() {
        if (totalHits == 0) return
        currentHit = if (currentHit <= 0) totalHits - 1 else currentHit - 1
    }
}

val LocalSearchState = compositionLocalOf<SearchState> { error("SearchState not provided") }

@Composable
fun rememberSearchState(): SearchState = remember { SearchState() }
