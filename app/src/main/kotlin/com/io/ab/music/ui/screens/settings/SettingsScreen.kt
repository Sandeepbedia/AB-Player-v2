package com.io.ab.music.ui.screens.settings
import com.io.ab.music.ui.components.MiniPlayerBottomSpacer

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.ui.theme.ThemeMode
import com.io.ab.music.ui.theme.LocalThemeMode

import com.io.ab.music.ui.viewmodel.PlayerViewModel
import com.io.ab.music.ui.viewmodel.SettingsViewModel
import com.io.ab.music.ui.viewmodel.UpdateInfo
import com.io.ab.music.ui.navigation.LocalMiniPlayerScrollState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel      : SettingsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel   = hiltViewModel(),
    onChangelogHistory: (() -> Unit)?  = null
) {
    val uiState  by viewModel.uiState.collectAsState()
    val context   = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope     = rememberCoroutineScope()

    // FIX: MiniPlayer never hid while scrolling this tab — wire it up the same
    // way HomeScreen/VideoScreen do.
    val settingsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val miniPlayerScroll  = LocalMiniPlayerScrollState.current
    var lastScrollIdx     by remember { mutableIntStateOf(0) }
    var lastScrollOffset  by remember { mutableIntStateOf(0) }
    LaunchedEffect(settingsListState) {
        snapshotFlow { settingsListState.firstVisibleItemIndex to settingsListState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                val delta = when {
                    idx > lastScrollIdx -> -100f
                    idx < lastScrollIdx ->  100f
                    else -> (lastScrollOffset - offset).toFloat()
                }
                miniPlayerScroll.onScrollDelta(delta)
                lastScrollIdx    = idx
                lastScrollOffset = offset
            }
    }

    // Manual wallpaper theme — system Photo Picker, no storage permission needed
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.setWallpaperImage(it) }
    }

    // Show snackbar for one-shot messages (rescan done, cache cleared, etc.)
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.onSnackbarShown()
    }

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            state          = settingsListState,
            // Performance: stable content avoids unnecessary recompositions
        ) {

            item(key = "settings_header") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "App preferences",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Developer Card ─────────────────────────────────────────────────
            item(key = "dev_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape  = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "S",
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AB Player",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "by Sandeep Bedia",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            Text(
                                "Version 2.7.7 (Codename 427)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { openUrl("https://github.com/Sandeepbedia") },
                            shape   = CircleShape
                        ) {
                            Icon(Icons.Rounded.Code, "GitHub", modifier = Modifier.size(18.dp))
                        }
                    }
                }

            }

            // ── APPEARANCE ────────────────────────────────────────────────────
            item(key = "sec_appearance") { SettingsSectionHeader("Appearance") }

            item(key = "theme") {
                SettingsItem(
                    icon     = Icons.Rounded.DarkMode,
                    label    = "Theme",
                    sublabel = when (uiState.themeMode) {
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED          -> "AMOLED Purple"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_CYAN     -> "AMOLED Cyan"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_PINK     -> "AMOLED Pink"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_GOLD     -> "AMOLED Gold"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_GREEN    -> "AMOLED Green"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_ORANGE   -> "AMOLED Orange"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_MIDNIGHT -> "AMOLED Midnight"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_VIOLET   -> "AMOLED Violet"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_TURQUOISE-> "AMOLED Turquoise"
                        com.io.ab.music.ui.theme.ThemeMode.AMOLED_BW       -> "AMOLED B&W"
                        com.io.ab.music.ui.theme.ThemeMode.LIGHT_LAVENDER  -> "Light Lavender"
                        com.io.ab.music.ui.theme.ThemeMode.LIGHT_MINT      -> "Light Mint"
                        com.io.ab.music.ui.theme.ThemeMode.LIGHT_CORAL     -> "Light Coral"
                        com.io.ab.music.ui.theme.ThemeMode.LIGHT_SUNRISE   -> "Light Sunrise"
                        com.io.ab.music.ui.theme.ThemeMode.LIGHT_OCEAN     -> "Light Ocean"
                        else -> uiState.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    onClick  = { viewModel.showThemeDialog() }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item(key = "dynamic_color") {
                    SettingsSwitchItem(
                        icon     = Icons.Rounded.Palette,
                        label    = "Dynamic Colors",
                        sublabel = "Use wallpaper colors (Material You)",
                        checked  = uiState.dynamicColor,
                        onToggle = { viewModel.setDynamicColor(it) }
                    )
                }
            }

            item(key = "accent") {
                SettingsItem(
                    icon     = Icons.Rounded.ColorLens,
                    label    = "Accent Color",
                    sublabel = "Customize primary color",
                    trailing = {
                        val swatch = runCatching { Color(android.graphics.Color.parseColor(uiState.accentColor)) }
                            .getOrDefault(MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(swatch))
                    },
                    onClick  = { viewModel.showAccentDialog() }
                )
            }

            item(key = "wallpaper_theme") {
                SettingsSwitchItem(
                    icon     = Icons.Rounded.Wallpaper,
                    label    = "Manual Wallpaper",
                    sublabel = if (uiState.wallpaperEnabled) "On — tap to adjust blur & image"
                               else "Use a custom photo as the app background",
                    checked  = uiState.wallpaperEnabled,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (uiState.wallpaperPath.isBlank()) {
                                wallpaperPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                viewModel.setWallpaperEnabled(true)
                            }
                        } else {
                            viewModel.setWallpaperEnabled(false)
                        }
                    }
                )
            }
            if (uiState.wallpaperEnabled) {
                item(key = "wallpaper_adjust") {
                    SettingsItem(
                        icon     = Icons.Rounded.Tune,
                        label    = "Wallpaper Settings",
                        sublabel = "Change image, blur amount & overlay",
                        onClick  = { viewModel.showWallpaperDialog() }
                    )
                }
            }

            // Dog pet companion toggle
            item(key = "pet_companion") {
                SettingsSwitchItem(
                    icon     = Icons.Rounded.Pets,
                    label    = "Dog Companion",
                    sublabel = if (uiState.petEnabled) "🐾 Sitting on your music player" else "Pet is resting",
                    checked  = uiState.petEnabled,
                    onToggle = { viewModel.setPetEnabled(it) }
                )
            }

            // Landscape mode bottom mini player toggle
            item(key = "landscape_mini_player") {
                SettingsSwitchItem(
                    icon     = Icons.Rounded.ScreenRotation,
                    label    = "Landscape Mini Player",
                    sublabel = if (uiState.landscapeMiniPlayerEnabled)
                        "Shown at the bottom in landscape mode"
                    else
                        "Hidden in landscape mode",
                    checked  = uiState.landscapeMiniPlayerEnabled,
                    onToggle = { viewModel.setLandscapeMiniPlayerEnabled(it) }
                )
            }

            // ── PLAYBACK ──────────────────────────────────────────────────────
            item(key = "sec_playback") { SettingsSectionHeader("Playback") }

            item(key = "audio_focus") {
                SettingsSwitchItem(
                    icon     = Icons.Rounded.VolumeUp,
                    label    = "Audio Focus",
                    sublabel = "Pause when another app plays audio",
                    checked  = uiState.audioFocus,
                    onToggle = { viewModel.setAudioFocus(it) }
                )
            }

            item(key = "equalizer") {
                SettingsItem(
                    icon     = Icons.Rounded.Equalizer,
                    label    = "Equalizer",
                    sublabel = "Adjust audio frequencies",
                    onClick  = { viewModel.showEqDialog() }
                )
            }

            // Sleep Timer — shows remaining time with live countdown
            item(key = "sleep_timer") {
                val remaining = uiState.sleepTimerSeconds
                val sublabel = when {
                    remaining <= 0 -> "Off — tap to set"
                    remaining < 60 -> "${remaining}s remaining"
                    else           -> "${remaining / 60}m ${remaining % 60}s remaining"
                }
                SettingsItem(
                    icon     = Icons.Rounded.Timer,
                    label    = "Sleep Timer",
                    sublabel = sublabel,
                    trailing = {
                        if (remaining > 0) {
                            // Cancel button shown when timer is active
                            IconButton(
                                onClick  = { viewModel.cancelSleepTimer() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Cancel,
                                    "Cancel timer",
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(Icons.Rounded.ChevronRight, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick  = { viewModel.showSleepTimerDialog() }
                )
            }

            // ── LIBRARY ───────────────────────────────────────────────────────
            item(key = "sec_library") { SettingsSectionHeader("Library") }

            item(key = "rescan") {
                SettingsItem(
                    icon     = Icons.Rounded.Refresh,
                    label    = "Rescan Library",
                    sublabel = if (uiState.isRescanning) "Scanning device…" else "Scan device for new music files",
                    trailing = {
                        if (uiState.isRescanning) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.ChevronRight, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick  = { viewModel.rescanLibrary() }
                )
            }

            item(key = "subfolders") {
                SettingsSwitchItem(
                    icon     = Icons.Rounded.FolderOpen,
                    label    = "Include Subfolders",
                    sublabel = "Scan music in nested folders",
                    checked  = uiState.includeSubfolders,
                    onToggle = { viewModel.setIncludeSubfolders(it) }
                )
            }

            item(key = "clear_cache") {
                SettingsItem(
                    icon     = Icons.Rounded.CleaningServices,
                    label    = "Clear Cache",
                    sublabel = if (uiState.isClearingCache) "Clearing…" else "Free up space used by artwork cache",
                    trailing = {
                        if (uiState.isClearingCache) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.ChevronRight, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick  = { viewModel.clearCache() }
                )
            }

            // ── NOTIFICATIONS ─────────────────────────────────────────────────
            item(key = "sec_notifications") { SettingsSectionHeader("Notifications") }

            item(key = "notif") {
                SettingsSwitchItem(
                    icon     = Icons.Rounded.Notifications,
                    label    = "Playback Notification",
                    sublabel = "Show media controls in notification shade",
                    checked  = uiState.playbackNotification,
                    onToggle = { viewModel.setPlaybackNotification(it) }
                )
            }

            // ── ABOUT ─────────────────────────────────────────────────────────
            item(key = "sec_about") { SettingsSectionHeader("About") }

            item(key = "version") {
                SettingsItem(
                    icon     = Icons.Rounded.Info,
                    label    = "Version",
                    sublabel = "2.7.7 (Codename 427)",
                    onClick  = {}
                )
            }

            item(key = "check_update") {
                val updateInfo       = uiState.updateInfo
                val isChecking       = uiState.isCheckingUpdate
                val checked          = uiState.updateChecked
                val updateAvailable  = updateInfo != null
                val updateIcon = when {
                    isChecking       -> Icons.Rounded.Autorenew
                    updateAvailable  -> Icons.Rounded.SystemUpdate
                    checked          -> Icons.Rounded.CheckCircle
                    else             -> Icons.Rounded.Update
                }
                val updateIconTint = when {
                    updateAvailable -> MaterialTheme.colorScheme.primary
                    checked         -> Color(0xFF4CAF50)
                    else            -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val updateSublabel = when {
                    isChecking      -> "Checking for updates…"
                    updateAvailable -> "v${updateInfo!!.versionName} available — tap to update"
                    checked         -> "App is up to date ✓"
                    else            -> "Tap to check for latest version"
                }
                // Spinner rotation animation disabled for performance
                Surface(
                    onClick  = { if (!isChecking) viewModel.checkForUpdate(force = true) },
                    modifier = Modifier.fillMaxWidth(),
                    color    = Color.Transparent
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = updateIcon,
                            contentDescription = null,
                            tint               = updateIconTint,
                            modifier           = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = 0f }
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Check for Update",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color      = if (updateAvailable) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                updateSublabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    updateAvailable -> MaterialTheme.colorScheme.primary.copy(0.8f)
                                    checked         -> Color(0xFF4CAF50).copy(0.8f)
                                    else            -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            item(key = "changelog_history") {
                SettingsItem(
                    icon     = Icons.Rounded.History,
                    label    = "Changelog History",
                    sublabel = "See all version update notes",
                    onClick  = { onChangelogHistory?.invoke() }
                )
            }

            item(key = "privacy") {
                SettingsItem(
                    icon     = Icons.Rounded.PrivacyTip,
                    label    = "Privacy Policy",
                    sublabel = "No tracking • No ads • Offline only",
                    onClick  = {}
                )
            }

            item(key = "rate") {
                SettingsItem(
                    icon     = Icons.Rounded.Star,
                    label    = "Rate the App",
                    sublabel = "Love AB Player? Leave a review!",
                    onClick  = {}
                )
            }

            item(key = "instagram") {
                SettingsItem(
                    icon     = Icons.Rounded.Share,
                    label    = "Instagram",
                    sublabel = "@sandee_bedia_08 • Follow the developer",
                    onClick  = { openUrl("https://www.instagram.com/sandee_bedia_08?igsh=aW1mZDk4ODdsN25t") }
                )
            }

            item(key = "contact") {
                SettingsItem(
                    icon     = Icons.Rounded.Send,
                    label    = "Contact Developer",
                    sublabel = "Reach out via Telegram",
                    onClick  = { openUrl("https://t.me/Infinity_384") }
                )
            }

            // ── DEVELOPER FOOTER ──────────────────────────────────────────────
            item(key = "dev_footer") {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
            }

            item(key = "bottom_pad") { MiniPlayerBottomSpacer() }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (uiState.showThemeDialog) {
        ThemePickerDialog(
            current   = uiState.themeMode,
            onSelect  = { viewModel.setThemeMode(it) },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
    if (uiState.showSleepTimerDialog) {
        SleepTimerDialog(
            currentSeconds = uiState.sleepTimerSeconds,
            onSelect       = { minutes -> viewModel.setSleepTimer(minutes) },
            onDismiss      = { viewModel.dismissDialog() }
        )
    }
    if (uiState.showEqDialog) {
        val eqLevels by playerViewModel.eqBandLevels.collectAsState()
        SettingsEqDialog(
            initialLevels = eqLevels.toList(),
            onApply       = { levels ->
                levels.forEachIndexed { i, db -> playerViewModel.setEqBand(i, db) }
            },
            onDismiss     = { viewModel.dismissDialog() }
        )
    }
    if (uiState.showWallpaperDialog) {
        WallpaperSettingsDialog(
            blur        = uiState.wallpaperBlur,
            dim         = uiState.wallpaperDim,
            onBlurChange= { viewModel.setWallpaperBlur(it) },
            onDimChange = { viewModel.setWallpaperDim(it) },
            onChangeImage = {
                wallpaperPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemove    = { viewModel.clearWallpaper() },
            onDismiss   = { viewModel.dismissDialog() }
        )
    }
    if (uiState.showAccentDialog) {
        com.io.ab.music.ui.components.AccentColorPickerDialog(
            initialHex    = uiState.accentColor,
            onColorChange = { viewModel.setAccentColorPreview(it) },
            onDone        = {
                viewModel.setAccentColor(it)
                viewModel.dismissDialog()
            },
            onDismiss     = { viewModel.dismissDialog() }
        )
    }
}

// ── Social Media Card ─────────────────────────────────────────────────────────
@Composable
fun SocialMediaCard(
    icon         : ImageVector,
    platform     : String,
    handle       : String,
    description  : String,
    gradientStart: Color,
    gradientEnd  : Color,
    onClick      : () -> Unit
) {
    val themeMode = LocalThemeMode.current
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Gradient icon box
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(gradientStart, gradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    platform,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = gradientStart
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.OpenInNew, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
        } // end gradient Box
    }
}

// ── Developer Footer ──────────────────────────────────────────────────────────
@Composable
fun DeveloperFooter(versionName: String) {
    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(
            color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text       = title.uppercase(),
        style      = MaterialTheme.typography.labelSmall,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp, end = 16.dp)
    )
}

@Composable
fun SettingsItem(
    icon    : ImageVector,
    label   : String,
    sublabel: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick : () -> Unit
) {
    Surface(
        onClick  = onClick,
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                Text(sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (trailing != null) trailing()
            else Icon(Icons.Rounded.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(
        modifier  = Modifier.padding(start = 70.dp, end = 16.dp),
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        thickness = 0.5.dp
    )
}

@Composable
fun SettingsSwitchItem(
    icon    : ImageVector,
    label   : String,
    sublabel: String,
    checked : Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick  = { onToggle(!checked) },
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                Text(sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
    HorizontalDivider(
        modifier  = Modifier.padding(start = 70.dp, end = 16.dp),
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        thickness = 0.5.dp
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────
@Composable
fun WallpaperSettingsDialog(
    blur         : Float,
    dim          : Float,
    onBlurChange : (Float) -> Unit,
    onDimChange  : (Float) -> Unit,
    onChangeImage: () -> Unit,
    onRemove     : () -> Unit,
    onDismiss    : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Wallpaper, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Wallpaper Theme", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Manually control your own wallpaper behind the app — adjust blur and overlay darkness so text stays readable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Blur", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${blur.toInt()}dp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value         = blur,
                        onValueChange = onBlurChange,
                        valueRange    = 0f..40f
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Overlay Darkness", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${(dim * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value         = dim,
                        onValueChange = onDimChange,
                        valueRange    = 0f..0.85f
                    )
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Text(
                        "Blur needs Android 12+. On this device the image shows sharp; use overlay darkness instead.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick  = onChangeImage,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Change Image") }
                TextButton(
                    onClick  = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove Wallpaper") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun ThemePickerDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Choose Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text  = {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                ThemeMode.values().forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM            -> "System"
                        ThemeMode.LIGHT             -> "Light"
                        ThemeMode.DARK              -> "Dark"
                        ThemeMode.AMOLED            -> "AMOLED — Purple"
                        ThemeMode.AMOLED_CYAN       -> "AMOLED — Cyan"
                        ThemeMode.AMOLED_PINK       -> "AMOLED — Pink"
                        ThemeMode.AMOLED_GOLD       -> "AMOLED — Gold"
                        ThemeMode.AMOLED_GREEN      -> "AMOLED — Green"
                        ThemeMode.AMOLED_ORANGE     -> "AMOLED — Orange"
                        ThemeMode.AMOLED_MIDNIGHT   -> "AMOLED — Midnight"
                        ThemeMode.AMOLED_VIOLET     -> "AMOLED — Violet"
                        ThemeMode.AMOLED_TURQUOISE  -> "AMOLED — Turquoise"
                        ThemeMode.AMOLED_BW         -> "AMOLED — Black & White"
                        ThemeMode.AMOLED_NEON_DEPTH -> "AMOLED — Neon Depth"
                        ThemeMode.LIGHT_LAVENDER    -> "Light — Lavender"
                        ThemeMode.LIGHT_MINT        -> "Light — Mint"
                        ThemeMode.LIGHT_CORAL       -> "Light — Coral"
                        ThemeMode.LIGHT_SUNRISE     -> "Light — Sunrise"
                        ThemeMode.LIGHT_OCEAN       -> "Light — Ocean"
                    }
                    val sublabel = when (mode) {
                        ThemeMode.SYSTEM            -> "Follow system setting"
                        ThemeMode.LIGHT             -> "Always bright & light"
                        ThemeMode.DARK              -> "Always dark"
                        ThemeMode.AMOLED            -> "True black · BB86FC purple"
                        ThemeMode.AMOLED_CYAN       -> "True black · 00E5FF cyan"
                        ThemeMode.AMOLED_PINK       -> "True black · FF4081 pink"
                        ThemeMode.AMOLED_GOLD       -> "True black · FFD54F gold"
                        ThemeMode.AMOLED_GREEN      -> "True black · 69FF47 matrix"
                        ThemeMode.AMOLED_ORANGE     -> "True black · FF6D00 sunset"
                        ThemeMode.AMOLED_MIDNIGHT   -> "True black · 7EB8FF midnight navy"
                        ThemeMode.AMOLED_VIOLET     -> "True black · 9D4EDD vivid violet"
                        ThemeMode.AMOLED_TURQUOISE  -> "True black · 26C6DA turquoise"
                        ThemeMode.AMOLED_BW         -> "True black · Pure white · No colour"
                        ThemeMode.AMOLED_NEON_DEPTH -> "True black · 8B5CF6 purple → EC4899 pink"
                        ThemeMode.LIGHT_LAVENDER    -> "Pastel lavender · soft purple"
                        ThemeMode.LIGHT_MINT        -> "Pastel mint · fresh green"
                        ThemeMode.LIGHT_CORAL       -> "Pastel coral · warm pink"
                        ThemeMode.LIGHT_SUNRISE     -> "Pastel sunrise · golden orange"
                        ThemeMode.LIGHT_OCEAN       -> "Pastel ocean · sky blue"
                    }
                    val accentDot = when (mode) {
                        ThemeMode.AMOLED            -> androidx.compose.ui.graphics.Color(0xFFBB86FC)
                        ThemeMode.AMOLED_CYAN       -> androidx.compose.ui.graphics.Color(0xFF00E5FF)
                        ThemeMode.AMOLED_PINK       -> androidx.compose.ui.graphics.Color(0xFFFF4081)
                        ThemeMode.AMOLED_GOLD       -> androidx.compose.ui.graphics.Color(0xFFFFD54F)
                        ThemeMode.AMOLED_GREEN      -> androidx.compose.ui.graphics.Color(0xFF69FF47)
                        ThemeMode.AMOLED_ORANGE     -> androidx.compose.ui.graphics.Color(0xFFFF6D00)
                        ThemeMode.AMOLED_MIDNIGHT   -> androidx.compose.ui.graphics.Color(0xFF7EB8FF)
                        ThemeMode.AMOLED_VIOLET     -> androidx.compose.ui.graphics.Color(0xFF9D4EDD)
                        ThemeMode.AMOLED_TURQUOISE  -> androidx.compose.ui.graphics.Color(0xFF26C6DA)
                        ThemeMode.AMOLED_NEON_DEPTH -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
                        ThemeMode.LIGHT_LAVENDER    -> androidx.compose.ui.graphics.Color(0xFF7B61FF)
                        ThemeMode.LIGHT_MINT        -> androidx.compose.ui.graphics.Color(0xFF00C896)
                        ThemeMode.LIGHT_CORAL       -> androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                        ThemeMode.LIGHT_SUNRISE     -> androidx.compose.ui.graphics.Color(0xFFFF9A3C)
                        ThemeMode.LIGHT_OCEAN       -> androidx.compose.ui.graphics.Color(0xFF0EA5E9)
                        else -> null
                    }
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(mode); onDismiss() }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mode, onClick = { onSelect(mode); onDismiss() })
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (current == mode) FontWeight.Bold else FontWeight.Normal,
                                color      = if (current == mode) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.onSurface)
                            Text(sublabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (accentDot != null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(accentDot, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun SleepTimerDialog(
    currentSeconds: Int,
    onSelect      : (Int) -> Unit,
    onDismiss     : () -> Unit
) {
    val options = listOf(5 to "5 minutes", 15 to "15 minutes", 30 to "30 minutes",
                         60 to "1 hour", 90 to "90 minutes")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Active timer indicator
                if (currentSeconds > 0) {
                    Surface(
                        color  = MaterialTheme.colorScheme.primaryContainer,
                        shape  = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Timer, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Text(
                                text  = "Active: ${currentSeconds / 60}m ${currentSeconds % 60}s left",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedButton(onClick = { onSelect(0); onDismiss() },
                    modifier = Modifier.fillMaxWidth()) { Text("Turn Off") }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                options.forEach { (min, label) ->
                    TextButton(onClick = { onSelect(min); onDismiss() },
                        modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsEqDialog(
    initialLevels : List<Float> = emptyList(),
    onApply       : (List<Float>) -> Unit = {},
    onDismiss     : () -> Unit
) {
    val bands  = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
    val levels = remember(initialLevels) {
        if (initialLevels.size == bands.size)
            initialLevels.map { mutableFloatStateOf(it) }
        else
            bands.map { mutableFloatStateOf(0f) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Equalizer, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Equalizer", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bands.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label,
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(48.dp),
                            color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value         = levels[i].floatValue,
                            onValueChange = { levels[i].floatValue = it },
                            valueRange    = -10f..10f,
                            modifier      = Modifier.weight(1f)
                        )
                        Text(
                            "${levels[i].floatValue.toInt()}dB",
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(38.dp),
                            color    = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // Preset chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Flat", "Bass", "Vocal", "Classical").forEach { preset ->
                        AssistChip(onClick = {
                            when (preset) {
                                "Flat"      -> levels.forEach { it.floatValue = 0f }
                                "Bass"      -> { levels[0].floatValue = 8f;  levels[1].floatValue = 5f
                                                 levels[2].floatValue = 0f;  levels[3].floatValue = -2f; levels[4].floatValue = -2f }
                                "Vocal"     -> { levels[0].floatValue = -3f; levels[1].floatValue = 0f
                                                 levels[2].floatValue = 4f;  levels[3].floatValue = 5f;  levels[4].floatValue = 2f }
                                "Classical" -> { levels[0].floatValue = 4f;  levels[1].floatValue = 3f
                                                 levels[2].floatValue = -2f; levels[3].floatValue = 2f;  levels[4].floatValue = 4f }
                            }
                        }, label = { Text(preset, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(levels.map { it.floatValue }); onDismiss() },
                shape   = RoundedCornerShape(12.dp)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
