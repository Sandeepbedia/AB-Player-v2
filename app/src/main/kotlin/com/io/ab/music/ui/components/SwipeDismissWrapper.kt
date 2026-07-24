package com.io.ab.music.ui.components

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SwipeDismissWrapper(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    statusBarColor: Color = MaterialTheme.colorScheme.surface,
    navigationBarColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    // PERF FIX: dragOffsetY used to be an Animatable updated via coroutineScope.launch { snapTo() }
    // inside onDrag — that launched a BRAND NEW coroutine for every single pointer-move event
    // (60-120/sec while swiping), which is real jank during swipe-to-dismiss on Search, Queue
    // and History screens. Live dragging now just writes a plain snapshot state (instant, no
    // coroutine); a coroutine is only spun up once per gesture-end to animate the settle.
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val screenHeightPx = with(LocalDensity.current) { 1000.dp.toPx() }
    val view = LocalView.current
    val defaultBg = MaterialTheme.colorScheme.background

    // Ensure edge-to-edge is enabled
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsCtrl = WindowCompat.getInsetsController(window, view)
        val systemIsDark = defaultBg.luminance() < 0.5f
        insetsCtrl.isAppearanceLightStatusBars     = !systemIsDark
        insetsCtrl.isAppearanceLightNavigationBars = !systemIsDark
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor     = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            insetsCtrl.isAppearanceLightStatusBars     = !systemIsDark
            insetsCtrl.isAppearanceLightNavigationBars = !systemIsDark
        }
    }

    val dragProgress = (dragOffsetY / 300f).coerceIn(0f, 1f)

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = statusBarColor.copy(alpha = 1f - dragProgress).toArgb()
        window.navigationBarColor = navigationBarColor.copy(alpha = 1f - dragProgress).toArgb()
    }

    fun settleClosed() {
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            dragOffsetY = screenHeightPx
            onDismiss?.invoke()
        }
    }

    fun settleOpen() {
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            dragOffsetY = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffsetY.coerceAtLeast(0f)
                if (onDismiss != null) {
                    alpha = 1f - (dragOffsetY / 700f).coerceIn(0f, 0.45f)
                }
            }
            .pointerInput(onDismiss != null) {
                if (onDismiss == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { settleJob?.cancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // PERF FIX: direct state write, no coroutine launch per pointer move.
                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (dragOffsetY > 150f) settleClosed() else settleOpen()
                    },
                    onDragCancel = { settleOpen() }
                )
            }
    ) {
        content()
    }
}
