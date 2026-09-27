package com.example.aimetadatacleaner.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.aimetadatacleaner.data.model.ImageInspectionResult
import com.example.aimetadatacleaner.data.model.MetadataCategory
import com.example.aimetadatacleaner.data.model.MetadataEntry
import com.example.aimetadatacleaner.data.model.PrivacyRisk
import java.io.InputStream
import java.nio.charset.StandardCharsets

object MetadataExtractor {

    fun inspectImage(context: Context, uri: Uri): ImageInspectionResult {
        var fileName = "image"
        var fileSize: Long = 0
        var mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

        // Query file metadata from ContentResolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Determine image dimensions
        var width = 0
        var height = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                width = options.outWidth
                height = options.outHeight
                if (options.outMimeType != null) {
                    mimeType = options.outMimeType
                }
            }
        } catch (_: Exception) {
        }

        val entries = mutableListOf<MetadataEntry>()
        val riskReasons = mutableListOf<String>()
        var hasAiMetadata = false
        var hasGpsLocation = false
        var rawPromptText: String? = null

        // 1. Read EXIF via AndroidX ExifInterface
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                extractExifEntries(exif, entries)
            }
        } catch (_: Exception) {
        }

        // 2. Deep scan for AI metadata chunks (PNG tEXt/iTXt, XMP, C2PA, ComfyUI, WebUI parameters)
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val aiScan = scanForAiMetadata(stream)
                if (aiScan.aiEntries.isNotEmpty()) {
                    entries.addAll(aiScan.aiEntries)
                    hasAiMetadata = true
                    if (aiScan.promptText != null) {
                        rawPromptText = aiScan.promptText
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Check EXIF entries for AI prompts or GPS
        for (entry in entries) {
            if (entry.category == MetadataCategory.LOCATION && entry.isSensitive) {
                hasGpsLocation = true
            }
            if (entry.category == MetadataCategory.AI_PROVENANCE) {
                hasAiMetadata = true
                if (rawPromptText == null && entry.key.contains("Prompt", ignoreCase = true)) {
                    rawPromptText = entry.value
                }
            }
        }

        // Evaluate privacy risk
        val riskLevel: PrivacyRisk = when {
            hasGpsLocation -> {
                riskReasons.add("Precise GPS coordinates detected (exposes exact location)")
                if (hasAiMetadata) riskReasons.add("AI generation prompt & parameters detected")
                PrivacyRisk.HIGH
            }
            hasAiMetadata -> {
                riskReasons.add("Full AI generation prompt / model fingerprint detected")
                PrivacyRisk.MEDIUM
            }
            entries.any { it.isSensitive } -> {
                riskReasons.add("Hardware identifiers or serial numbers detected")
                PrivacyRisk.MEDIUM
            }
            entries.isNotEmpty() -> {
                riskReasons.add("Camera or timestamp metadata present")
                PrivacyRisk.LOW
            }
            else -> {
                riskReasons.add("Zero metadata detected — pristine privacy status")
                PrivacyRisk.SAFE
            }
        }

        return ImageInspectionResult(
            uri = uri,
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSize,
            width = width,
            height = height,
            entries = entries,
            riskLevel = riskLevel,
            riskReasons = riskReasons,
            hasAiMetadata = hasAiMetadata,
            hasGpsLocation = hasGpsLocation,
            rawPromptText = rawPromptText
        )
    }

    private fun extractExifEntries(exif: ExifInterface, list: MutableList<MetadataEntry>) {
        // Location & GPS
        val latLong = exif.latLong
        if (latLong != null) {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.LOCATION,
                    key = "GPS Coordinates",
                    value = String.format("%.5f°, %.5f°", latLong[0], latLong[1]),
                    isSensitive = true,
                    description = "Latitude and Longitude of shooting location"
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.LOCATION,
                    key = "GPS Altitude",
                    value = "$it m",
                    isSensitive = true
                )
            )
        }

        // Camera & Device
        exif.getAttribute(ExifInterface.TAG_MAKE)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "Camera Make",
                    value = it
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_MODEL)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "Camera Model",
                    value = it,
                    isSensitive = true
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "Lens Model",
                    value = it
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "Focal Length",
                    value = "$it mm"
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "Aperture",
                    value = "f/$it"
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "Exposure Time",
                    value = "$it sec"
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.CAMERA_DEVICE,
                    key = "ISO",
                    value = "ISO $it"
                )
            )
        }

        // Timestamps
        exif.getAttribute(ExifInterface.TAG_DATETIME)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.TIMESTAMPS_FILE,
                    key = "Date / Time Original",
                    value = it
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.TIMESTAMPS_FILE,
                    key = "Date Digitized",
                    value = it
                )
            )
        }

        // Author & Software
        exif.getAttribute(ExifInterface.TAG_ARTIST)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.AUTHOR_SYSTEM,
                    key = "Artist / Author",
                    value = it,
                    isSensitive = true
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_COPYRIGHT)?.let {
            list.add(
                MetadataEntry(
                    category = MetadataCategory.AUTHOR_SYSTEM,
                    key = "Copyright Notice",
                    value = it
                )
            )
        }
        exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let {
            val isAiSoftware = isAiToolName(it)
            list.add(
                MetadataEntry(
                    category = if (isAiSoftware) MetadataCategory.AI_PROVENANCE else MetadataCategory.AUTHOR_SYSTEM,
                    key = if (isAiSoftware) "AI Generation Engine" else "Software / Tool",
                    value = it,
                    isSensitive = isAiSoftware
                )
            )
        }

        // Check UserComment and ImageDescription for AI Prompts
        exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { comment ->
            if (comment.isNotBlank()) {
                val isAi = looksLikeAiPrompt(comment)
                list.add(
                    MetadataEntry(
                        category = if (isAi) MetadataCategory.AI_PROVENANCE else MetadataCategory.AUTHOR_SYSTEM,
                        key = if (isAi) "AI Prompt / Parameters" else "User Comment",
                        value = comment,
                        isSensitive = isAi
                    )
                )
            }
        }
        exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.let { desc ->
            if (desc.isNotBlank()) {
                val isAi = looksLikeAiPrompt(desc)
                list.add(
                    MetadataEntry(
                        category = if (isAi) MetadataCategory.AI_PROVENANCE else MetadataCategory.AUTHOR_SYSTEM,
                        key = if (isAi) "AI Generation Description" else "Image Description",
                        value = desc,
                        isSensitive = isAi
                    )
                )
            }
        }
    }

    private data class AiScanResult(
        val aiEntries: List<MetadataEntry>,
        val promptText: String?
    )

    private fun scanForAiMetadata(stream: InputStream): AiScanResult {
        val entries = mutableListOf<MetadataEntry>()
        var foundPrompt: String? = null

        val buffer = ByteArray(256 * 1024) // Read up to first 256KB for header chunks
        val bytesRead = stream.read(buffer)
        if (bytesRead <= 0) return AiScanResult(emptyList(), null)

        val content = String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1)

        // 1. Automatic1111 / WebUI parameters
        if (content.contains("parameters", ignoreCase = true) || content.contains("Negative prompt:", ignoreCase = true)) {
            val paramIdx = content.indexOf("parameters")
            if (paramIdx != -1) {
                val sub = content.substring(paramIdx, minOf(content.length, paramIdx + 2000))
                val cleanText = extractReadableText(sub)
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "Stable Diffusion / WebUI Parameters",
                        value = cleanText.take(500),
                        isSensitive = true
                    )
                )
                foundPrompt = cleanText
            }
        }

        // 2. Midjourney or DALL-E tags
        if (content.contains("Midjourney", ignoreCase = true)) {
            entries.add(
                MetadataEntry(
                    category = MetadataCategory.AI_PROVENANCE,
                    key = "AI Generator",
                    value = "Midjourney (Identified from embedded signatures)",
                    isSensitive = true
                )
            )
        }
        if (content.contains("DALL-E", ignoreCase = true) || content.contains("openai", ignoreCase = true)) {
            entries.add(
                MetadataEntry(
                    category = MetadataCategory.AI_PROVENANCE,
                    key = "AI Generator",
                    value = "OpenAI DALL-E / ChatGPT Image",
                    isSensitive = true
                )
            )
        }

        // 3. ComfyUI workflow
        if (content.contains("\"prompt\":", ignoreCase = true) || content.contains("\"workflow\":", ignoreCase = true)) {
            entries.add(
                MetadataEntry(
                    category = MetadataCategory.AI_PROVENANCE,
                    key = "ComfyUI Workflow Metadata",
                    value = "Embedded Node Graph & Prompt JSON detected",
                    isSensitive = true
                )
            )
        }

        // 4. C2PA / Content Credentials manifest
        if (content.contains("c2pa", ignoreCase = true) || content.contains("claim_generator", ignoreCase = true)) {
            entries.add(
                MetadataEntry(
                    category = MetadataCategory.AI_PROVENANCE,
                    key = "C2PA Content Credentials",
                    value = "Cryptographic provenance manifest detected",
                    isSensitive = true
                )
            )
        }

        return AiScanResult(entries, foundPrompt)
    }

    private fun isAiToolName(tool: String): Boolean {
        val lower = tool.lowercase()
        return lower.contains("stable diffusion") ||
                lower.contains("midjourney") ||
                lower.contains("comfyui") ||
                lower.contains("dall-e") ||
                lower.contains("novelai") ||
                lower.contains("flux") ||
                lower.contains("leonardo") ||
                lower.contains("adobe firefly")
    }

    private fun looksLikeAiPrompt(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("steps:") ||
                lower.contains("sampler:") ||
                lower.contains("cfg scale:") ||
                lower.contains("seed:") ||
                lower.contains("negative prompt:") ||
                lower.contains("model:") ||
                lower.contains("lora:") ||
                lower.contains("midjourney") ||
                lower.contains("--v ") ||
                lower.contains("--ar ")
    }

    private fun extractReadableText(raw: String): String {
        return raw.filter { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
            .trim()
    }
}
