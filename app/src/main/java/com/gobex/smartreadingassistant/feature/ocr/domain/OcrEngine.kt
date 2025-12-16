package com.gobex.smartreadingassistant.feature.ocr.domain

import android.graphics.Bitmap
import com.gobex.smartreadingassistant.feature.ocr.data.model.OcrResult

/**
 * Interface for OCR engines
 */
interface OcrEngine {
    suspend fun processImage(bitmap: Bitmap): Result<OcrResult>
    fun close()
}

