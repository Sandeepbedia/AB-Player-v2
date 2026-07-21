package com.io.ab.music.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class DogState { QUIET, LISTEN, DRAGGING, FALLING }

/**
 * Holds the pet's dragged position so it survives MiniPlayer hide/show cycles.
 * Create this with [rememberDogPetState] at a level that outlives AnimatedVisibility.
 */
@Stable
class DogPetPositionState {
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
}

@Composable
fun rememberDogPetState(): DogPetPositionState = remember { DogPetPositionState() }

/**
 * FIX: "dog ka falling/dragging/listenToMusic/quiet image frame achhe trah se
 * kaam nhi karta" — every frame used to be a separate AsyncImage load straight
 * from android_asset, re-requested from scratch on EVERY frame swap (up to
 * ~14 times/second for dragging/falling). AsyncImage shows nothing while a
 * request is in flight, so any small hitch (first time a state plays, a GC
 * pause, disk contention) showed up as a blank/flickering frame, and the
 * faster states (dragging 70ms, falling 90ms) visibly stuttered instead of
 * reading as a smooth flip-book animation.
 *
 * Fix: decode every frame of all four states ONCE, up front, into an
 * in-memory Bitmap cache; the per-tick animation loop then just swaps between
 * already-decoded bitmaps — no I/O and no loading gap on the frame boundary,
 * so quiet/listen/dragging/falling all play back smoothly regardless of how
 * fast the state changes. A plain AsyncImage is used only as a first-paint
 * fallback for the handful of frames not yet warm in the cache.
 */
@Composable
fun DogPet(
    isPlaying    : Boolean,
    positionState: DogPetPositionState,
    modifier     : Modifier = Modifier
) {
    val context = LocalContext.current

    val quietFrames    = remember { (1..6).map { "dog/quiet/$it.webp" } }
    val listenFrames   = remember { (1..9).map { "dog/listenToMusic/$it.webp" } }
    val draggingFrames = remember { (1..6).map { "dog/dragging/$it.webp" } }
    val fallingFrames  = remember { (1..6).map { "dog/falling/$it.webp" } }
    val allFramePaths  = remember { quietFrames + listenFrames + draggingFrames + fallingFrames }

    // path -> decoded bitmap. Filled once by the preload effect below, then
    // read (never re-decoded) on every animation tick.
    val bitmapCache = remember { mutableStateMapOf<String, Bitmap>() }

    LaunchedEffect(Unit) {
        val loader = ImageLoader.Builder(context).build()
        allFramePaths.forEach { path ->
            try {
                val request = ImageRequest.Builder(context)
                    .data("file:///android_asset/$path")
                    .allowHardware(false) // need a software Bitmap to cache/reuse directly
                    .build()
                val bmp = (loader.execute(request).drawable as? BitmapDrawable)?.bitmap
                if (bmp != null) bitmapCache[path] = bmp
            } catch (_: Exception) {
                // Skip a bad/missing frame rather than crash the whole pet animation.
            }
        }
    }

    var dogState   by remember { mutableStateOf(DogState.QUIET) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var isFalling  by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (!isDragging && !isFalling) {
            dogState = if (isPlaying) DogState.LISTEN else DogState.QUIET
        }
    }

    LaunchedEffect(dogState) {
        frameIndex = 0
        while (true) {
            val frames = when (dogState) {
                DogState.QUIET    -> quietFrames
                DogState.LISTEN   -> listenFrames
                DogState.DRAGGING -> draggingFrames
                DogState.FALLING  -> fallingFrames
            }
            val delayMs = when (dogState) {
                DogState.LISTEN   -> 80L
                DogState.DRAGGING -> 70L
                DogState.FALLING  -> 90L
                else              -> 200L
            }
            delay(delayMs)
            frameIndex = (frameIndex + 1) % frames.size
            // NOTE: FALLING frames just loop — the physics LaunchedEffect
            // controls when falling ends and resets dogState + isFalling.
        }
    }

    // The pet is rendered 52dp above the card (offset y = -52.dp in the layout).
    // offsetY = 0  → pet is at its natural resting spot, visible just above the card.
    // Positive offsetY → pet moves downward (behind the card = invisible).
    // So the landing target is always Y = 0 (visible on top of mini player).

    // Falling animation:
    // Pet falls from wherever it is DOWN to Y = 0 (just above mini player) and stays.
    // X position is preserved — pet only moves along Y axis.
    LaunchedEffect(isFalling) {
        if (!isFalling) return@LaunchedEffect

        val gravity  = 2800f
        val startY   = positionState.offsetY
        val landingY = 0f   // resting position = visible, just above mini player

        // ── Step 1: Gravity drop with a small overshoot past landing ──────────
        val overshoot  = 30f   // pixels past the landing point for bounce feel
        val fallFloorY = landingY + overshoot

        // Only run gravity if pet is above the floor
        if (startY < fallFloorY) {
            var vy = 0f
            while (positionState.offsetY < fallFloorY) {
                delay(16L)
                vy += gravity * 0.016f
                positionState.offsetY += vy * 0.016f
            }
            positionState.offsetY = fallFloorY
        }

        // Small pause — pet "thuds"
        delay(80L)

        // ── Step 2: Bounce back and settle at Y = 0 (visible above mini player)
        // X position is NOT touched — pet stays at the dropped X position.
        val returnY = Animatable(positionState.offsetY)
        returnY.animateTo(
            targetValue   = landingY,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            )
        ) {
            positionState.offsetY = value
        }
        positionState.offsetY = landingY

        // ── Done falling — return to normal state ─────────────────────────────
        isFalling = false
        dogState  = if (isPlaying) DogState.LISTEN else DogState.QUIET
    }

    val currentFrames = when (dogState) {
        DogState.QUIET    -> quietFrames
        DogState.LISTEN   -> listenFrames
        DogState.DRAGGING -> draggingFrames
        DogState.FALLING  -> fallingFrames
    }
    val safeIndex = frameIndex.coerceIn(0, currentFrames.lastIndex)
    val assetPath = currentFrames[safeIndex]
    val cachedBitmap = bitmapCache[assetPath]

    Box(
        modifier = modifier
            .size(72.dp)
            .offset { IntOffset(positionState.offsetX.roundToInt(), positionState.offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        isFalling  = false
                        dogState   = DogState.DRAGGING
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        positionState.offsetX += dragAmount.x
                        positionState.offsetY += dragAmount.y
                    },
                    onDragEnd = {
                        isDragging = false
                        dogState   = DogState.FALLING
                        isFalling  = true
                    },
                    onDragCancel = {
                        isDragging = false
                        isFalling  = false
                        dogState   = if (isPlaying) DogState.LISTEN else DogState.QUIET
                    }
                )
            }
    ) {
        if (cachedBitmap != null) {
            // Already-decoded frame — swaps instantly, no loading gap.
            Image(
                bitmap             = cachedBitmap.asImageBitmap(),
                contentDescription = "Dog pet",
                modifier           = Modifier.size(72.dp)
            )
        } else {
            // First-paint fallback only, for the brief window before the
            // preload effect above has warmed this particular frame.
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/$assetPath")
                    .crossfade(false)
                    .build(),
                contentDescription = "Dog pet",
                modifier = Modifier.size(72.dp)
            )
        }
    }
}
