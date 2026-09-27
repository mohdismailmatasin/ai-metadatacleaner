package com.example.aimetadatacleaner.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.aimetadatacleaner.data.model.AiGenerationMetadata
import com.example.aimetadatacleaner.data.model.ImageInspectionResult
import com.example.aimetadatacleaner.data.model.MetadataCategory
import com.example.aimetadatacleaner.data.model.MetadataEntry
import com.example.aimetadatacleaner.data.model.PrivacyRisk

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
        var aiMetadata: AiGenerationMetadata? = null

        var exifComment: String? = null
        var exifDescription: String? = null
        var exifSoftware: String? = null

        // 1. Read EXIF via AndroidX ExifInterface
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exifComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                exifDescription = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                exifSoftware = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                extractExifEntries(exif, entries)
            }
        } catch (_: Exception) {
        }

        // 2. Deep scan for AI metadata chunks and parameters
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val scans = AiMetadataParser.scanStreamForAiPayloads(stream)
                aiMetadata = AiMetadataParser.parseAiMetadata(
                    scans = scans,
                    exifComment = exifComment,
                    exifDescription = exifDescription,
                    exifSoftware = exifSoftware
                )
            }
        } catch (_: Exception) {
        }

        // If AI metadata parsed, populate structured entries
        aiMetadata?.let { ai ->
            hasAiMetadata = true
            rawPromptText = ai.positivePrompt ?: ai.rawParametersText

            entries.add(
                MetadataEntry(
                    category = MetadataCategory.AI_PROVENANCE,
                    key = "AI Generator Engine",
                    value = ai.detectedEngine,
                    isSensitive = true,
                    description = "Detected generative AI tool or framework"
                )
            )

            ai.positivePrompt?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "AI Positive Prompt",
                        value = it,
                        isSensitive = true,
                        description = "Text prompt used to create this image"
                    )
                )
            }

            ai.negativePrompt?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "AI Negative Prompt",
                        value = it,
                        isSensitive = true,
                        description = "Excluded attributes/negative constraints"
                    )
                )
            }

            ai.model?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "AI Model Checkpoint",
                        value = it,
                        isSensitive = true,
                        description = "Base neural network weights or checkpoint"
                    )
                )
            }

            ai.seed?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "Generation Seed",
                        value = it,
                        isSensitive = true,
                        description = "Deterministic random seed"
                    )
                )
            }

            ai.steps?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "Inference Steps",
                        value = it,
                        isSensitive = false,
                        description = "Diffusion sampling iteration count"
                    )
                )
            }

            ai.sampler?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "Sampler / Scheduler",
                        value = it,
                        isSensitive = false,
                        description = "Diffusion noise scheduler"
                    )
                )
            }

            ai.cfgScale?.let {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "CFG Guidance Scale",
                        value = it,
                        isSensitive = false,
                        description = "Prompt adherence multiplier"
                    )
                )
            }

            if (ai.loras.isNotEmpty()) {
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "LoRA Fine-Tunes",
                        value = ai.loras.joinToString(", "),
                        isSensitive = true,
                        description = "Low-Rank Adaptation models"
                    )
                )
            }

            ai.metaTagsInvolved.forEach { tag ->
                entries.add(
                    MetadataEntry(
                        category = MetadataCategory.AI_PROVENANCE,
                        key = "AI Meta Tag Involved",
                        value = tag,
                        isSensitive = true,
                        description = "Physical container or chunk holding AI parameters"
                    )
                )
            }
        }

        // Check for GPS
        for (entry in entries) {
            if (entry.category == MetadataCategory.LOCATION && entry.isSensitive) {
                hasGpsLocation = true
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
                riskReasons.add("Hardware identifiers or camera serial numbers detected")
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
            rawPromptText = rawPromptText,
            aiMetadata = aiMetadata
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
    }
}
