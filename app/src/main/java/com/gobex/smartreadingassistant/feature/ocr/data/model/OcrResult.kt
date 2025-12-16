package com.gobex.smartreadingassistant.feature.ocr.data.model

import android.graphics.Rect

/**
 * Represents the result of OCR processing on an image
 */
data class OcrResult(
    val text: String,
    val blocks: List<TextBlock> = emptyList(),
    val confidence: Float? = null,
    val processingTimeMs: Long = 0,
    val language: String? = null
)

/**
 * Represents a block of text found in the image
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float? = null,
    val lines: List<TextLine> = emptyList()
)

/**
 * Represents a line of text within a block
 */
data class TextLine(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float? = null
)

