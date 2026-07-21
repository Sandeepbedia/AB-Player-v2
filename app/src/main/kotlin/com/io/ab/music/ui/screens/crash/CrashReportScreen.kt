package com.io.ab.music.ui.screens.crash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.io.ab.music.util.CrashReportManager
import java.io.File

/**
 * Full-screen crash report shown right after the app restarts from a crash.
 * Lets the user see the whole log and reach the developer on Telegram
 * (https://t.me/Infinity_384) directly, with the log file ready to send.
 *
 * FIX: header content (title + subtitle) used to sit directly under
 * `Modifier.fillMaxSize()` with a plain `.padding(20.dp)`, so on edge-to-edge
 * devices the phone's status bar (clock/battery/notification icons) painted
 * straight over the "App Crashed" title. Added `.statusBarsPadding()` on the
 * header and `.navigationBarsPadding()` on the action column so both system
 * bars are respected. Also gave the screen a visual redesign: a gradient
 * header with an icon badge instead of a bare icon + text row, a labeled
 * bordered log console, and consistent spacing between the action buttons.
 */
@Composable
fun CrashReportScreen(
    logFile  : File,
    onDismiss: () -> Unit
) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val logText = remember(logFile) { runCatching { logFile.readText() }.getOrDefault("(could not read crash log)") }
    val logLineCount = remember(logText) { logText.lineSequence().count() }

    val bgColor       = Color(0xFF0B0B0F)
    val consoleColor  = Color(0xFF101018)
    val errorRed      = Color(0xFFFF5252)
    val telegramBlue  = Color(0xFF2AABEE)

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(
                        Brush.verticalGradient(
                            listOf(errorRed.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(errorRed.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, errorRed.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.BugReport, null, tint = errorRed, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "App Crashed",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "$logLineCount lines captured",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sorry about that. Here's exactly what happened — you can message " +
                    "the developer on Telegram directly and send this log so it gets fixed.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ── Log viewer ──────────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Text(
                    "CRASH LOG",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(consoleColor, RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text       = logText,
                        color      = Color(0xFF7CFF9E),
                        fontSize   = 11.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        overflow   = TextOverflow.Clip,
                        modifier   = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
            }

            // ── Actions ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {

                // Step 1 — open the developer's Telegram chat directly.
                Button(
                    onClick  = { CrashReportManager.openDeveloperChat(context) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                ) {
                    Icon(Icons.Rounded.Send, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Message Developer (@Infinity_384)")
                }

                Spacer(Modifier.height(10.dp))

                // Step 2 — hand the .txt to Telegram so it can be sent in the chat.
                OutlinedButton(
                    onClick  = { CrashReportManager.sendCrashFileViaTelegram(context, logFile) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Crash File via Telegram")
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick  = { CrashReportManager.shareManually(context, logFile) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    OutlinedButton(
                        onClick  = { clipboard.setText(AnnotatedString(logText)) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }

                Spacer(Modifier.height(6.dp))

                TextButton(
                    onClick  = {
                        CrashReportManager.deleteCrashLog(logFile)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss & Continue to App", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}
