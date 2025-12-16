package com.gobex.smartreadingassistant.feature.ocr.ml

import android.graphics.Bitmap
import com.gobex.smartreadingassistant.feature.ocr.data.model.OcrResult
import com.gobex.smartreadingassistant.feature.ocr.data.model.TextBlock
import com.gobex.smartreadingassistant.feature.ocr.data.model.TextLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.system.measureTimeMillis

/**
 * ML Kit implementation for OCR text recognition
 */
class MlKitOcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process an image and extract text using ML Kit
     * @param bitmap The image to process
     * @return OcrResult containing extracted text and metadata
     */
    suspend fun processImage(bitmap: Bitmap): Result<OcrResult> {
        return try {
            var result: OcrResult? = null
            val processingTime = measureTimeMillis {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).await()

                val blocks = visionText.textBlocks.map { block ->
                    TextBlock(
                        text = block.text,
                        boundingBox = block.boundingBox,
                        confidence = null, // ML Kit doesn't provide confidence at block level
                        lines = block.lines.map { line ->
                            TextLine(
                                text = line.text,
                                boundingBox = line.boundingBox,
                                confidence = null // ML Kit doesn't provide confidence at line level
                            )
                        }
                    )
                }

                result = OcrResult(
                    text = visionText.text,
                    blocks = blocks,
                    processingTimeMs = 0, // Will be set below
                    language = detectLanguage(visionText.text)
                )
            }

            Result.success(result!!.copy(processingTimeMs = processingTime))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Improved language detection for common cases (Turkish, English, mixed).
     * Heuristic approach:
     * - If empty -> "unknown"
     * - If contains clear non-Latin script (CJK, Arabic, Cyrillic) -> "non-latin"
     * - If contains Turkish-specific characters (ç,ğ,ı,İ,ö,ş,ü) -> Turkish indicator
     * - If contains common English stopwords or plain ascii-only words -> English indicator
     * - If both indicators present -> "mixed"
     * This is a lightweight heuristic intended for UI/UX metadata, not production-grade language detection.
     */
    private fun detectLanguage(text: String): String {
        if (text.isBlank()) return "unknown"

        // Quick check for scripts that are clearly non-Latin (Chinese/Japanese/Korean, Arabic, Cyrillic)
        // Use alternation with a raw string to match any of these Unicode script properties.
        val nonLatinScriptRegex = Regex("""\p{IsHan}|\p{IsHangul}|\p{IsHiragana}|\p{IsKatakana}|\p{IsArabic}|\p{IsCyrillic}""")
        if (nonLatinScriptRegex.containsMatchIn(text)) return "non-latin"

        // Turkish-specific characters
        val turkishCharRegex = Regex("[çÇğĞıİöÖşŞüÜ]")

        // Small set of common English stopwords to help identify English tokens
        val englishStopwords = setOf(
            "the", "and", "is", "in", "to", "of", "for", "with", "on", "at",
            "this", "that", "are", "be", "or", "not", "you", "i", "we", "they",
            "he", "she", "it", "was", "were", "by", "from", "as", "an"
        )

        val tokens = text
            .lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotEmpty() }

        var turkishIndicator = false
        var englishIndicator = false

        if (turkishCharRegex.containsMatchIn(text)) {
            turkishIndicator = true
        }

        for (token in tokens) {
            if (token in englishStopwords) {
                englishIndicator = true
            }

            // token looks like ascii-only letters (a-z) -> likely English or at least Latin script
            if (!turkishIndicator && token.matches(Regex("^[a-z]+"))) {
                // to avoid false-positive for short tokens like "a" or "i", require length > 1
                if (token.length > 1) englishIndicator = true
            }

            // token contains Turkish-specific characters
            if (!turkishIndicator && token.contains(turkishCharRegex)) turkishIndicator = true

            if (turkishIndicator && englishIndicator) break
        }

        return when {
            turkishIndicator && englishIndicator -> "mixed"
            turkishIndicator -> "turkish"
            englishIndicator -> "english"
            else -> "latin"
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        recognizer.close()
    }
}
