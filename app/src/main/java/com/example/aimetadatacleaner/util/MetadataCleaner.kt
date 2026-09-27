package com.example.aimetadatacleaner.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.aimetadatacleaner.data.model.CleanExecutionResult
import com.example.aimetadatacleaner.data.model.CleaningOptions
import com.example.aimetadatacleaner.data.model.OutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object MetadataCleaner {

    suspend fun cleanImage(
        context: Context,
        inputUri: Uri,
        options: CleaningOptions
    ): CleanExecutionResult = withContext(Dispatchers.IO) {
        val inspection = MetadataExtractor.inspectImage(context, inputUri)

        try {
            // Read orientation to preserve visual rotation after EXIF is stripped
            val orientation = getOrientation(context, inputUri)

            // Decode image
            val bitmap = decodeBitmap(context, inputUri, orientation)
                ?: return@withContext CleanExecutionResult(
                    success = false,
                    originalUri = inputUri,
                    cleanedUri = null,
                    cleanedFilePath = null,
                    originalFileName = inspection.fileName,
                    cleanedFileName = "",
                    originalSizeBytes = inspection.fileSizeBytes,
                    cleanedSizeBytes = 0,
                    tagsRemovedCount = 0,
                    removedAiTags = false,
                    removedGps = false,
                    errorMessage = "Could not decode image bitmap."
                )

            // Determine target format & extension
            val (compressFormat, extension) = determineFormat(inspection.mimeType, options.format)
            val baseName = inspection.fileName.substringBeforeLast(".")
            val cleanedFileName = "cleaned_${baseName}_${System.currentTimeMillis()}.$extension"

            // Target file in app's internal cache
            val outputDir = File(context.cacheDir, "cleaned").apply { mkdirs() }
            val outputFile = File(outputDir, cleanedFileName)

            // Write pristine pixel stream (strips all non-pixel metadata chunks automatically)
            FileOutputStream(outputFile).use { outStream ->
                bitmap.compress(compressFormat, options.quality.coerceIn(50, 100), outStream)
            }
            bitmap.recycle()

            val cleanedSize = outputFile.length()
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            // Count how many metadata tags were eliminated
            val tagsRemoved = if (options.stripAll) {
                inspection.entries.size.coerceAtLeast(1)
            } else {
                var count = 0
                if (options.stripAiMetadata && inspection.hasAiMetadata) count += 2
                if (options.stripLocationGps && inspection.hasGpsLocation) count += 2
                if (options.stripCameraDevice) count += inspection.entries.count { it.category.name == "CAMERA_DEVICE" }
                if (options.stripTimestamps) count += inspection.entries.count { it.category.name == "TIMESTAMPS_FILE" }
                if (options.stripAuthorCopyright) count += inspection.entries.count { it.category.name == "AUTHOR_SYSTEM" }
                count.coerceAtLeast(1)
            }

            CleanExecutionResult(
                success = true,
                originalUri = inputUri,
                cleanedUri = contentUri,
                cleanedFilePath = outputFile.absolutePath,
                originalFileName = inspection.fileName,
                cleanedFileName = cleanedFileName,
                originalSizeBytes = inspection.fileSizeBytes,
                cleanedSizeBytes = cleanedSize,
                tagsRemovedCount = tagsRemoved,
                removedAiTags = inspection.hasAiMetadata,
                removedGps = inspection.hasGpsLocation
            )
        } catch (e: Exception) {
            CleanExecutionResult(
                success = false,
                originalUri = inputUri,
                cleanedUri = null,
                cleanedFilePath = null,
                originalFileName = inspection.fileName,
                cleanedFileName = "",
                originalSizeBytes = inspection.fileSizeBytes,
                cleanedSizeBytes = 0,
                tagsRemovedCount = 0,
                removedAiTags = false,
                removedGps = false,
                errorMessage = e.localizedMessage ?: "Unknown error while cleaning image"
            )
        }
    }

    suspend fun saveToGallery(context: Context, cleanedFile: File): Uri? = withContext(Dispatchers.IO) {
        try {
            val mimeType = when {
                cleanedFile.name.endsWith(".png", ignoreCase = true) -> "image/png"
                cleanedFile.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanedFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MetaClean")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { out ->
                cleanedFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (_: Exception) {
            null
        }
    }

    fun shareImage(context: Context, uri: Uri, fileName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Cleaned Photo"))
    }

    private fun decodeBitmap(context: Context, uri: Uri, orientation: Int): Bitmap? {
        val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(stream)
        stream.close()

        if (originalBitmap == null) return null
        if (orientation == 0) return originalBitmap

        val matrix = Matrix()
        when (orientation) {
            90 -> matrix.postRotate(90f)
            180 -> matrix.postRotate(180f)
            270 -> matrix.postRotate(270f)
            else -> return originalBitmap
        }

        val rotated = Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalBitmap.width,
            originalBitmap.height,
            matrix,
            true
        )
        if (rotated != originalBitmap) {
            originalBitmap.recycle()
        }
        return rotated
    }

    private fun getOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun determineFormat(
        originalMime: String,
        target: OutputFormat
    ): Pair<Bitmap.CompressFormat, String> {
        return when (target) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG to "jpg"
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG to "png"
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY to "webp"
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP to "webp"
                }
            }
            OutputFormat.PRESERVE_ORIGINAL -> {
                when {
                    originalMime.contains("png", ignoreCase = true) -> Bitmap.CompressFormat.PNG to "png"
                    originalMime.contains("webp", ignoreCase = true) -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY to "webp"
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP to "webp"
                        }
                    }
                    else -> Bitmap.CompressFormat.JPEG to "jpg"
                }
            }
        }
    }
}
