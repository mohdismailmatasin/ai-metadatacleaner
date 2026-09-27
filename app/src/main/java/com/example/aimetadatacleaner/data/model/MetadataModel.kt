package com.example.aimetadatacleaner.data.model

import android.net.Uri

enum class MetadataCategory(val displayName: String, val iconDescription: String) {
    AI_PROVENANCE("AI & Generation Data", "AI Prompts, Models, Seeds & Credentials"),
    LOCATION("Location & GPS", "Latitude, Longitude & Geotags"),
    CAMERA_DEVICE("Camera & Device", "Hardware make, model & lens settings"),
    TIMESTAMPS_FILE("Timestamps & File", "Creation date, timestamps & format info"),
    AUTHOR_SYSTEM("Author & Software", "Artist, copyright & editing software")
}

enum class PrivacyRisk(val label: String, val score: Int) {
    SAFE("Clean / No Risk", 100),
    LOW("Low Privacy Risk", 75),
    MEDIUM("Medium Privacy Risk", 45),
    HIGH("High Privacy Risk", 15)
}

data class MetadataEntry(
    val category: MetadataCategory,
    val key: String,
    val value: String,
    val isSensitive: Boolean = false,
    val description: String = ""
)

data class ImageInspectionResult(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val entries: List<MetadataEntry>,
    val riskLevel: PrivacyRisk,
    val riskReasons: List<String>,
    val hasAiMetadata: Boolean,
    val hasGpsLocation: Boolean,
    val rawPromptText: String? = null
)
