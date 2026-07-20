package com.io.ab.music.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import com.io.ab.music.ui.components.SwipeDismissWrapper
import com.io.ab.music.ui.theme.NeonDepthPurple
import com.io.ab.music.ui.theme.NeonDepthPink

// ── Data model ────────────────────────────────────────────────────────────────
data class VersionEntry(
    val versionName : String,
    val versionCode : Int,
    val date        : String,
    val changes     : List<String>
)

private sealed class ChangelogState {
    object Loading : ChangelogState()
    data class Success(val versions: List<VersionEntry>) : ChangelogState()
    data class Error(val message: String) : ChangelogState()
}

private const val CHANGELOG_URL =
    "https://raw.githubusercontent.com/Sandeepbedia/AB-Player/refs/heads/main/Changelog-history.json"

// PERF FIX: this screen used to hit the network with java.net.URL.readText() and
// NO timeout set (URLConnection defaults to an infinite timeout) on every single
// visit — so on a slow/flaky connection it could hang showing a spinner for a long
// time, and even on a good connection it re-downloaded + re-parsed from scratch
// every time you opened History from Settings, which is what made it feel
// noticeably less smooth than the rest of the app. Now the parsed result is kept
// in memory for the process lifetime: repeat visits render instantly from cache
// while a fresh copy is silently fetched in the background to pick up new entries.
private object ChangelogCache {
    var cached: List<VersionEntry>? = null
}

/**
 * Full-screen changelog history screen.
 * Fetches Changelog-history.json from GitHub and renders each version entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogHistoryScreen(onBack: () -> Unit) {
    val cachedOnEntry = ChangelogCache.cached
    var state by remember {
        mutableStateOf<ChangelogState>(
            if (cachedOnEntry != null) ChangelogState.Success(cachedOnEntry) else ChangelogState.Loading
        )
    }
    val listState = rememberLazyListState()
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryTrigger) {
        // Only show the spinner if we have nothing cached to display yet.
        if (cachedOnEntry == null) state = ChangelogState.Loading
        try {
            val json = withContext(Dispatchers.IO) {
                val connection = URL(CHANGELOG_URL).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.inputStream.bufferedReader().use { it.readText() }
            }
            val parsed = parseChangelog(json)
            ChangelogCache.cached = parsed
            state = ChangelogState.Success(parsed)
        } catch (e: Exception) {
            // If we already have a cached copy on screen, keep showing it silently
            // rather than replacing it with an error just because a background refresh failed.
            if (cachedOnEntry == null) {
                state = ChangelogState.Error("Could not load changelog: ${e.message}")
            }
        }
    }

    SwipeDismissWrapper(onDismiss = onBack) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Changelog",
                            style      = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "AB Player version history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (com.io.ab.music.ui.theme.LocalWallpaperActive.current)
                        Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        },
        // FIX: match every other screen — drop the solid containerColor when the
        // wallpaper theme is active so the wallpaper shows through here too,
        // instead of this screen alone painting an opaque background "surface".
        containerColor = if (com.io.ab.music.ui.theme.LocalWallpaperActive.current)
            Color.Transparent else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is ChangelogState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text(
                            "Loading changelog…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ChangelogState.Error -> {
                    Column(
                        modifier            = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff, null,
                            modifier = Modifier.size(48.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            "Couldn't load changelog",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = {
                                state = ChangelogState.Loading
                                retryTrigger++
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }

                is ChangelogState.Success -> {
                    if (s.versions.isEmpty()) {
                        Text(
                            "No changelog entries found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            state           = listState,
                            modifier        = Modifier.fillMaxSize(),
                            contentPadding  = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                s.versions,
                                key         = { _, v -> v.versionCode },
                                contentType = { _, _ -> "version_card" }
                            ) { index, version ->
                                VersionCard(version = version, isLatest = index == 0)
                            }
                            item(key = "bottom_pad") { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun VersionCard(version: VersionEntry, isLatest: Boolean) {
    val neonColor = if (isLatest) NeonDepthPurple else NeonDepthPink
    // FIX: "tap to expand" — the changelog for a version is now collapsed by
    // default and only shows when the card is tapped, instead of every
    // version's full change list being dumped on screen at once. Latest
    // version starts expanded since that's usually what people want to read.
    var expanded by remember { mutableStateOf(isLatest) }
    val chevronRotation by animateFloatAsState(
        targetValue   = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label         = "changelogChevron"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(enabled = version.changes.isNotEmpty()) { expanded = !expanded },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = neonColor.copy(alpha = if (isLatest) 0.22f else 0.10f)
        ),
        border    = BorderStroke(
            width = if (isLatest) 2.dp else 1.dp,
            color = if (isLatest) neonColor else neonColor.copy(alpha = 0.18f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Version header row
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gradient version badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(NeonDepthPurple, NeonDepthPink))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "v${version.versionName.take(3)}",
                        style      = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color      = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Version ${version.versionName}",
                            style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLatest) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NeonDepthPink
                            ) {
                                Text(
                                    "LATEST",
                                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color    = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        version.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "#${version.versionCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (version.changes.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && version.changes.isNotEmpty(),
                enter   = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit    = shrinkVertically(tween(180)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    Spacer(Modifier.height(10.dp))

                    version.changes.forEachIndexed { idx, change ->
                        Row(
                            modifier             = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment    = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Numbered bullet
                            Surface(
                                shape = CircleShape,
                                color = NeonDepthPurple.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "${idx + 1}",
                                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color    = NeonDepthPurple,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                change,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Parser ────────────────────────────────────────────────────────────────────
private fun parseChangelog(json: String): List<VersionEntry> {
    val root     = JSONObject(json)
    val arr      = root.optJSONArray("versions") ?: return emptyList()
    val versions = mutableListOf<VersionEntry>()
    for (i in 0 until arr.length()) {
        val obj     = arr.getJSONObject(i)
        val changes = mutableListOf<String>()
        val changesArr = obj.optJSONArray("changes")
        if (changesArr != null) {
            for (j in 0 until changesArr.length()) changes.add(changesArr.getString(j))
        }
        versions.add(
            VersionEntry(
                versionName = obj.optString("version_name", "?"),
                versionCode = obj.optInt("version_code", 0),
                date        = obj.optString("date", ""),
                changes     = changes
            )
        )
    }
    // Sort newest first by version code
    return versions.sortedByDescending { it.versionCode }
}
