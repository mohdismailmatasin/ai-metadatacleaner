package com.example.aimetadatacleaner.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.aimetadatacleaner.R
import com.example.aimetadatacleaner.data.model.ImageInspectionResult
import com.example.aimetadatacleaner.data.model.MetadataCategory
import com.example.aimetadatacleaner.data.model.PrivacyRisk
import com.example.aimetadatacleaner.ui.MainViewModel
import com.example.aimetadatacleaner.ui.components.CategorySectionCard
import com.example.aimetadatacleaner.ui.components.CleaningOptionToggle
import com.example.aimetadatacleaner.ui.components.PrivacyRiskBadge
import com.example.aimetadatacleaner.ui.theme.AmberWarning
import com.example.aimetadatacleaner.ui.theme.CyanAccent
import com.example.aimetadatacleaner.ui.theme.CyanAccentDark
import com.example.aimetadatacleaner.ui.theme.EmeraldSuccess
import com.example.aimetadatacleaner.ui.theme.IndigoLight
import com.example.aimetadatacleaner.ui.theme.RedDanger
import com.example.aimetadatacleaner.ui.theme.Slate400
import com.example.aimetadatacleaner.ui.theme.Slate700
import com.example.aimetadatacleaner.ui.theme.Slate800
import com.example.aimetadatacleaner.ui.theme.Slate900
import com.example.aimetadatacleaner.ui.theme.Slate950

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedUri by viewModel.selectedUri.collectAsStateWithLifecycle()
    val inspection by viewModel.inspectionResult.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()
    val isCleaning by viewModel.isCleaning.collectAsStateWithLifecycle()
    val options by viewModel.cleaningOptions.collectAsStateWithLifecycle()
    val cleanResult by viewModel.cleanResult.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectImage(uri)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            HeaderSection(
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }

        if (isInspecting) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = CyanAccent,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Extracting EXIF & AI parameters...",
                            color = Slate400,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else if (inspection != null) {
            item {
                ImageInspectionCard(inspection = inspection!!)
            }

            // High priority warning if AI prompts or GPS found
            if (inspection!!.hasAiMetadata && inspection!!.rawPromptText != null) {
                item {
                    AiPromptLeakageCard(promptText = inspection!!.rawPromptText!!)
                }
            }

            // Risk Assessment Card
            item {
                RiskAssessmentCard(inspection = inspection!!)
            }

            // Detailed Categories
            val grouped = inspection!!.entries.groupBy { it.category }
            grouped.forEach { (cat, entries) ->
                item {
                    CategorySectionCard(category = cat, entries = entries)
                }
            }

            // Cleaning Options
            item {
                Text(
                    text = "Sanitization Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                CleaningOptionToggle(
                    title = "Strip All Metadata (Recommended)",
                    subtitle = "Eliminate 100% of EXIF, GPS, AI tags, XMP, device info",
                    checked = options.stripAll,
                    onCheckedChange = { viewModel.updateOptions { o -> o.copy(stripAll = it) } },
                    testTag = "toggle_strip_all"
                )
            }

            if (!options.stripAll) {
                item {
                    CleaningOptionToggle(
                        title = "Strip AI Prompts & Models",
                        subtitle = "Remove Midjourney, Stable Diffusion & ComfyUI generation footprints",
                        checked = options.stripAiMetadata,
                        onCheckedChange = { viewModel.updateOptions { o -> o.copy(stripAiMetadata = it) } },
                        testTag = "toggle_strip_ai"
                    )
                }
                item {
                    CleaningOptionToggle(
                        title = "Strip Location & GPS",
                        subtitle = "Wipe precise latitude, longitude, and elevation",
                        checked = options.stripLocationGps,
                        onCheckedChange = { viewModel.updateOptions { o -> o.copy(stripLocationGps = it) } },
                        testTag = "toggle_strip_gps",
                        isWarning = true
                    )
                }
                item {
                    CleaningOptionToggle(
                        title = "Strip Camera & Device ID",
                        subtitle = "Wipe phone model, lens specs, camera serial",
                        checked = options.stripCameraDevice,
                        onCheckedChange = { viewModel.updateOptions { o -> o.copy(stripCameraDevice = it) } },
                        testTag = "toggle_strip_camera"
                    )
                }
                item {
                    CleaningOptionToggle(
                        title = "Strip Timestamps & Dates",
                        subtitle = "Wipe creation and digitized date-time records",
                        checked = options.stripTimestamps,
                        onCheckedChange = { viewModel.updateOptions { o -> o.copy(stripTimestamps = it) } },
                        testTag = "toggle_strip_timestamps"
                    )
                }
            }

            // Clean action button
            item {
                Button(
                    onClick = { viewModel.cleanCurrentImage() },
                    enabled = !isCleaning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("clean_photo_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Slate950
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isCleaning) {
                        CircularProgressIndicator(
                            color = Slate950,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sanitizing Image...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Clean",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Strip & Clean Metadata",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        } else {
            // Empty state hero
            item {
                EmptyStateCard(
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Cleaned result dialog
    cleanResult?.let { result ->
        CleanResultDialog(
            result = result,
            onDismiss = { viewModel.dismissCleanResult() },
            onSaveToGallery = { path -> viewModel.saveCleanedToGallery(path) },
            onShare = {
                viewModel.shareImage(result.cleanedUri, result.cleanedFileName)
            }
        )
    }
}

@Composable
fun HeaderSection(
    onPickPhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f))
                            .border(1.dp, CyanAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI Metadata Cleaner",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "100% On-Device Privacy Shield",
                            fontSize = 12.sp,
                            color = EmeraldSuccess
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onPickPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pick_photo_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanAccent,
                    contentColor = Slate950
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Pick Photo",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Photo to Inspect & Clean", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ImageInspectionCard(inspection: ImageInspectionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = inspection.uri,
                    contentDescription = "Selected image preview",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Slate700, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inspection.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${inspection.mimeType.substringAfter("/") .uppercase()} • ${formatBytes(inspection.fileSizeBytes)}",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    if (inspection.width > 0 && inspection.height > 0) {
                        Text(
                            text = "${inspection.width} × ${inspection.height} px",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    PrivacyRiskBadge(risk = inspection.riskLevel)
                }
            }
        }
    }
}

@Composable
fun AiPromptLeakageCard(promptText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(IndigoLight)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Prompt",
                    tint = IndigoLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Prompt & Model Parameters Detected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = IndigoLight
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "This image contains embedded text prompts or generation configuration. Anyone who downloads the photo can extract your creative prompts and model checkpoints.",
                fontSize = 12.sp,
                color = Slate400,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Slate950)
                    .border(1.dp, Slate800, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = promptText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun RiskAssessmentCard(inspection: ImageInspectionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Risk Assessment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${inspection.entries.size} metadata tags",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            inspection.riskReasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (inspection.riskLevel == PrivacyRisk.SAFE) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Risk indicator",
                        tint = if (inspection.riskLevel == PrivacyRisk.SAFE) EmeraldSuccess else AmberWarning,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = reason,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    onPickPhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.hero_privacy_shield),
                contentDescription = "Privacy Shield Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Strip Digital Footprints & AI Tags",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Photos contain hidden GPS coordinates, phone serials, and AI generation prompts (Midjourney, Stable Diffusion, DALL-E). Inspect and sanitize them instantly.",
                fontSize = 13.sp,
                color = Slate400,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onPickPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("empty_select_photo_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanAccent,
                    contentColor = Slate950
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Pick photo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Select Photo from Device", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
