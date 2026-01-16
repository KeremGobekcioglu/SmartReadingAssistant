# Smart Reading Assistant - Android Application Architecture

## Table of Contents
- [Overview](#overview)
- [High-Level Architecture](#high-level-architecture)
- [Tech Stack](#tech-stack)
- [Data Flow](#data-flow)
- [Module Structure](#module-structure)
- [Dependency Injection](#dependency-injection)
- [Key Components](#key-components)
- [Integration Points](#integration-points)

---

## Overview

Smart Reading Assistant is an Android application that connects to ESP32-based smart glasses to provide AI-powered visual assistance. The app captures images from the wearable camera, processes them through Google Gemini AI, and delivers streaming audio responses.

**Architecture Pattern**: Clean Architecture with MVVM (Model-View-ViewModel)

**Key Features**:
- Zero-configuration IoT device pairing (BLE → WiFi handshake)
- Real-time AI streaming responses
- Multi-modal input (voice + image)
- Accessibility-first design with voice commands
- Conversation history persistence

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ MainActivity │  │ Compose UI   │  │  ViewModels  │     │
│  │              │  │  Screens     │  │   (Hilt)     │     │
│  └──────────────┘  └──────────────┘  └──────┬───────┘     │
└──────────────────────────────────────────────┼─────────────┘
                                               │
┌──────────────────────────────────────────────┼─────────────┐
│                    Domain Layer              │              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────▼───────┐     │
│  │ Repositories │  │    Models    │  │  Use Cases   │     │
│  │ (Interfaces) │  │   (Domain)   │  │  (Business)  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└──────────────────────────────────────────────┬─────────────┘
                                               │
┌──────────────────────────────────────────────┼─────────────┐
│                     Data Layer               │              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────▼───────┐     │
│  │ Repository   │  │  Data Sources│  │     DTOs     │     │
│  │ Impl         │  │  (Local/API) │  │   (Mappers)  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

### Core Technologies
| Component | Technology | Version/Details |
|-----------|-----------|-----------------|
| **Language** | Kotlin | 100% Kotlin codebase |
| **UI Framework** | Jetpack Compose | Material 3 design system |
| **Architecture** | Clean Architecture + MVVM | Feature-based modular structure |
| **Dependency Injection** | Dagger Hilt | `@HiltAndroidApp`, `@HiltViewModel` |
| **Navigation** | Jetpack Navigation Compose | Type-safe routes with kotlinx.serialization |
| **Async** | Kotlin Coroutines + Flow | StateFlow, SharedFlow, Channel |
| **Networking** | Retrofit 2 + OkHttp3 | Custom SSE streaming support |
| **Database** | Room | SQLite abstraction with migration support |
| **JSON** | Gson | API serialization/deserialization |

### Specialized Libraries
| Feature | Library |
|---------|---------|
| **Speech-to-Text** | Android SpeechRecognizer API |
| **Text-to-Speech** | Android TextToSpeech API |
| **Bluetooth LE** | Nordic Semiconductor BLE Library (`no.nordicsemi.android.ble.ktx`) |
| **Hotspot** | Android LocalOnlyHotspot API |
| **OCR (Planned)** | Google ML Kit |

---

## Data Flow

### Complete User Journey: Voice-Activated Image Analysis

```
┌──────────────────────────────────────────────────────────────┐
│ 1. DEVICE CONNECTION PHASE                                    │
└──────────────────────────────────────────────────────────────┘

MainActivity.onCreate()
    ↓
ConversationViewModel.init()
    ↓
DeviceConnectionManager.attemptReconnectToSavedConnection()
    ├─→ Query Room DB for last active connection
    ├─→ DeviceRepository.pingDevice(savedIP)
    │   └─→ Success? → Reconnect immediately ✅
    │   └─→ Failed? → Start BLE handshake ⬇️
    │
    ├─→ HotspotManager.startHotspot()
    │   └─→ Creates WiFi hotspot: "DIRECT-xy-AndroidAP"
    │
    ├─→ BleConnectionManager.startConnectionSequence(ssid, pass)
    │   ├─→ Scan for BLE device (UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E)
    │   ├─→ Connect to ESP32
    │   ├─→ Write WiFi credentials via BLE GATT
    │   ├─→ Observe deviceIpFlow (ESP32 sends its IP)
    │   └─→ ⚡ BLE handshake completes in 2-3 seconds
    │
    └─→ DeviceRepository.connectToDeviceServer(ip)
        └─→ Creates Retrofit instance: http://{ip}/

📝 Note: Network scanning fallback (commented out in code) is not used.
    Connection is BLE-only for simplicity and speed.

┌──────────────────────────────────────────────────────────────┐
│ 2. IMAGE CAPTURE PHASE                                        │
└──────────────────────────────────────────────────────────────┘

User: Presses "Capture Photo" button
    ↓
ConversationViewModel.captureImageOnly()
    ↓
DeviceRepository.captureImage()
    ├─→ Esp32ApiService.captureImage()
    │   └─→ GET http://{ip}/capture
    │   └─→ Returns: ResponseBody (JPEG bytes)
    │
    └─→ Saves to: state.capturedImageBytes
    └─→ Shows image preview dialog
    └─→ TTS: "Photo captured. What would you like to know?"

┌──────────────────────────────────────────────────────────────┐
│ 3. VOICE INPUT PHASE                                          │
└──────────────────────────────────────────────────────────────┘

User: Holds microphone button
    ↓
ConversationViewModel.startListening()
    ├─→ ttsManager.stop() (Interrupt any AI speech)
    ├─→ delay(300ms) (Let speaker hardware settle)
    └─→ AndroidSpeechToTextManager.startListening()
        └─→ SpeechRecognizer starts (supports en-US, tr-TR)

User: Speaks "What text is on this sign?"
    ↓
SttState.Result("What text is on this sign?")
    ↓
ConversationViewModel.handleVoiceCommand()
    ├─→ VoiceCommandParser.parse(text)
    └─→ Parsed as: VoiceCommand.SendToAI(text)
    └─→ sendPrompt(text, imageBase64)

┌──────────────────────────────────────────────────────────────┐
│ 4. AI PROCESSING PHASE (Streaming)                           │
└──────────────────────────────────────────────────────────────┘

ConversationViewModel.sendPrompt(text, imageBase64?)
    ↓
Create: Message(USER, text, imageBase64)
    ↓
Add to state.messages
    ↓
LLMRepositoryImpl.streamMessage(text, imageBase64, history)
    │
    ├─→ Step 1: Upload Image (if present)
    │   ├─→ Check if image already uploaded (check history for fileUri)
    │   ├─→ If new: LLMApiService.uploadFile()
    │   │   └─→ POST https://generativelanguage.googleapis.com/upload/v1beta/files
    │   │   └─→ Returns: FileUploadResponse.file.uri
    │   │   └─→ Cache in Message.fileUri (reuse for follow-ups)
    │   └─→ If exists: Reuse cached URI
    │
    ├─→ Step 2: Build Request
    │   ├─→ Include conversationHistory (previous messages)
    │   ├─→ Add parts: [fileData(uri) OR inlineData(base64), text]
    │   └─→ Create GeminiRequestDto
    │
    └─→ Step 3: Stream Response
        └─→ LLMApiService.streamGenerateContent()
            ├─→ POST /v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse
            ├─→ OkHttp streaming (readTimeout = 0 for infinite streaming)
            └─→ Server-Sent Events parsing:
                ├─→ Read line-by-line: "data: {...}"
                ├─→ Gson → GeminiResponseDto
                └─→ Emit StreamResult.Chunk(text)

┌──────────────────────────────────────────────────────────────┐
│ 5. RESPONSE DELIVERY PHASE                                    │
└──────────────────────────────────────────────────────────────┘

ViewModel collects Flow<StreamResult>
    │
    ├─→ StreamResult.Chunk(text)
    │   ├─→ Append to state.currentText (UI updates incrementally)
    │   ├─→ Buffer words until space/newline detected
    │   └─→ TextToSpeechManager.speak(buffer) (word-by-word TTS)
    │
    └─→ StreamResult.Complete(fullMessage, metadata, uploadedFileUri)
        ├─→ Save Message(ASSISTANT, fullText) to Room DB
        ├─→ ConversationDAO.insertMessage(MessageEntity)
        └─→ Update state with complete message

┌──────────────────────────────────────────────────────────────┐
│ 6. PERSISTENCE PHASE                                          │
└──────────────────────────────────────────────────────────────┘

Room Database: smart_glasses_db
    ├─→ MessageEntity(id, role, text, imageBase64, fileUri, timestamp, tokenCount)
    └─→ DeviceConnectionEntity(id, deviceIp, ssid, connectedAt, isActive)
```

---

## Module Structure

### 📂 Project Organization

```
app/src/main/java/com/gobex/smartreadingassistant/
│
├── 📦 core/                          # Shared infrastructure
│   ├── audio/                        # Speech interfaces
│   │   ├── AudioInterfaces.kt        # SpeechToTextManager, TextToSpeechManager
│   │   ├── STTImpl.kt                # AndroidSpeechToTextManager
│   │   └── TTSImpl.kt                # AndroidTextToSpeechManager
│   │
│   ├── connectivity/                 # IoT device management
│   │   ├── ConnectionManager.kt      # DeviceConnectionManager (orchestrator)
│   │   ├── BLEManager.kt             # BleConnectionManager (BLE handshake)
│   │   ├── Esp32BleManager.kt        # Nordic BLE implementation
│   │   ├── HotSpotManager.kt         # Android hotspot API wrapper
│   │   ├── HotSpotService.kt         # Foreground service
│   │   └── NetworkScanner.kt         # Unused (network fallback removed)
│   │
│   ├── db/                           # Local database
│   │   └── AppDatabase.kt            # Room database configuration
│   │
│   ├── di/                           # Dependency injection modules
│   │   ├── AudioModule.kt            # Audio bindings
│   │   ├── NetworkModule.kt          # Retrofit + OkHttp providers
│   │   ├── DatabaseModule.kt         # Room DB + DAOs
│   │   └── RepositoryModule.kt       # Repository bindings
│   │
│   ├── navigation/                   # Navigation definitions
│   │   └── AppNavigation.kt          # Type-safe routes
│   │
│   ├── util/                         # Utilities
│   │   ├── Constants.kt              # API keys, URLs
│   │   └── Extensions.kt             # Kotlin extensions
│   │
│   └── shared/                       # Shared models
│       └── model/
│           └── ProcessedDocument.kt  # Cross-feature models
│
├── 📦 feature/                       # Feature modules (Clean Architecture)
│   │
│   ├── conversation/                 # AI chat feature
│   │   ├── presentation/             # UI Layer
│   │   │   ├── ConversationViewModel.kt     # Core business orchestrator
│   │   │   ├── ConversionState.kt           # UI state data class
│   │   │   ├── ConversationEffect.kt        # One-shot UI events
│   │   │   ├── ConnectScreen.kt             # Device connection UI
│   │   │   └── screens/
│   │   │       ├── AccessibleUserScreen.kt  # Voice-first accessibility UI
│   │   │       ├── ChatTestScreen.kt        # Debug chat interface
│   │   │       └── HistoryScreen.kt         # Conversation history viewer
│   │   │
│   │   ├── domain/                   # Business Logic Layer
│   │   │   ├── LLMRepository.kt             # Repository interface
│   │   │   ├── AppModels.kt                 # Message, MessageRole, etc.
│   │   │   ├── StreamResult.kt              # Sealed class: Chunk/Complete/Error
│   │   │   ├── RequestClasses.kt            # API request models
│   │   │   ├── ResponseClasses.kt           # API response models
│   │   │   └── Mappers.kt                   # DTO ↔ Domain mappers
│   │   │
│   │   └── data/                     # Data Layer
│   │       ├── repository/
│   │       │   └── LLMRepositoryImpl.kt     # Streaming + image upload logic
│   │       ├── source/
│   │       │   ├── ConversationDAO.kt       # Room DAO
│   │       │   ├── ConversationLocalDataSource.kt  # Interface
│   │       │   ├── DBImpl.kt                # Room implementation
│   │       │   └── entities/
│   │       │       └── MessageEntity.kt     # Database entity
│   │       ├── model/                       # DTOs for API
│   │       │   └── (Gemini API DTOs)
│   │       └── LLMApiService.kt             # Retrofit interface
│   │
│   ├── device/                       # ESP32 hardware control
│   │   ├── ESPCODE                   # ESP32-CAM firmware (C++)
│   │   ├── data/
│   │   │   ├── Esp32ApiService.kt           # Retrofit API definition
│   │   │   ├── repository/
│   │   │   │   └── DeviceRepository.kt      # Camera control logic
│   │   │   └── local/
│   │   │       ├── DeviceConnectionDao.kt   # Room DAO
│   │   │       └── DeviceConnectionEntity.kt
│   │   ├── domain/                   # (Reserved for future use cases)
│   │   └── service/                  # (Background services)
│   │
│   ├── ocr/                          # Text extraction (planned feature)
│   │   ├── domain/
│   │   │   ├── OcrEngine.kt                 # OCR interface
│   │   │   └── DocumentAnalyzer.kt          # Text processing logic
│   │   ├── ml/
│   │   │   └── MlKitOcrProcessor.kt         # Google ML Kit integration
│   │   ├── data/
│   │   │   ├── repository/
│   │   │   │   └── OcrRepository.kt
│   │   │   └── model/
│   │   │       └── OcrResult.kt
│   │   └── test/
│   │       └── OcrTestRunner.kt
│   │
│   └── preprocess/                   # Image preprocessing (stub)
│       ├── Preprocess.kt
│       └── a.kt
│
├── 📦 ui/                            # Global UI components
│   ├── components/                   # Reusable Compose components
│   ├── theme/                        # Material 3 theming
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── MainScreen.kt                 # Main app screen
│   ├── MainViewModel.kt              # Main ViewModel
│   └── MainUiState.kt                # Main UI state
│
├── MainActivity.kt                   # Single Activity entry point
└── SmartReadingApp.kt                # Application class (@HiltAndroidApp)
```

---

## Dependency Injection

### Hilt Module Structure

#### 1. AudioModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds @Singleton
    abstract fun bindSpeechToTextManager(
        impl: AndroidSpeechToTextManager
    ): SpeechToTextManager

    @Binds @Singleton
    abstract fun bindTextToSpeechManager(
        impl: AndroidTextToSpeechManager
    ): TextToSpeechManager
}
```

#### 2. NetworkModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)  // Infinite for streaming
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(httpClient: OkHttpClient): Retrofit

    @Provides @Singleton
    fun provideLLMApiService(retrofit: Retrofit): LLMApiService

    @Provides @Singleton
    fun provideGson(): Gson
}
```

#### 3. DatabaseModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase

    @Provides @Singleton
    fun provideConversationDAO(database: AppDatabase): ConversationDAO

    @Provides @Singleton
    fun provideDeviceConnectionDao(database: AppDatabase): DeviceConnectionDao
}
```

#### 4. RepositoryModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindLLMRepository(
        impl: LLMRepositoryImpl
    ): LLMRepository

    @Binds @Singleton
    abstract fun bindConversationLocalDataSource(
        impl: DBImpl
    ): ConversationLocalDataSource
}
```

### Injection Scopes

| Scope | Components | Lifecycle |
|-------|-----------|-----------|
| `SingletonComponent` | Managers, Repositories, DAOs | Application lifetime |
| `ViewModelComponent` | ViewModels (`@HiltViewModel`) | Configuration change survival |
| `ActivityRetainedComponent` | Shared activity data | Activity recreation survival |

---

## Key Components

### 1. ConversationViewModel (749 lines)

**Location**: `feature/conversation/presentation/ConversationViewModel.kt`

**Responsibilities**:
- Orchestrates entire user flow (connection → capture → AI → TTS)
- Manages UI state via StateFlow
- Coordinates 5+ managers (Device, STT, TTS, LLM, DB)
- Implements voice command parsing
- Handles accessibility mode

**Key Dependencies**:
```kotlin
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val repository: LLMRepository,
    private val connectionManager: DeviceConnectionManager,
    private val deviceRepository: DeviceRepository,
    private val sttManager: SpeechToTextManager,
    private val ttsManager: TextToSpeechManager
)
```

**State Management**:
```kotlin
data class ConversionState(
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val currentText: String = "",
    val capturedImageBytes: ByteArray? = null,
    val currentImageUri: String? = null,
    val isAccessibilityMode: Boolean = false,
    val isVoiceAnnouncementsEnabled: Boolean = false,
    ...
)
```

### 2. DeviceConnectionManager

**Location**: `core/connectivity/ConnectionManager.kt`

**Responsibilities**:
- Orchestrates BLE + WiFi + Hotspot
- Auto-reconnect to last known device
- Health check monitoring (10-second intervals)
- Connection state broadcasting

**State Machine**:
```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val step: String) : ConnectionState()
    data class Connected(val ip: String, val connectionId: Long) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

**Flow**:
1. Check Room DB for saved connection
2. Ping last IP → Success? Connect directly
3. Failed? → Start BLE handshake
   - HotspotManager.startHotspot()
   - BleConnectionManager.startConnectionSequence()
   - Wait for ESP32's IP via BLE notification (2-3 seconds)
4. Save connection to Room DB
5. Start heartbeat monitoring

**Note**: Network scanning fallback was removed. Connection is BLE-only.

### 3. LLMRepositoryImpl

**Location**: `feature/conversation/data/repository/LLMRepositoryImpl.kt`

**Responsibilities**:
- Streaming response handling (SSE parsing)
- Image upload to Gemini API
- Smart URI caching (avoid re-uploading images)
- Conversation persistence

**Key Algorithm: Smart Image Handling**
```kotlin
suspend fun streamMessage(...): Flow<StreamResult> {
    // 1. Check if image already uploaded
    val existingUri = conversationHistory.findLast { 
        it.imageBase64 == imageBase64 && it.fileUri != null 
    }?.fileUri
    
    // 2. Upload only if new
    val currentFileUri = existingUri ?: uploadImageToGemini(imageBytes)
    
    // 3. Build request with URI (not Base64)
    val parts = mutableListOf<PartDto>()
    if (currentFileUri != null) {
        parts.add(PartDto(fileData = FileDataRequestDto("image/jpeg", currentFileUri)))
    }
    
    // 4. Stream response...
}
```

**SSE Parsing**:
```kotlin
responseBody.byteStream().bufferedReader().use { reader ->
    while (true) {
        val line = reader.readLine() ?: break
        if (line.startsWith("data: ")) {
            val jsonData = line.substring(6)
            val chunk = gson.fromJson(jsonData, GeminiResponseDto::class.java)
            val text = chunk.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            emit(StreamResult.Chunk(text))
        }
    }
}
```

### 4. DeviceRepository

**Location**: `feature/device/data/repository/DeviceRepository.kt`

**Responsibilities**:
- Dynamic Retrofit instance management (per-IP)
- Camera capture with flash control
- Fast health checks (2-second timeout)

**Key Methods**:
```kotlin
fun connectToDeviceServer(ipAddress: String) {
    // Creates http://{ip}/ base URL
    val retrofit = Retrofit.Builder()
        .baseUrl("http://$ipAddress/")
        .client(client)
        .build()
    apiService = retrofit.create(Esp32ApiService::class.java)
}

suspend fun captureImage(): Result<ByteArray> {
    val response = apiService.captureImage()  // GET /capture
    return if (response.isSuccessful) {
        Result.success(response.body()!!.bytes())
    } else {
        Result.failure(Exception("Capture failed"))
    }
}

suspend fun pingDevice(ipAddress: String): Boolean {
    // Fast 2-second timeout
    val response = pingService.ping()  // GET /ping
    return response.isSuccessful
}
```

---

## Integration Points

### 1. Google Gemini AI API

**Base URL**: `https://generativelanguage.googleapis.com/`

**Endpoints Used**:

#### Streaming Chat
```http
POST /v1beta/models/gemini-2.5-flash:streamGenerateContent?key={API_KEY}&alt=sse
Content-Type: application/json

{
  "contents": [
    {
      "role": "user",
      "parts": [
        {"fileData": {"mimeType": "image/jpeg", "fileUri": "https://..."}},
        {"text": "What's in this image?"}
      ]
    }
  ]
}
```

**Response**: Server-Sent Events
```
data: {"candidates":[{"content":{"parts":[{"text":"The "}]}}]}

data: {"candidates":[{"content":{"parts":[{"text":"image "}]}}]}

data: [DONE]
```

#### Image Upload
```http
POST /upload/v1beta/files?key={API_KEY}&uploadType=multipart
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{"file": {"displayName": "image_1234567890.jpg"}}

--boundary
Content-Disposition: form-data; name="file"; filename="upload.jpg"
Content-Type: image/jpeg

<JPEG bytes>
--boundary--
```

**Response**:
```json
{
  "file": {
    "name": "files/abc123",
    "uri": "https://generativelanguage.googleapis.com/v1beta/files/abc123",
    "mimeType": "image/jpeg",
    "sizeBytes": "45320",
    "createTime": "2024-01-09T10:30:00Z",
    "expirationTime": "2024-01-11T10:30:00Z"
  }
}
```

**Authentication**: Query parameter `?key={GEMINI_API_KEY}`

**Special Configuration**:
```kotlin
OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS)  // Infinite for streaming
    .connectTimeout(30, TimeUnit.SECONDS)
    .callTimeout(5, TimeUnit.MINUTES)
```

### 2. ESP32-CAM Device

**Communication**: HTTP REST API (dynamic base URL)

**Endpoints**:

| Method | Endpoint | Purpose | Response |
|--------|----------|---------|----------|
| GET | `/capture` | Capture JPEG image | `image/jpeg` (binary) |
| GET | `/control?var=flash&val=1` | Control hardware | `text/plain` "OK" |
| GET | `/ping` | Health check | `text/plain` "PONG" |
| GET | `/status` | Camera status | `text/plain` "READY" |
| GET | `/` | Web dashboard | `text/html` |

**Retrofit Interface**:
```kotlin
interface Esp32ApiService {
    @GET("capture")
    suspend fun captureImage(): Response<ResponseBody>

    @GET("control")
    suspend fun controlDevice(
        @Query("var") variable: String,
        @Query("val") value: Int
    ): Response<ResponseBody>

    @GET("ping")
    suspend fun ping(): Response<ResponseBody>
}
```

### 3. Bluetooth LE Protocol

**Library**: Nordic Semiconductor Android BLE Library

**Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` (UART Service)

**Characteristics**:
- **RX** (`6E400002-...`): Android writes WiFi credentials
- **TX** (`6E400003-...`): ESP32 notifies IP address

**Flow**:
```kotlin
// 1. Scan for ESP32
val scanner = bluetoothAdapter.bluetoothLeScanner
scanner.startScan(listOf(filter), settings, scanCallback)

// 2. Connect
bleManager.connect(device)
    .retry(3, 100)
    .timeout(10000)
    .suspend()

// 3. Listen for IP
bleManager.observeIpNotifications().collect { ip ->
    deviceIpFlow.emit(ip)
}

// 4. Send credentials
bleManager.sendWifiCredentials(ssid, password)
```

### 4. Android Platform APIs

#### Speech Recognition
```kotlin
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
             RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, 
             arrayListOf("en-US", "tr-TR"))
}
speechRecognizer.startListening(intent)
```

#### Text-to-Speech
```kotlin
textToSpeech.speak(
    text,
    TextToSpeech.QUEUE_ADD,
    null,
    "utteranceId"
)
```

#### LocalOnlyHotspot
```kotlin
wifiManager.startLocalOnlyHotspot(object : LocalOnlyHotspotCallback() {
    override fun onStarted(reservation: LocalOnlyHotspotReservation?) {
        val config = reservation?.softApConfiguration
        val ssid = config?.ssid
        val password = config?.passphrase
    }
}, null)
```

---

## Design Patterns & Best Practices

### 1. Reactive State Management
```kotlin
// ViewModel exposes StateFlow (read-only)
val state: StateFlow<ConversionState> = _state.asStateFlow()

// UI observes and reacts
LaunchedEffect(Unit) {
    viewModel.state.collect { state ->
        // Update UI based on state
    }
}
```

### 2. One-Shot Events (UI Effects)
```kotlin
// ViewModel uses Channel for one-time events
private val _uiEffect = Channel<ConversationEffect>()
val uiEffect = _uiEffect.receiveAsFlow()

// Emit event
_uiEffect.send(ConversationEffect.ShowError("Connection failed"))

// UI consumes once
LaunchedEffect(Unit) {
    viewModel.uiEffect.collect { effect ->
        when (effect) {
            is ConversationEffect.ShowError -> showSnackbar(effect.message)
        }
    }
}
```

### 3. Repository Pattern with Flow
```kotlin
interface LLMRepository {
    suspend fun streamMessage(...): Flow<StreamResult>
}

// Sealed class for streaming states
sealed class StreamResult {
    data class Chunk(val text: String) : StreamResult()
    data class Complete(val fullMessage: Message, ...) : StreamResult()
    data class Error(val exception: Exception) : StreamResult()
}
```

### 4. Clean Architecture Layers
```
Presentation → Domain ← Data
     ↓           ↓        ↓
  ViewModel  Interface  Impl
```

**Benefits**:
- Testability (mock interfaces)
- Separation of concerns
- Framework independence (domain layer is pure Kotlin)

---

## Performance Optimizations

### 1. Image Caching Strategy
- **Problem**: Gemini API multipart upload is slow (~2-3 seconds)
- **Solution**: Upload once, reuse URI in conversation history
- **Benefit**: Follow-up questions on same image are instant

### 2. TTS Streaming
- **Problem**: Waiting for full AI response delays speech
- **Solution**: Speak word-by-word as chunks arrive
- **Implementation**: Buffer until space/newline, then speak

### 3. Connection Persistence
- **Problem**: BLE handshake takes 2-3 seconds + hotspot setup
- **Solution**: Save device IP to Room DB, ping on next launch
- **Benefit**: Reconnect in <2 seconds if device still online

### 4. Hotspot Service
- **Problem**: Android kills background processes
- **Solution**: Foreground Service keeps hotspot alive
- **Implementation**: `HotspotService` with notification

---

## Error Handling

### Network Errors
```kotlin
try {
    repository.streamMessage(...)
} catch (e: Exception) {
    emit(StreamResult.Error(e))
    _uiEffect.send(ConversationEffect.ShowError(e.message))
}
```

### Device Connection Errors
```kotlin
sealed class ConnectionState {
    data class Error(val message: String) : ConnectionState()
}

// Auto-recovery via health checks
suspend fun performHealthCheck() {
    val isAlive = deviceRepository.pingDevice(ip)
    if (!isAlive) {
        connectionDao.deactivateAll()
        connect()  // Restart BLE handshake
    }
}
```

### Speech Recognition Errors
```kotlin
when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> {
        _state.update { SttState.Idle }  // Silent fail
    }
    5 -> {  // Client error
        _state.update { SttState.Error(message, shouldSpeak = false) }
    }
    else -> {
        _state.update { SttState.Error(message, shouldSpeak = true) }
    }
}
```

---

## Testing Strategy (Recommended)

### Unit Tests
```kotlin
@Test
fun `streamMessage should emit chunks then complete`() = runTest {
    // Mock repository
    val repository = FakeLLMRepository()
    
    // Test streaming
    repository.streamMessage("test").test {
        assertThat(awaitItem()).isInstanceOf<StreamResult.Chunk>()
        assertThat(awaitItem()).isInstanceOf<StreamResult.Complete>()
        awaitComplete()
    }
}
```

### Integration Tests
```kotlin
@HiltAndroidTest
class ConnectionFlowTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var connectionManager: DeviceConnectionManager
    
    @Test
    fun `should connect to saved device on launch`() = runTest {
        // Test auto-reconnect logic
    }
}
```

---

## Security Considerations

### Current State
1. **API Keys**: Stored in `BuildConfig` (build-time injection)
2. **BLE**: Short-range communication (10m max)
3. **Hotspot**: Randomized credentials per session
4. **HTTP**: Plain text to ESP32 (local network only)

### Recommendations
1. **Android Keystore**: Encrypt sensitive credentials
2. **Certificate Pinning**: Verify Gemini API certificates
3. **ESP32 HTTPS**: Use mbedTLS (requires more memory)
4. **API Token**: Add authentication to ESP32 endpoints

---

## Future Enhancements

### 1. OCR Module Integration
- Google ML Kit text recognition
- On-device processing (no internet required)
- LayoutLM model integration (already in SROIE2019/ folder)

### 2. Multi-Device Support
- Connect to multiple ESP32 devices
- Switch between devices in UI

### 3. Conversation Management
- Session-based conversations
- Export/import chat history
- Search functionality

### 4. Advanced Voice Commands
- "Summarize last 3 messages"
- "Export this conversation"
- "Switch to device #2"

---

## Appendix: File References

### Critical Files
- `MainActivity.kt` (107 lines) - Entry point
- `ConversationViewModel.kt` (749 lines) - Core orchestrator
- `LLMRepositoryImpl.kt` (270 lines) - AI streaming logic
- `ConnectionManager.kt` (241 lines) - Device connectivity
- `AppDatabase.kt` (21 lines) - Room configuration

### Configuration Files
- `build.gradle` - Dependencies and SDK versions
- `AndroidManifest.xml` - Permissions and services
- `Constants.kt` - API keys and URLs

### Navigation Routes
```kotlin
sealed interface Route {
    @Serializable data object Connect : Route
    @Serializable data object Chat : Route
    @Serializable data object AccessibleChat : Route
    @Serializable data object History : Route
    @Serializable data class ConversationDetail(val conversationId: String) : Route
}
```

---

**Document Version**: 1.1  
**Last Updated**: January 9, 2026  
**Maintainer**: Kerem Göbekcioğlu

**Changelog v1.1**:
- Fixed BLE handshake timing (2-3 seconds, not 10-15)
- Clarified NetworkScanner is unused (fallback removed)
- Updated connection flow diagrams
