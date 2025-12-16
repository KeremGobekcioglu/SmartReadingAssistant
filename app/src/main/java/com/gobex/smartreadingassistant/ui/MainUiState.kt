package com.gobex.smartreadingassistant.ui

import android.graphics.Bitmap
import com.gobex.smartreadingassistant.feature.ocr.data.model.OcrResult
import com.gobex.smartreadingassistant.feature.ocr.test.OcrTestReport

/**
 * UI State for the main screen
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val selectedImage: Bitmap? = null,
    val ocrResult: OcrResult? = null,
    val error: String? = null,
    val testReport: OcrTestReport? = null,
    val isTestRunning: Boolean = false
)

