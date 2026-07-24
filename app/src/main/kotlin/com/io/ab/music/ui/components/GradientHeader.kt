package com.io.ab.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Header container that used to draw its own gradient, but now simply acts
 * as a transparent layout wrapper.  The single global gradient in
 * [com.io.ab.music.ui.navigation.ABMusicNavGraph] covers the entire
 * status-bar → tab-bar → content area, so individual screens no longer
 * need their own gradient layers (which used to cause visible "banding"
 * at the tab-bar / content boundary).
 *
 * This composable is kept so call-sites don't break — it simply wraps
 * its [content] in a full-width Box.
 */
@Composable
fun GradientHeaderBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        content()
    }
}

/**
 * Previously drew a 500 dp tall gradient overlay behind page content.
 * Now a no-op — the global gradient in NavGraph handles this.
 */
@Composable
fun PageGradientBackground() {
    // No-op: global gradient in NavGraph is the single source of truth.
}
