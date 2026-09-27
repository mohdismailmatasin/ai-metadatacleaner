package com.example.aimetadatacleaner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aimetadatacleaner.ui.theme.CyanAccent
import com.example.aimetadatacleaner.ui.theme.EmeraldSuccess
import com.example.aimetadatacleaner.ui.theme.IndigoLight
import com.example.aimetadatacleaner.ui.theme.RedDanger
import com.example.aimetadatacleaner.ui.theme.Slate400
import com.example.aimetadatacleaner.ui.theme.Slate800
import com.example.aimetadatacleaner.ui.theme.Slate900

@Composable
fun PrivacyGuideScreen(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyanAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Guide",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Metadata & Privacy Guide",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "How your hidden photo data can be exposed",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }
                }
            }
        }

        item {
            GuideSectionCard(
                title = "AI Prompts & Model Fingerprints",
                subtitle = "Midjourney, Stable Diffusion, DALL-E & ComfyUI",
                description = "AI image generators embed your original prompt text, negative prompts, seed numbers, CFG scales, and workflow graphs into PNG chunks (tEXt/iTXt) or EXIF UserComment fields. Anyone downloading the raw file can inspect your exact creative recipes.",
                icon = Icons.Default.AutoAwesome,
                accentColor = IndigoLight
            )
        }

        item {
            GuideSectionCard(
                title = "Precise GPS Coordinates",
                subtitle = "Exposes home, school, and work addresses",
                description = "Smartphones and modern cameras automatically embed GPS latitude and longitude tags into photo headers. When shared on websites or chat apps that don't scrub metadata, anyone can determine the exact geographic location where the picture was captured down to a few feet.",
                icon = Icons.Default.LocationOff,
                accentColor = RedDanger
            )
        }

        item {
            GuideSectionCard(
                title = "Hardware Serial & Camera Footprint",
                subtitle = "Cross-platform device identification",
                description = "EXIF headers frequently store camera serial numbers, lens configurations, firmware versions, and sub-second timestamps. These values act as a unique digital fingerprint connecting anonymous uploads across different platforms.",
                icon = Icons.Default.PhonelinkLock,
                accentColor = CyanAccent
            )
        }

        item {
            GuideSectionCard(
                title = "C2PA & Content Credentials",
                subtitle = "Cryptographic provenance manifests",
                description = "Emerging standards (C2PA) attach cryptographic manifests indicating whether an image was created or modified by AI. MetaClean allows you to sanitize these markers to maintain clean, unencumbered media files.",
                icon = Icons.Default.VpnKey,
                accentColor = EmeraldSuccess
            )
        }

        item {
            GuideSectionCard(
                title = "100% On-Device Processing",
                subtitle = "Zero server uploads, zero trackers",
                description = "All metadata inspection, EXIF stripping, and pixel re-encoding occur purely inside local phone memory. Your photos never leave your device and are never sent to external servers.",
                icon = Icons.Default.Security,
                accentColor = EmeraldSuccess
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun GuideSectionCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = Slate400,
                lineHeight = 18.sp
            )
        }
    }
}
