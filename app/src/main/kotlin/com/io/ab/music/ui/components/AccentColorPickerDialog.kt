package com.io.ab.music.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

// PRD palette
private val PickerBg   = Color(0xFF0B0F14)
private val PickerCard = Color(0xFF151A23)

/**
 * Small floating RGB color picker — Material 3, dark, Photoshop/Figma style.
 * Saturation-value drag box + vertical hue slider + live preview + HEX/RGB
 * fields + quick actions (Copy / Random / Reset), wrapped in a compact Dialog
 * card (not a full screen) so it drops in over Settings.
 *
 * onColorChange fires continuously while dragging (for a live swatch);
 * onDone fires once, when the user commits with the Done button.
 */
@Composable
fun AccentColorPickerDialog(
    initialHex: String,
    onColorChange: (String) -> Unit,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics   = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    val defaultArgb = remember { AndroidColor.parseColor("#9C27B0") }
    val startArgb = remember(initialHex) {
        runCatching { AndroidColor.parseColor(initialHex) }.getOrDefault(defaultArgb)
    }
    val startHsv = remember(startArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(startArgb, it) }
    }

    var hue        by remember { mutableFloatStateOf(startHsv[0]) } // 0..360
    var saturation by remember { mutableFloatStateOf(startHsv[1]) } // 0..1
    var brightness by remember { mutableFloatStateOf(startHsv[2]) } // 0..1

    val currentArgb by remember {
        derivedStateOf { AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) }
    }
    val currentColor by remember { derivedStateOf { Color(currentArgb) } }
    val hexString by remember {
        derivedStateOf { String.format("#%06X", 0xFFFFFF and currentArgb) }
    }

    var hexInput by remember { mutableStateOf(hexString) }
    LaunchedEffect(hexString) { hexInput = hexString }
    LaunchedEffect(hexString) { onColorChange(hexString) }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = h; saturation = s; brightness = v
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun applyHexInput(text: String) {
        val cleaned = if (text.startsWith("#")) text else "#$text"
        if (cleaned.length == 7) {
            runCatching { AndroidColor.parseColor(cleaned) }.getOrNull()?.let { parsed ->
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(parsed, hsv)
                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            }
        }
    }

    fun applyRgb(r: Int, g: Int, b: Int) {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), hsv)
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
    }

    val r = (currentColor.red * 255).roundToInt()
    val g = (currentColor.green * 255).roundToInt()
    val b = (currentColor.blue * 255).roundToInt()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 340.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PickerBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {

                // ── Header: title · reset · done ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Accent Color",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val hsv = FloatArray(3)
                        AndroidColor.colorToHSV(defaultArgb, hsv)
                        applyHsv(hsv[0], hsv[1], hsv[2])
                    }) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Reset",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(onClick = { onDone(hexString) }) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Color area (SV box) + vertical hue slider ────────────
                Row(modifier = Modifier.fillMaxWidth()) {
                    SaturationValueBox(
                        hue = hue,
                        saturation = saturation,
                        brightness = brightness,
                        modifier = Modifier
                            .weight(1f)
                            .height(170.dp),
                        onDrag = { s, v -> applyHsv(hue, s, v) }
                    )
                    Spacer(Modifier.width(10.dp))
                    HueSlider(
                        hue = hue,
                        modifier = Modifier
                            .width(28.dp)
                            .height(170.dp),
                        onHueChange = { h -> applyHsv(h, saturation, brightness) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Live preview + HEX field ──────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = {
                            hexInput = it
                            applyHexInput(it)
                        },
                        label = { Text("HEX") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = currentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = currentColor,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = currentColor
                        )
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── RGB sliders ────────────────────────────────────────────
                RgbSliderRow("R", r, Color(0xFFFF5252)) { applyRgb(it, g, b) }
                RgbSliderRow("G", g, Color(0xFF4CAF50)) { applyRgb(r, it, b) }
                RgbSliderRow("B", b, Color(0xFF2196F3)) { applyRgb(r, g, it) }

                Spacer(Modifier.height(8.dp))

                // ── Quick actions ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copy HEX",
                        modifier = Modifier.weight(1f)
                    ) { clipboard.setText(AnnotatedString(hexString)) }

                    QuickActionChip(
                        icon = Icons.Rounded.Shuffle,
                        label = "Random",
                        modifier = Modifier.weight(1f)
                    ) {
                        applyHsv(
                            (0..359).random().toFloat(),
                            (40..100).random() / 100f,
                            (60..100).random() / 100f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    brightness: Float,
    modifier: Modifier = Modifier,
    onDrag: (s: Float, v: Float) -> Unit
) {
    val hueColor = remember(hue) { Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))) }

    fun updateFromOffset(pos: Offset, size: IntSize) {
        val s = (pos.x / size.width).coerceIn(0f, 1f)
        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
        onDrag(s, v)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateFromOffset(change.position, size)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> updateFromOffset(pos, size) }
                }
        ) {
            drawRect(color = hueColor)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            val x = saturation * size.width
            val y = (1f - brightness) * size.height
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    modifier: Modifier = Modifier,
    onHueChange: (Float) -> Unit
) {
    // True hue sweep (0-360°) — mathematically consistent with HSV<->RGB so
    // slider position always round-trips to the exact same color.
    val hueColors = remember {
        (0..360 step 30).map { Color(AndroidColor.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
    }

    fun updateFromY(y: Float, height: Int) {
        onHueChange((y / height).coerceIn(0f, 1f) * 360f)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(hueColors))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromY(change.position.y, size.height)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> updateFromY(pos.y, size.height) }
            }
    ) {
        val thumbY = maxHeight * (hue / 360f)
        Box(
            modifier = Modifier
                .offset(y = thumbY - 4.dp)
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

@Composable
private fun RgbSliderRow(label: String, value: Int, trackColor: Color, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.25f)
            )
        )
        Text(
            value.toString(),
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.width(30.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PickerCard)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
