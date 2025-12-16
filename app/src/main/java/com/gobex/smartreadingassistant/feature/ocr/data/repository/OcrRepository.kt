package com.gobex.smartreadingassistant.feature.ocr.data.repository

import android.graphics.Bitmap
import com.gobex.smartreadingassistant.feature.ocr.data.model.OcrResult
import com.gobex.smartreadingassistant.feature.ocr.ml.MlKitOcrProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing OCR operations
 */
class OcrRepository(
    private val ocrProcessor: MlKitOcrProcessor = MlKitOcrProcessor()
) {
    /**
     * Process an image and extract text
     */
    suspend fun processImage(bitmap: Bitmap): Result<OcrResult> = withContext(Dispatchers.Default) {
        ocrProcessor.processImage(bitmap)
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        ocrProcessor.close()
    }
}

