package com.gobex.smartreadingassistant.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gobex.smartreadingassistant.feature.ocr.data.repository.OcrRepository
import com.gobex.smartreadingassistant.feature.ocr.test.OcrTestRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the main screen
 */
class MainViewModel(
    private val ocrRepository: OcrRepository = OcrRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var testRunner: OcrTestRunner? = null

    /**
     * Process an image with OCR
     */
    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, selectedImage = bitmap) }

            val result = ocrRepository.processImage(bitmap)

            result.fold(
                onSuccess = { ocrResult ->
                    Log.d("MainViewModel", "OCR Success: ${ocrResult.text}")
                    Log.d("MainViewModel", "Processing time: ${ocrResult.processingTimeMs}ms")
                    Log.d("MainViewModel", "Blocks found: ${ocrResult.blocks.size}")

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            ocrResult = ocrResult,
                            error = null
                        )
                    }
                },
                onFailure = { exception ->
                    Log.e("MainViewModel", "OCR Error: ${exception.message}", exception)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Unknown error occurred"
                        )
                    }
                }
            )
        }
    }

    /**
     * Run OCR test on SROIE dataset
     * @param context Android context
     * @param datasetPath Path to SROIE2019 folder (e.g., /sdcard/Download/SROIE2019)
     * @param maxTests Maximum number of tests to run (default 20)
     */
    fun runOcrTest(context: Context, datasetPath: String, maxTests: Int = 20) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestRunning = true, error = null, testReport = null) }

            try {
                // Initialize test runner
                testRunner = OcrTestRunner(context)

                // Try test folder first, then train folder
                val testFolder = File(datasetPath, "test")
                val trainFolder = File(datasetPath, "train")

                val folder = when {
                    testFolder.exists() -> testFolder
                    trainFolder.exists() -> trainFolder
                    else -> {
                        _uiState.update {
                            it.copy(
                                isTestRunning = false,
                                error = "SROIE dataset not found at: $datasetPath\n" +
                                        "Please ensure the dataset is in:\n" +
                                        "- $datasetPath/test/img/ or\n" +
                                        "- $datasetPath/train/img/"
                            )
                        }
                        return@launch
                    }
                }

                Log.d("MainViewModel", "Running OCR test on: ${folder.absolutePath}")

                val report = testRunner?.runTests(folder, maxTests)

                Log.d("MainViewModel", "Test completed: ${report?.totalTests} tests")
                Log.d("MainViewModel", "Success rate: ${report?.successRate}")
                Log.d("MainViewModel", "Average CER: ${report?.averageCER}")

                _uiState.update {
                    it.copy(
                        isTestRunning = false,
                        testReport = report,
                        error = null
                    )
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Test error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isTestRunning = false,
                        error = "Test failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Clear the current OCR result
     */
    fun clearResult() {
        _uiState.update { MainUiState() }
    }

    /**
     * Clear test report
     */
    fun clearTestReport() {
        _uiState.update { it.copy(testReport = null) }
    }

    override fun onCleared() {
        super.onCleared()
        ocrRepository.cleanup()
        testRunner?.cleanup()
    }
}

