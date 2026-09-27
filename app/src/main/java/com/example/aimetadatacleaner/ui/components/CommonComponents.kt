package com.example.aimetadatacleaner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aimetadatacleaner.data.model.MetadataCategory
import com.example.aimetadatacleaner.data.model.MetadataEntry
import com.example.aimetadatacleaner.data.model.PrivacyRisk
import com.example.aimetadatacleaner.ui.theme.AmberWarning
import com.example.aimetadatacleaner.ui.theme.CyanAccent
import com.example.aimetadatacleaner.ui.theme.EmeraldSuccess
import com.example.aimetadatacleaner.ui.theme.IndigoLight
import com.example.aimetadatacleaner.ui.theme.RedDanger
import com.example.aimetadatacleaner.ui.theme.Slate400
import com.example.aimetadatacleaner.ui.theme.Slate700
import com.example.aimetadatacleaner.ui.theme.Slate800
import com.example.aimetadatacleaner.ui.theme.Slate900

@Composable
fun PrivacyRiskBadge(
    risk: PrivacyRisk,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (risk) {
        PrivacyRisk.HIGH -> Triple(RedDanger.copy(alpha = 0.18f), RedDanger, Icons.Default.Warning)
        PrivacyRisk.MEDIUM -> Triple(AmberWarning.copy(alpha = 0.18f), AmberWarning, Icons.Default.Warning)
        PrivacyRisk.LOW -> Triple(CyanAccent.copy(alpha = 0.18f), CyanAccent, Icons.Default.Security)
        PrivacyRisk.SAFE -> Triple(EmeraldSuccess.copy(alpha = 0.18f), EmeraldSuccess, Icons.Default.CheckCircle)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = risk.label,
            tint = textColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = risk.label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CategorySectionCard(
    category: MetadataCategory,
    entries: List<MetadataEntry>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    val icon: ImageVector = when (category) {
        MetadataCategory.AI_PROVENANCE -> Icons.Default.AutoAwesome
        MetadataCategory.LOCATION -> Icons.Default.LocationOn
        MetadataCategory.CAMERA_DEVICE -> Icons.Default.PhotoCamera
        MetadataCategory.TIMESTAMPS_FILE -> Icons.Default.Schedule
        MetadataCategory.AUTHOR_SYSTEM -> Icons.Default.Lock
    }

    val accentColor: Color = when (category) {
        MetadataCategory.AI_PROVENANCE -> IndigoLight
        MetadataCategory.LOCATION -> RedDanger
        MetadataCategory.CAMERA_DEVICE -> CyanAccent
        MetadataCategory.TIMESTAMPS_FILE -> AmberWarning
        MetadataCategory.AUTHOR_SYSTEM -> Slate400
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
                            contentDescription = category.displayName,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = category.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${entries.size} field${if (entries.size > 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Slate400
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    entries.forEach { entry ->
                        MetadataRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataRow(entry: MetadataEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (entry.isSensitive) RedDanger.copy(alpha = 0.08f) else Slate800.copy(alpha = 0.35f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.key,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (entry.isSensitive) RedDanger else CyanAccent
            )
            if (entry.isSensitive) {
                Text(
                    text = "SENSITIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedDanger
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = entry.value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun CleaningOptionToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate900)
            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isWarning) AmberWarning else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Slate400,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyanAccent,
                checkedTrackColor = CyanAccent.copy(alpha = 0.35f),
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate700
            )
        )
    }
}
