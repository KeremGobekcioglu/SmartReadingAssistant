package com.gobex.smartreadingassistant.feature.ocr.test

import android.content.Context
import android.graphics.BitmapFactory
import com.gobex.smartreadingassistant.feature.ocr.ml.MlKitOcrProcessor
import java.io.File

data class OcrTestResult(
    val imageName: String,
    val groundTruth: String,
    val detectedText: String,
    val characterErrorRate: Float,
    val wordErrorRate: Float,
    val processingTimeMs: Long,
    val success: Boolean
)

data class OcrTestReport(
    val totalTests: Int,
    val successRate: Float,
    val averageCER: Float,
    val averageWER: Float,
    val averageProcessingTime: Long,
    val results: List<OcrTestResult>
)

class OcrTestRunner(private val context: Context) {

    private val ocrProcessor = MlKitOcrProcessor()

    /**
     * Run tests on SROIE2019 dataset
     * @param testFolder Path to SROIE2019/test or train folder
     */
    suspend fun runTests(testFolder: File, maxTests: Int = 20): OcrTestReport {
        val imgFolder = File(testFolder, "img")
        val boxFolder = File(testFolder, "box")

        if (!imgFolder.exists()) {
            throw IllegalArgumentException("Image folder not found: ${imgFolder.absolutePath}")
        }

        val results = mutableListOf<OcrTestResult>()

        imgFolder.listFiles()?.filter { it.extension in listOf("jpg", "jpeg", "png") }
            ?.take(maxTests)?.forEach { imageFile ->
            try {
                // Load ground truth text
                val boxFile = File(boxFolder, "${imageFile.nameWithoutExtension}.txt")
                val groundTruth = loadGroundTruth(boxFile)

                // Process image with ML Kit
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap == null) {
                    results.add(createFailedResult(imageFile.name, groundTruth))
                    return@forEach
                }

                val startTime = System.currentTimeMillis()
                val ocrResult = ocrProcessor.processImage(bitmap)
                val processingTime = System.currentTimeMillis() - startTime

                if (ocrResult.isSuccess) {
                    val detectedText = ocrResult.getOrNull()?.text ?: ""

                    // Log for debugging
                    android.util.Log.d("OcrTestRunner", "Image: ${imageFile.name}")
                    android.util.Log.d("OcrTestRunner", "Ground Truth Length: ${groundTruth.length}")
                    android.util.Log.d("OcrTestRunner", "Detected Text Length: ${detectedText.length}")
                    android.util.Log.d("OcrTestRunner", "Ground Truth Preview: ${groundTruth.take(100)}")
                    android.util.Log.d("OcrTestRunner", "Detected Text Preview: ${detectedText.take(100)}")

                    results.add(OcrTestResult(
                        imageName = imageFile.name,
                        groundTruth = groundTruth,
                        detectedText = detectedText,
                        characterErrorRate = calculateCER(groundTruth, detectedText),
                        wordErrorRate = calculateWER(groundTruth, detectedText),
                        processingTimeMs = processingTime,
                        success = true
                    ))
                } else {
                    results.add(createFailedResult(imageFile.name, groundTruth, processingTime))
                }

                // Clean up bitmap
                bitmap.recycle()

            } catch (e: Exception) {
                e.printStackTrace()
                results.add(createFailedResult(imageFile.name, ""))
            }
        }

        if (results.isEmpty()) {
            throw IllegalStateException("No test results generated. Check if images exist in ${imgFolder.absolutePath}")
        }

        return OcrTestReport(
            totalTests = results.size,
            successRate = results.count { it.success }.toFloat() / results.size,
            averageCER = results.filter { it.success }.map { it.characterErrorRate }.average().toFloat(),
            averageWER = results.filter { it.success }.map { it.wordErrorRate }.average().toFloat(),
            averageProcessingTime = results.map { it.processingTimeMs }.average().toLong(),
            results = results
        )
    }

    private fun createFailedResult(
        imageName: String,
        groundTruth: String,
        processingTime: Long = 0
    ): OcrTestResult {
        return OcrTestResult(
            imageName = imageName,
            groundTruth = groundTruth,
            detectedText = "",
            characterErrorRate = 1f,
            wordErrorRate = 1f,
            processingTimeMs = processingTime,
            success = false
        )
    }

    /**
     * Load ground truth from SROIE box file
     * Format: x1,y1,x2,y2,x3,y3,x4,y4,text
     */
    private fun loadGroundTruth(boxFile: File): String {
        if (!boxFile.exists()) return ""

        return try {
            boxFile.readLines()
                .mapNotNull { line ->
                    // Format: x1,y1,x2,y2,x3,y3,x4,y4,text
                    val parts = line.split(",")
                    if (parts.size >= 9) {
                        // Join all parts after the 8 coordinates
                        parts.subList(8, parts.size).joinToString(",")
                    } else null
                }
                .joinToString(" ")
                .trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Calculate Character Error Rate using Levenshtein distance
     */
    private fun calculateCER(reference: String, hypothesis: String): Float {
        if (reference.isEmpty()) return if (hypothesis.isEmpty()) 0f else 1f

        val distance = levenshteinDistance(reference, hypothesis)
        return distance.toFloat() / reference.length
    }

    /**
     * Calculate Word Error Rate
     */
    private fun calculateWER(reference: String, hypothesis: String): Float {
        val refWords = reference.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val hypWords = hypothesis.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0f else 1f

        val distance = levenshteinDistanceList(refWords, hypWords)
        return distance.toFloat() / refWords.size
    }

    /**
     * Levenshtein distance for strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * Levenshtein distance for word lists
     */
    private fun levenshteinDistanceList(list1: List<String>, list2: List<String>): Int {
        val dp = Array(list1.size + 1) { IntArray(list2.size + 1) }

        for (i in 0..list1.size) dp[i][0] = i
        for (j in 0..list2.size) dp[0][j] = j

        for (i in 1..list1.size) {
            for (j in 1..list2.size) {
                val cost = if (list1[i - 1] == list2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[list1.size][list2.size]
    }

    fun cleanup() {
        ocrProcessor.close()
    }
}

