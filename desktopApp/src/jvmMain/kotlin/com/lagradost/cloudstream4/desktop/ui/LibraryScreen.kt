package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.desktop.History

/** Minimal SearchResponse so bookmarks reuse the poster card. */
private class BookmarkItem(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String?,
) : SearchResponse {
    override var posterHeaders: Map<String, String>? = null
    override var id: Int? = null
    override var quality: SearchQuality? = null
    override var score: Score? = null
}

@Composable
fun LibraryScreen(app: AppState) {
    val history by History.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Text("Library", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp))
        if (history.bookmarks.isEmpty()) {
            MessageBox("Your library is empty", "Use the Bookmark button on a title to save it here.", Modifier.fillMaxSize())
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(history.bookmarks, key = { it.apiName + it.url }) { b ->
                val item = BookmarkItem(b.name, b.url, b.apiName, b.type?.let { t -> TvType.entries.firstOrNull { it.name == t } }, b.posterUrl)
                PosterCard(item, width = 140.dp) { app.open(Screen.Details(b.apiName, b.url, b.name)) }
            }
        }
    }
}
