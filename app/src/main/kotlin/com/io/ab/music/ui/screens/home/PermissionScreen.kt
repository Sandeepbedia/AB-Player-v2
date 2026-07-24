package com.io.ab.music.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    // Pulse animation disabled for performance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 32.dp)
        ) {
            // Animated icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.LibraryMusic, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "Welcome to AB Player",
                style     = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color     = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "To play your music, AB Player needs access to your audio files stored on this device.",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // Permission benefits
            listOf(
                Icons.Rounded.MusicNote  to "Play all your local music",
                Icons.Rounded.Album      to "Browse by albums & artists",
                Icons.Rounded.CloudOff   to "Works 100% offline • No ads"
            ).forEach { (icon, text) ->
                Row(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment= Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick  = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.FolderOpen, null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Grant Access",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ))
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Your music stays private on your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
