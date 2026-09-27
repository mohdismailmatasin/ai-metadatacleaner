package com.example.aimetadatacleaner.util

import com.example.aimetadatacleaner.data.model.AiGenerationMetadata
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object AiMetadataParser {

    data class RawAiScan(
        val rawText: String,
        val sourceTag: String
    )

    fun scanStreamForAiPayloads(stream: InputStream): List<RawAiScan> {
        val results = mutableListOf<RawAiScan>()
        val buffer = ByteArray(512 * 1024) // Scan first 512KB for headers, text chunks, XMP
        val bytesRead = stream.read(buffer)
        if (bytesRead <= 0) return results

        val latin1Content = String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1)
        val utf8Content = try {
            String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            latin1Content
        }

        // 1. Check PNG "parameters" chunk
        if (latin1Content.contains("parameters", ignoreCase = false)) {
            val idx = latin1Content.indexOf("parameters")
            val endIdx = findChunkEnd(buffer, idx + 10, bytesRead)
            val chunkStr = extractSafeString(buffer, idx + 10, endIdx)
            if (chunkStr.isNotBlank()) {
                results.add(RawAiScan(chunkStr, "PNG Chunk: \"parameters\" (WebUI / SD)"))
            }
        }

        // 2. Check PNG "prompt" chunk (ComfyUI API graph)
        if (latin1Content.contains("prompt\u0000") || latin1Content.contains("tEXtprompt") || latin1Content.contains("iTXtprompt")) {
            val idx = latin1Content.indexOf("prompt")
            val endIdx = findChunkEnd(buffer, idx + 7, bytesRead)
            val chunkStr = extractSafeString(buffer, idx + 7, endIdx)
            if (chunkStr.contains("{") && chunkStr.contains("}")) {
                results.add(RawAiScan(chunkStr, "PNG Chunk: \"prompt\" (ComfyUI Workflow)"))
            }
        }

        // 3. Check PNG "workflow" chunk (ComfyUI Visual graph)
        if (latin1Content.contains("workflow\u0000") || latin1Content.contains("tEXtworkflow") || latin1Content.contains("iTXtworkflow")) {
            val idx = latin1Content.indexOf("workflow")
            val endIdx = findChunkEnd(buffer, idx + 9, bytesRead)
            val chunkStr = extractSafeString(buffer, idx + 9, endIdx)
            if (chunkStr.contains("{") && chunkStr.contains("}")) {
                results.add(RawAiScan(chunkStr, "PNG Chunk: \"workflow\" (ComfyUI Graph UI)"))
            }
        }

        // 4. Check for Stable Diffusion signature even without explicit "parameters" chunk header
        if (results.isEmpty() && (utf8Content.contains("Steps:", ignoreCase = true) || utf8Content.contains("Negative prompt:", ignoreCase = true))) {
            val startIdx = maxOf(0, utf8Content.indexOf("Negative prompt:").let { if (it > 200) it - 200 else 0 })
            val sub = utf8Content.substring(startIdx, minOf(utf8Content.length, startIdx + 3000))
            results.add(RawAiScan(sub.trim(), "Embedded Metadata: Diffusion Generation Parameters"))
        }

        // 5. Check Midjourney embedded signatures
        if (utf8Content.contains("Midjourney", ignoreCase = true) || utf8Content.contains("--v ") || utf8Content.contains("--ar ")) {
            val mjPattern = Pattern.compile("([^\\x00-\\x1F\\x7F-\\x9F]{10,800}(?:--v |--ar |--stylize |--seed )[^\\x00-\\x1F\\x7F-\\x9F]{0,300})")
            val m = mjPattern.matcher(utf8Content)
            if (m.find()) {
                results.add(RawAiScan(m.group(1)?.trim() ?: "Midjourney generation parameters", "Image Text: Midjourney Prompt & Flags"))
            } else if (!results.any { it.sourceTag.contains("Midjourney") }) {
                results.add(RawAiScan("Midjourney generation signature detected", "Embedded Signature: Midjourney"))
            }
        }

        // 6. Check DALL-E / OpenAI / Bing Image Creator
        if (utf8Content.contains("DALL-E", ignoreCase = true) || utf8Content.contains("openai", ignoreCase = true) || utf8Content.contains("trainedAlgorithmicMedia", ignoreCase = true)) {
            results.add(RawAiScan("AI Generator: OpenAI DALL-E / Bing Image Creator", "IPTC / XMP Tag: DigitalSourceType: trainedAlgorithmicMedia"))
        }

        // 7. Check C2PA / Content Credentials
        if (latin1Content.contains("c2pa", ignoreCase = true) || latin1Content.contains("claim_generator", ignoreCase = true) || latin1Content.contains("jumb", ignoreCase = true)) {
            results.add(RawAiScan("C2PA Cryptographic Content Credentials manifest detected", "Provenance: C2PA / CAI Manifest"))
        }

        return results
    }

    fun parseAiMetadata(
        scans: List<RawAiScan>,
        exifComment: String? = null,
        exifDescription: String? = null,
        exifSoftware: String? = null
    ): AiGenerationMetadata? {
        val metaTagsInvolved = mutableListOf<String>()
        val allRawParts = mutableListOf<String>()

        scans.forEach {
            metaTagsInvolved.add(it.sourceTag)
            allRawParts.add(it.rawText)
        }

        if (!exifComment.isNullOrBlank() && looksLikeAiText(exifComment)) {
            metaTagsInvolved.add("EXIF Tag: UserComment (0x9286)")
            allRawParts.add(exifComment)
        }
        if (!exifDescription.isNullOrBlank() && looksLikeAiText(exifDescription)) {
            metaTagsInvolved.add("EXIF Tag: ImageDescription (0x010E)")
            allRawParts.add(exifDescription)
        }
        if (!exifSoftware.isNullOrBlank() && isAiEngineName(exifSoftware)) {
            metaTagsInvolved.add("EXIF Tag: Software (0x0131): \"$exifSoftware\"")
            allRawParts.add("Software: $exifSoftware")
        }

        if (metaTagsInvolved.isEmpty() && allRawParts.isEmpty()) {
            return null
        }

        val combinedRaw = allRawParts.joinToString("\n\n")

        // Try parsing Stable Diffusion WebUI / Automatic1111 format
        val sdResult = parseAutomatic1111(combinedRaw, metaTagsInvolved)
        if (sdResult != null) return sdResult

        // Try parsing ComfyUI JSON format
        val comfyResult = parseComfyUi(combinedRaw, metaTagsInvolved)
        if (comfyResult != null) return comfyResult

        // Try parsing Midjourney format
        val mjResult = parseMidjourney(combinedRaw, metaTagsInvolved)
        if (mjResult != null) return mjResult

        // Try parsing NovelAI format
        val naiResult = parseNovelAi(combinedRaw, metaTagsInvolved)
        if (naiResult != null) return naiResult

        // Fallback generic AI generator detection
        val engine = detectEngineName(combinedRaw, exifSoftware)
        return AiGenerationMetadata(
            detectedEngine = engine,
            positivePrompt = extractGeneralPrompt(combinedRaw),
            negativePrompt = null,
            rawParametersText = combinedRaw.take(3000),
            metaTagsInvolved = metaTagsInvolved.distinct()
        )
    }

    private fun parseAutomatic1111(text: String, tags: List<String>): AiGenerationMetadata? {
        val stepsPattern = Pattern.compile("(?i)Steps:\\s*(\\d+)")
        val stepsMatcher = stepsPattern.matcher(text)
        val hasSteps = stepsMatcher.find()
        val hasNegative = text.contains("Negative prompt:", ignoreCase = true)

        if (!hasSteps && !hasNegative && !text.contains("CFG scale:", ignoreCase = true)) {
            return null
        }

        var positivePrompt: String? = null
        var negativePrompt: String? = null
        val otherParams = mutableMapOf<String, String>()

        val negIdx = text.indexOf("Negative prompt:", ignoreCase = true)
        val stepsIdx = findStepsIndex(text)

        if (negIdx != -1) {
            positivePrompt = text.substring(0, negIdx).trim()
            if (stepsIdx != -1 && stepsIdx > negIdx) {
                negativePrompt = text.substring(negIdx + "Negative prompt:".length, stepsIdx).trim()
            } else {
                negativePrompt = text.substring(negIdx + "Negative prompt:".length).trim()
            }
        } else if (stepsIdx != -1) {
            positivePrompt = text.substring(0, stepsIdx).trim()
        }

        // Parse key-value parameters
        val paramSection = if (stepsIdx != -1) text.substring(stepsIdx) else text

        val steps = extractRegex(paramSection, "(?i)Steps:\\s*(\\d+)")
        val sampler = extractRegex(paramSection, "(?i)Sampler:\\s*([^,]+)")
        val cfg = extractRegex(paramSection, "(?i)CFG scale:\\s*([0-9.]+)")
        val seed = extractRegex(paramSection, "(?i)Seed:\\s*(\\d+)")
        val size = extractRegex(paramSection, "(?i)Size:\\s*(\\d+x\\d+)")
        val model = extractRegex(paramSection, "(?i)Model:\\s*([^,]+)")
        val modelHash = extractRegex(paramSection, "(?i)Model hash:\\s*([^,]+)")
        val denoise = extractRegex(paramSection, "(?i)Denoising strength:\\s*([0-9.]+)")
        val clipSkip = extractRegex(paramSection, "(?i)Clip skip:\\s*(\\d+)")

        if (modelHash != null) otherParams["Model Hash"] = modelHash
        if (denoise != null) otherParams["Denoising Strength"] = denoise
        if (clipSkip != null) otherParams["Clip Skip"] = clipSkip

        // Extract LoRAs
        val loras = mutableListOf<String>()
        val loraPattern = Pattern.compile("<lora:([^:>]+):?([^>]*)>")
        val loraMatcher = loraPattern.matcher(text)
        while (loraMatcher.find()) {
            val name = loraMatcher.group(1) ?: ""
            val weight = loraMatcher.group(2) ?: "1.0"
            loras.add("$name ($weight)")
        }

        return AiGenerationMetadata(
            detectedEngine = "Stable Diffusion (WebUI / Forge)",
            positivePrompt = positivePrompt?.takeIf { it.isNotBlank() },
            negativePrompt = negativePrompt?.takeIf { it.isNotBlank() },
            steps = steps,
            sampler = sampler,
            cfgScale = cfg,
            seed = seed,
            model = model,
            dimensions = size,
            loras = loras,
            otherParameters = otherParams,
            rawParametersText = text.trim(),
            metaTagsInvolved = tags.distinct()
        )
    }

    private fun parseComfyUi(text: String, tags: List<String>): AiGenerationMetadata? {
        if (!text.contains("\"inputs\"") && !text.contains("\"class_type\"")) return null
        return try {
            val jsonStart = text.indexOf("{")
            val jsonEnd = text.lastIndexOf("}")
            if (jsonStart == -1 || jsonEnd <= jsonStart) return null

            val jsonStr = text.substring(jsonStart, jsonEnd + 1)
            val root = JSONObject(jsonStr)

            var posPrompt: String? = null
            var negPrompt: String? = null
            var steps: String? = null
            var sampler: String? = null
            var cfg: String? = null
            var seed: String? = null
            var model: String? = null
            val loras = mutableListOf<String>()

            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val node = root.optJSONObject(key) ?: continue
                val classType = node.optString("class_type", "")
                val inputs = node.optJSONObject("inputs") ?: continue

                when {
                    classType.contains("CLIPTextEncode", ignoreCase = true) -> {
                        val promptText = inputs.optString("text", "")
                        if (promptText.isNotBlank()) {
                            if (posPrompt == null) posPrompt = promptText
                            else if (negPrompt == null) negPrompt = promptText
                        }
                    }
                    classType.contains("KSampler", ignoreCase = true) -> {
                        if (steps == null) steps = inputs.optString("steps", null)
                        if (sampler == null) {
                            val sName = inputs.optString("sampler_name", "")
                            val sched = inputs.optString("scheduler", "")
                            sampler = if (sched.isNotBlank()) "$sName ($sched)" else sName
                        }
                        if (cfg == null) cfg = inputs.optString("cfg", null)
                        if (seed == null) seed = inputs.optString("seed", null)
                    }
                    classType.contains("CheckpointLoader", ignoreCase = true) -> {
                        if (model == null) model = inputs.optString("ckpt_name", null)
                    }
                    classType.contains("LoraLoader", ignoreCase = true) -> {
                        val loraName = inputs.optString("lora_name", "")
                        val str = inputs.optString("strength_model", "1.0")
                        if (loraName.isNotBlank()) loras.add("$loraName ($str)")
                    }
                }
            }

            AiGenerationMetadata(
                detectedEngine = "ComfyUI (Node Graph Workflow)",
                positivePrompt = posPrompt,
                negativePrompt = negPrompt,
                steps = steps,
                sampler = sampler,
                cfgScale = cfg,
                seed = seed,
                model = model,
                loras = loras,
                rawParametersText = text.trim(),
                metaTagsInvolved = tags.distinct()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMidjourney(text: String, tags: List<String>): AiGenerationMetadata? {
        if (!text.contains("--v ") && !text.contains("--ar ") && !text.contains("Midjourney", ignoreCase = true)) {
            return null
        }

        val flagIdx = text.indexOf("--")
        val prompt = if (flagIdx != -1) text.substring(0, flagIdx).trim() else text.trim()
        val version = extractRegex(text, "--v\\s+([0-9.]+)")
        val ar = extractRegex(text, "--ar\\s+([0-9:]+)")
        val seed = extractRegex(text, "--seed\\s+(\\d+)")
        val stylize = extractRegex(text, "--stylize\\s+(\\d+)") ?: extractRegex(text, "--s\\s+(\\d+)")
        val chaos = extractRegex(text, "--chaos\\s+(\\d+)")

        val other = mutableMapOf<String, String>()
        if (stylize != null) other["Stylize"] = stylize
        if (chaos != null) other["Chaos"] = chaos

        return AiGenerationMetadata(
            detectedEngine = "Midjourney (v${version ?: "6"})",
            positivePrompt = prompt.takeIf { it.isNotBlank() },
            negativePrompt = null,
            model = "Midjourney ${version?.let { "v$it" } ?: ""}".trim(),
            dimensions = ar?.let { "Aspect Ratio $it" },
            seed = seed,
            otherParameters = other,
            rawParametersText = text.trim(),
            metaTagsInvolved = tags.distinct()
        )
    }

    private fun parseNovelAi(text: String, tags: List<String>): AiGenerationMetadata? {
        if (!text.contains("\"uc\":") && !text.contains("\"prompt\":")) return null
        return try {
            val jsonStart = text.indexOf("{")
            val jsonEnd = text.lastIndexOf("}")
            if (jsonStart == -1) return null
            val obj = JSONObject(text.substring(jsonStart, jsonEnd + 1))
            AiGenerationMetadata(
                detectedEngine = "NovelAI Diffusion",
                positivePrompt = obj.optString("prompt", null),
                negativePrompt = obj.optString("uc", null),
                steps = obj.optString("steps", null),
                cfgScale = obj.optString("scale", null),
                seed = obj.optString("seed", null),
                sampler = obj.optString("sampler", null),
                rawParametersText = text.trim(),
                metaTagsInvolved = tags.distinct()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRegex(input: String, patternStr: String): String? {
        val p = Pattern.compile(patternStr)
        val m = p.matcher(input)
        return if (m.find()) m.group(1)?.trim() else null
    }

    private fun findStepsIndex(text: String): Int {
        val p = Pattern.compile("(?i)Steps:\\s*\\d+")
        val m = p.matcher(text)
        return if (m.find()) m.start() else -1
    }

    private fun looksLikeAiText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("steps:") ||
                lower.contains("sampler:") ||
                lower.contains("cfg scale:") ||
                lower.contains("seed:") ||
                lower.contains("negative prompt:") ||
                lower.contains("midjourney") ||
                lower.contains("comfyui") ||
                lower.contains("--v ") ||
                lower.contains("--ar ") ||
                lower.contains("dall-e") ||
                lower.contains("flux") ||
                lower.contains("parameters")
    }

    private fun isAiEngineName(software: String): Boolean {
        val lower = software.lowercase()
        return lower.contains("stable diffusion") ||
                lower.contains("midjourney") ||
                lower.contains("comfyui") ||
                lower.contains("novelai") ||
                lower.contains("dall-e") ||
                lower.contains("flux") ||
                lower.contains("automatic1111") ||
                lower.contains("webui") ||
                lower.contains("firefly")
    }

    private fun detectEngineName(text: String, software: String?): String {
        val lower = text.lowercase()
        val sLower = software?.lowercase() ?: ""
        return when {
            lower.contains("midjourney") || sLower.contains("midjourney") -> "Midjourney"
            lower.contains("dall-e") || lower.contains("openai") -> "OpenAI DALL-E 3"
            lower.contains("comfyui") || sLower.contains("comfyui") -> "ComfyUI Workflow"
            lower.contains("novelai") -> "NovelAI"
            lower.contains("flux") -> "Flux.1 Generator"
            lower.contains("c2pa") || lower.contains("claim_generator") -> "C2PA Provenance Engine"
            else -> "AI Image Generator (Model Footprint)"
        }
    }

    private fun extractGeneralPrompt(text: String): String {
        return text.take(600).filter { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }.trim()
    }

    private fun findChunkEnd(buffer: ByteArray, start: Int, max: Int): Int {
        var i = start
        while (i < max && i < start + 6000) {
            if (buffer[i] == 0.toByte() && i + 1 < max && buffer[i + 1] == 0.toByte()) {
                return i
            }
            i++
        }
        return minOf(max, start + 6000)
    }

    private fun extractSafeString(buffer: ByteArray, start: Int, end: Int): String {
        val len = maxOf(0, end - start)
        if (len <= 0) return ""
        return try {
            String(buffer, start, len, StandardCharsets.UTF_8)
                .filter { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
                .trim()
        } catch (_: Exception) {
            ""
        }
    }
}
