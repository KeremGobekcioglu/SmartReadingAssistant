# Smart Reading Assistant - ESP32-CAM Firmware Documentation

## Table of Contents
- [Overview](#overview)
- [Hardware Platform](#hardware-platform)
- [Firmware Architecture](#firmware-architecture)
- [Boot Sequence](#boot-sequence)
- [BLE Handshake Protocol](#ble-handshake-protocol)
- [HTTP API Endpoints](#http-api-endpoints)
- [Camera System](#camera-system)
- [WiFi Watchdog](#wifi-watchdog)
- [Memory Management](#memory-management)
- [Integration with Android](#integration-with-android)
- [Troubleshooting](#troubleshooting)

---

## Overview

The ESP32-CAM firmware transforms the device into a **smart camera server** that:
1. Advertises itself via Bluetooth LE (BLE)
2. Receives WiFi credentials from Android app
3. Connects to Android's hotspot
4. Serves images via HTTP REST API
5. Auto-restarts on connection loss

**File**: `feature/device/ESPCODE` (466 lines, C++)  
**Platform**: Arduino/ESP-IDF  
**Version**: v4.1

---

## Hardware Platform

### ESP32-CAM (AI-Thinker)

**Specifications**:
- **MCU**: ESP32 (Dual-core Xtensa LX6 @ 240 MHz)
- **RAM**: 520 KB SRAM (320 KB available after bootloader)
- **Flash**: 4 MB (configured as "Huge APP 3MB No OTA")
- **Camera**: OV2640 (2MP JPEG encoder)
- **Connectivity**: 
  - WiFi 802.11 b/g/n (2.4 GHz)
  - Bluetooth 4.2 LE
- **Flash LED**: GPIO 4 (white LED for illumination)

### Pin Configuration

```cpp
// Camera Pins (OV2640)
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26  // I2C Data
#define SIOC_GPIO_NUM     27  // I2C Clock
#define Y9_GPIO_NUM       35  // Data bit 9
#define Y8_GPIO_NUM       34  // Data bit 8
#define Y7_GPIO_NUM       39  // Data bit 7
#define Y6_GPIO_NUM       36  // Data bit 6
#define Y5_GPIO_NUM       21  // Data bit 5
#define Y4_GPIO_NUM       19  // Data bit 4
#define Y3_GPIO_NUM       18  // Data bit 3
#define Y2_GPIO_NUM        5  // Data bit 2
#define VSYNC_GPIO_NUM    25  // Vertical sync
#define HREF_GPIO_NUM     23  // Horizontal reference
#define PCLK_GPIO_NUM     22  // Pixel clock

// Flash LED
#define FLASH_LED_PIN      4  // White LED (active HIGH)
```

---

## Firmware Architecture

### State Machine

```
┌─────────────────────────────────────────────────────────────┐
│                    POWER ON / RESET                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  INITIALIZATION PHASE                                        │
│  - Disable brownout detector                                 │
│  - Initialize Serial (115200 baud)                           │
│  - Setup flash LED (GPIO 4)                                  │
│  - Start BLE advertising ("SmartGlasses")                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  WAITING FOR CREDENTIALS                                     │
│  - BLE advertising active                                    │
│  - Listening on RX characteristic                            │
│  - Loop: credentialsReady == false                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Android writes "SSID,PASSWORD"
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  WIFI CONNECTION PHASE                                       │
│  - WiFi.begin(ssid, password)                                │
│  - Wait up to 15 seconds (30 retries × 500ms)                │
│  - If failed: ESP.restart()                                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ WiFi.status() == WL_CONNECTED
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  IP NOTIFICATION PHASE                                       │
│  - Get IP: WiFi.localIP()                                    │
│  - Send via BLE TX characteristic: "IP:192.168.49.78"        │
│  - Shutdown BLE: BLEDevice::deinit()                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  CAMERA INITIALIZATION                                       │
│  - Configure OV2640 sensor                                   │
│  - Warm-up: Capture and discard 3 frames                     │
│  - Set cameraInitialized = true                              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  WEB SERVER ACTIVE                                           │
│  - HTTP server on port 80                                    │
│  - Endpoints: /capture, /control, /ping, /status, /         │
│  - WiFi watchdog monitoring (every 10ms)                     │
│  - Auto-restart if WiFi lost for >6 seconds                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Boot Sequence

### setup() Function (Lines 353-371)

```cpp
void setup() {
    // 1. Disable brownout detector (prevents random resets)
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
    
    // 2. Initialize serial communication
    Serial.begin(115200);
    delay(1000);
    
    // 3. Print startup banner
    logln("\n=== SmartGlasses v4.1 ===");
    logln("BLE UART: SmartGlasses");
    logln("IMPORTANT: Set Partition Scheme to 'Huge APP (3MB No OTA)'");
    
    // 4. Initialize flash LED (GPIO 4)
    setupFlash();
    
    // 5. Start BLE advertising
    setupBLE();
    
    logln("Ready for WiFi credentials!");
}
```

**Key Actions**:
1. **Brownout Disable**: Prevents ESP32 from resetting when voltage dips during camera capture
2. **Serial Debug**: 115200 baud for debugging via USB
3. **Flash LED**: Initialize GPIO 4 as output (LOW = off)
4. **BLE Advertising**: Start broadcasting as "SmartGlasses"

---

## BLE Handshake Protocol

### Service Definition (Nordic UART Service)

```cpp
#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define RX_CHARACTERISTIC   "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // Write
#define TX_CHARACTERISTIC   "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // Notify
```

**Why Nordic UART?**  
Industry-standard BLE service for simple bidirectional communication (like a wireless serial port).

### setupBLE() Function (Lines 274-306)

```cpp
void setupBLE() {
    BLEDevice::init("SmartGlasses");  // Device name visible in scan
    
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    
    BLEService *pService = pServer->createService(SERVICE_UUID);
    
    // RX: Android → ESP32 (WiFi credentials)
    pRxCharacteristic = pService->createCharacteristic(
        RX_CHARACTERISTIC,
        BLECharacteristic::PROPERTY_WRITE
    );
    pRxCharacteristic->setCallbacks(new MyCallbacks());
    
    // TX: ESP32 → Android (IP address)
    pTxCharacteristic = pService->createCharacteristic(
        TX_CHARACTERISTIC,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pTxCharacteristic->addDescriptor(new BLE2902());  // Enable notifications
    
    pService->start();
    BLEDevice::startAdvertising();
    logln("BLE Ready - Device: SmartGlasses");
}
```

### Data Exchange Protocol

#### 1. Android Writes Credentials (RX Characteristic)

**Format**: `SSID,PASSWORD` (comma-separated)

**Example**: `DIRECT-xy-AndroidAP,12345678`

**Callback** (Lines 246-272):
```cpp
class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String tempRxValue = pCharacteristic->getValue();
        const char* rawData = tempRxValue.c_str();
        
        // Parse: Find comma separator
        const char* commaPtr = strchr(rawData, ',');
        
        if (commaPtr != NULL) {
            size_t ssidLen = commaPtr - rawData;
            const char* passPtr = commaPtr + 1;
            size_t passLen = len - ssidLen - 1;
            
            // Copy to static buffers (safe from stack overflow)
            strncpy(ssid_buf, rawData, ssidLen);
            ssid_buf[ssidLen] = '\0';
            
            strncpy(pass_buf, passPtr, passLen);
            pass_buf[passLen] = '\0';
            
            credentialsReady = true;  // Trigger WiFi connection
            logln("Credentials received via BLE");
        }
    }
};
```

**Memory Safety**:
```cpp
char ssid_buf[33] = {0};  // Max SSID = 32 chars + null terminator
char pass_buf[65] = {0};  // Max WPA2 password = 64 chars + null
volatile bool credentialsReady = false;  // Volatile for ISR safety
```

#### 2. ESP32 Notifies IP Address (TX Characteristic)

**Format**: `IP:xxx.xxx.xxx.xxx`

**Example**: `IP:192.168.49.78`

**Code** (Lines 407-422):
```cpp
if (WiFi.status() == WL_CONNECTED) {
    String ipAddr = WiFi.localIP().toString();
    
    // Send IP via BLE notification
    if (deviceConnected && pTxCharacteristic != NULL) {
        String msg = "IP:" + ipAddr;
        
        Serial.print("Sending IP via BLE: ");
        Serial.println(msg);
        
        pTxCharacteristic->setValue(msg.c_str());
        pTxCharacteristic->notify();  // Push to Android
        
        delay(1000);  // Give Android time to receive
    }
}
```

#### 3. BLE Shutdown (Free Memory)

```cpp
// After IP sent, BLE is no longer needed
logln("Shutting down BLE to free memory...");
BLEDevice::deinit(true);  // Free ~100KB RAM
delay(500);
```

**Why Shutdown?**  
ESP32 has limited RAM (~320KB). BLE stack uses ~100KB. Camera DMA buffers need ~200KB. Sequential usage (BLE → Camera) solves this.

---

## HTTP API Endpoints

### WebServer Configuration (Lines 433-449)

```cpp
server.on("/", handleRoot);
server.on("/capture", handleCapture);
server.on("/status", handleStatus);
server.on("/control", handleControl);
server.on("/ping", handlePing);
server.begin();

Serial.println("\n=== Web Server Started ===");
Serial.print("Access camera at: http://");
Serial.println(ipAddr);
```

---

### 1. GET / (Root Dashboard)

**Handler**: `handleRoot()` (Lines 219-230)

**Response**: HTML page with links

```html
<!DOCTYPE html><html><body>
<h1>SmartGlasses Camera</h1>
<p>WiFi: Connected</p>
<p>Camera: Ready</p>
<p><a href='/capture'>Capture Image</a></p>
<p><a href='/status'>Status</a></p>
<p><a href='/control?var=flash&val=1'>Flash ON</a></p>
<p><a href='/control?var=flash&val=0'>Flash OFF</a></p>
</body></html>
```

**Purpose**: Human-readable interface for debugging

---

### 2. GET /capture (Image Capture)

**Handler**: `handleCapture()` (Lines 156-186)

**Flow**:
```cpp
void handleCapture() {
    // 1. Check camera ready
    if (!cameraInitialized) {
        server.send(503, "text/plain", "Camera not initialized");
        return;
    }
    
    // 2. Disable WiFi sleep (prevent disconnection during transfer)
    WiFi.setSleep(false);
    
    // 3. Capture frame
    camera_fb_t * fb = esp_camera_fb_get();
    if(!fb) {
        server.send(500, "text/plain", "Camera capture failed");
        return;
    }
    
    // 4. Write raw HTTP response (bypass WebServer for large payloads)
    WiFiClient client = server.client();
    client.write("HTTP/1.1 200 OK\r\n");
    client.write("Content-Type: image/jpeg\r\n");
    client.write("Content-Disposition: inline; filename=capture.jpg\r\n");
    client.write("Access-Control-Allow-Origin: *\r\n");
    client.write("Content-Length: ");
    client.print(fb->len);
    client.write("\r\n\r\n");
    client.write(fb->buf, fb->len);  // Send JPEG bytes
    
    // 5. Free framebuffer
    esp_camera_fb_return(fb);
}
```

**Response Format**:
```http
HTTP/1.1 200 OK
Content-Type: image/jpeg
Content-Disposition: inline; filename=capture.jpg
Access-Control-Allow-Origin: *
Content-Length: 45320

<JPEG bytes>
```

**Performance Optimization**:  
Direct `WiFiClient.write()` instead of `server.send()` avoids copying large buffers.

---

### 3. GET /control (Hardware Control)

**Handler**: `handleControl()` (Lines 189-209)

**Parameters**:
- `var` (string): Variable name (e.g., "flash")
- `val` (int): Value (0 or 1)

**Example**: `GET /control?var=flash&val=1`

**Code**:
```cpp
void handleControl() {
    if (!server.hasArg("var") || !server.hasArg("val")) {
        server.send(400, "text/plain", "Missing var or val parameter");
        return;
    }
    
    String variable = server.arg("var");
    int value = server.arg("val").toInt();
    
    if (variable == "flash") {
        setFlash(value == 1);  // GPIO 4 HIGH/LOW
        server.send(200, "text/plain", "OK");
    } else {
        server.send(400, "text/plain", "Unknown variable");
    }
}
```

**Flash Control**:
```cpp
void setFlash(bool state) {
    digitalWrite(FLASH_LED_PIN, state ? HIGH : LOW);
    Serial.print("Flash: ");
    Serial.println(state ? "ON" : "OFF");
}
```

**Extensibility**:  
Can add more variables (e.g., `?var=quality&val=10` to change JPEG quality).

---

### 4. GET /ping (Health Check)

**Handler**: `handlePing()` (Lines 215-218)

**Response**: `text/plain` "PONG"

**Purpose**: Fast health check for Android's `DeviceConnectionManager.performHealthCheck()`

**Code**:
```cpp
void handlePing() {
    server.send(200, "text/plain", "PONG");
}
```

**Timeout**: Android uses 2-second timeout for this endpoint.

---

### 5. GET /status (Camera Status)

**Handler**: `handleStatus()` (Lines 211-214)

**Response**: `"READY"` or `"CAMERA_NOT_INITIALIZED"`

**Code**:
```cpp
void handleStatus() {
    String status = cameraInitialized ? "READY" : "CAMERA_NOT_INITIALIZED";
    server.send(200, "text/plain", status);
}
```

---

## Camera System

### OV2640 Configuration (Lines 97-131)

```cpp
void setupCamera() {
    cameraInitialized = false;  // Not ready yet
    
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    // ... (pin assignments)
    config.xclk_freq_hz = 10000000;      // 10 MHz clock
    config.pixel_format = PIXFORMAT_JPEG; // Hardware JPEG encoder
    
    // Image settings
    config.frame_size = FRAMESIZE_SVGA;   // 800x600 pixels
    config.jpeg_quality = 15;             // 0-63 (lower = better quality)
    config.fb_count = 1;                  // Single frame buffer
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    
    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        logln("Camera init failed");
        return;
    }
    
    // CRITICAL: Warm-up sequence
    warmUpCamera();
    
    cameraInitialized = true;
    logln("Camera initialized successfully!");
}
```

### Camera Warm-Up (Lines 136-146)

**Problem**: First frames from OV2640 are often dark/corrupted due to:
- Auto-exposure not stabilized
- Auto-white-balance not calibrated
- Sensor power-on transients

**Solution**: Capture and discard 3 frames

```cpp
logln("Warming up camera sensor...");

for(int i = 0; i < 3; i++) {
    camera_fb_t * fb = esp_camera_fb_get();
    if(fb) {
        esp_camera_fb_return(fb);  // Immediately discard
        logln("Warm-up frame discarded");
    } else {
        logln("Warm-up frame capture failed");
    }
    delay(100);  // 100ms between captures
}

logln("Camera warm-up complete");
```

**Timing**: Total warm-up = 300ms (3 frames × 100ms)

**Impact**: Without this, Android app receives black/underexposed images.

### Image Quality Settings

| Parameter | Value | Notes |
|-----------|-------|-------|
| **Resolution** | SVGA (800×600) | Balance between quality and transfer speed |
| **JPEG Quality** | 15 | Lower = better (0-63 scale, inverted) |
| **Frame Buffer** | 1 | Single buffer (saves RAM) |
| **Clock Speed** | 10 MHz | Stable for most OV2640 modules |

**Typical Image Size**: 40-60 KB per capture

---

## WiFi Watchdog

### Auto-Restart on Connection Loss (Lines 309-349)

**Problem**: If Android hotspot dies, ESP32 becomes unreachable.

**Solution**: Monitor WiFi status every 10ms, restart if disconnected >6 seconds.

```cpp
#define WIFI_TIMEOUT_MS 6000  // 6 seconds

unsigned long wifiDisconnectedTime = 0;

void checkWiFiWatchdog() {
    wl_status_t status = WiFi.status();
    
    if (wifiConnected) {
        if (status != WL_CONNECTED) {
            // Connection lost!
            if (wifiDisconnectedTime == 0) {
                // First detection
                wifiDisconnectedTime = millis();
                logln("⚠️  WiFi connection lost! Starting watchdog timer...");
            } else {
                // Check duration
                unsigned long disconnectedDuration = millis() - wifiDisconnectedTime;
                
                if (disconnectedDuration >= WIFI_TIMEOUT_MS) {
                    // Timeout exceeded - restart!
                    Serial.println("\n❌ WiFi connection lost for too long!");
                    Serial.println("🔄 Restarting ESP32 to restore connectivity...\n");
                    delay(1000);
                    ESP.restart();  // Reboot
                } else {
                    // Log progress every 3 seconds
                    if (disconnectedDuration % 3000 == 0) {
                        Serial.print("⏳ Disconnected for ");
                        Serial.print(disconnectedDuration / 1000);
                        Serial.print("s / ");
                        Serial.print(WIFI_TIMEOUT_MS / 1000);
                        Serial.println("s");
                    }
                }
            }
        } else {
            // Connection restored!
            if (wifiDisconnectedTime != 0) {
                logln("✅ WiFi connection restored!");
                wifiDisconnectedTime = 0;  // Reset timer
            }
        }
    }
}
```

**Called in loop()** (Line 464):
```cpp
void loop() {
    // ... handle web requests ...
    checkWiFiWatchdog();
    delay(10);  // Check every 10ms
}
```

**Recovery Flow**:
1. WiFi lost → Start 6-second timer
2. Timer expires → `ESP.restart()`
3. ESP32 reboots → BLE advertising resumes
4. Android detects BLE → Re-initiates handshake

---

## Memory Management

### RAM Constraints

**Total SRAM**: 520 KB  
**Available after bootloader**: ~320 KB

**Memory Budget**:
| Component | Size | Notes |
|-----------|------|-------|
| BLE Stack | ~100 KB | Active during handshake only |
| Camera DMA Buffer | ~200 KB | SVGA JPEG framebuffer |
| WebServer | ~10 KB | HTTP request/response buffers |
| Application Code | ~10 KB | Global variables, stack |

**Problem**: BLE + Camera = 300 KB > 320 KB available

### Sequential Resource Usage

```
┌─────────────────────────────────────────────────────────┐
│  Boot → BLE Active (100 KB)                             │
│         Camera OFF                                       │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ Credentials received
                     ▼
┌─────────────────────────────────────────────────────────┐
│  WiFi Connected → BLEDevice::deinit() (Free 100 KB)     │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  Camera Active (200 KB)                                 │
│  BLE OFF                                                 │
└─────────────────────────────────────────────────────────┘
```

**Code** (Line 426):
```cpp
BLEDevice::deinit(true);  // Free BLE stack memory
delay(500);               // Let cleanup complete
setupCamera();            // Now enough RAM for camera
```

### Static Buffer Allocation

```cpp
// Avoid dynamic allocation (prevents fragmentation)
char ssid_buf[33] = {0};
char pass_buf[65] = {0};
volatile bool credentialsReady = false;
```

**Why `volatile`?**  
`credentialsReady` is modified in BLE callback (interrupt context) and read in `loop()` (main context). `volatile` prevents compiler optimization that could cause race conditions.

---

## Integration with Android

### Connection Flow Diagram

```
┌──────────────────────┐                  ┌──────────────────────┐
│   Android App        │                  │   ESP32-CAM          │
└──────────┬───────────┘                  └──────────┬───────────┘
           │                                         │
           │  1. Start Hotspot                       │
           │    "DIRECT-xy-HP" / "password123"       │
           │                                         │
           │  2. BLE Scan (UUID filter)              │
           │────────────────────────────────────────>│
           │                                         │
           │  3. BLE Advertising                     │
           │<────────────────────────────────────────│
           │    "SmartGlasses"                       │
           │                                         │
           │  4. BLE Connect                         │
           │────────────────────────────────────────>│
           │                                         │
           │  5. Enable TX Notifications             │
           │────────────────────────────────────────>│
           │                                         │
           │  6. Write RX: "DIRECT-xy-HP,password123"│
           │────────────────────────────────────────>│
           │                                         │
           │                                         │  WiFi.begin()
           │                                         │  Wait 15s...
           │                                         │
           │  7. TX Notification: "IP:192.168.49.78" │
           │<────────────────────────────────────────│
           │                                         │
           │                                         │  BLEDevice::deinit()
           │                                         │  setupCamera()
           │                                         │  server.begin()
           │                                         │
           │  8. HTTP GET /ping                      │
           │────────────────────────────────────────>│
           │                                         │
           │  9. Response: "PONG"                    │
           │<────────────────────────────────────────│
           │                                         │
           │  ✅ Connection Established              │
           │                                         │
           │  10. HTTP GET /capture                  │
           │────────────────────────────────────────>│
           │                                         │
           │  11. Response: JPEG bytes               │
           │<────────────────────────────────────────│
           │                                         │
```

### Android API Mapping

| ESP32 Endpoint | Android Method | File |
|----------------|----------------|------|
| BLE TX Notify | `bleManager.observeIpNotifications()` | `BLEManager.kt` |
| `GET /capture` | `Esp32ApiService.captureImage()` | `Esp32ApiService.kt` |
| `GET /control` | `Esp32ApiService.controlDevice(var, val)` | `Esp32ApiService.kt` |
| `GET /ping` | `Esp32ApiService.ping()` | `Esp32ApiService.kt` |

---

## Troubleshooting

### Common Issues

#### 1. Camera Initialization Fails

**Symptoms**: Serial output shows `Camera init failed: 0x105`

**Causes**:
- Incorrect pin configuration
- Insufficient power supply (needs 5V 2A)
- Brownout detector triggered

**Solutions**:
```cpp
// Disable brownout detector
WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

// Verify pin definitions match your hardware
#define PWDN_GPIO_NUM 32  // Check schematic
```

#### 2. Dark/Black Images

**Symptoms**: First capture is always dark

**Cause**: Camera sensor not warmed up

**Solution**: Already implemented (Lines 136-146)
```cpp
// Warm-up: Discard first 3 frames
for(int i = 0; i < 3; i++) {
    camera_fb_t * fb = esp_camera_fb_get();
    if(fb) esp_camera_fb_return(fb);
    delay(100);
}
```

#### 3. WiFi Connection Fails

**Symptoms**: Serial shows "WiFi Failed! Wrong Password?"

**Causes**:
- Incorrect credentials from Android
- Hotspot not started
- Signal too weak

**Debug**:
```cpp
Serial.print("Connecting to: ");
Serial.println(ssid_buf);
Serial.print("Password: ");
Serial.println(pass_buf);  // Verify credentials
```

**Auto-Recovery**: ESP32 restarts after failed connection (Line 454)

#### 4. BLE Not Visible

**Symptoms**: Android can't find "SmartGlasses"

**Causes**:
- Bluetooth not enabled in menuconfig
- BLE already shut down (after WiFi connection)
- Android Bluetooth permissions denied

**Check**:
```cpp
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled!
#endif
```

**Solution**: In Arduino IDE:
1. Tools → Partition Scheme → "Huge APP (3MB No OTA)"
2. Ensure Bluetooth is enabled in SDK config

#### 5. Memory Allocation Failed

**Symptoms**: Crash during camera init or BLE operation

**Cause**: Insufficient heap memory

**Solution**: Sequential resource usage (already implemented)
```cpp
BLEDevice::deinit(true);  // Free BLE before camera
delay(500);
setupCamera();
```

#### 6. HTTP Requests Timeout

**Symptoms**: Android gets `SocketTimeoutException`

**Causes**:
- WiFi sleep mode enabled
- Camera capture taking too long
- Network congestion

**Solutions**:
```cpp
// Disable WiFi sleep during capture
WiFi.setSleep(false);

// Reduce JPEG quality for faster encoding
config.jpeg_quality = 20;  // Higher number = lower quality = faster
```

---

## Performance Metrics

### Timing Benchmarks

| Operation | Duration | Notes |
|-----------|----------|-------|
| **Boot to BLE Ready** | ~2 seconds | Serial init + BLE advertising |
| **BLE Handshake** | 2-3 seconds | Scan + connect + credentials |
| **WiFi Connection** | 2-4 seconds | Depends on signal strength |
| **Camera Warm-Up** | 300 ms | 3 frames × 100ms |
| **Image Capture** | 200-500 ms | SVGA JPEG encoding |
| **HTTP Transfer** | 500-1000 ms | 40-60 KB over WiFi |
| **Total First Capture** | 8-12 seconds | From boot to first image |
| **Subsequent Captures** | <1 second | Camera already warmed up |

### Power Consumption

| State | Current Draw | Notes |
|-------|--------------|-------|
| **BLE Advertising** | ~80 mA | Active scanning |
| **WiFi Connected (Idle)** | ~100 mA | Listening for HTTP |
| **Camera Capture** | ~300 mA | Peak during JPEG encoding |
| **Flash LED ON** | +150 mA | Additional current |
| **Deep Sleep** | ~10 µA | Not implemented (always-on server) |

**Power Supply**: Requires stable 5V 2A source (USB or battery)

---

## Configuration Options

### Adjustable Parameters

#### Image Quality
```cpp
config.frame_size = FRAMESIZE_SVGA;  // Options:
// FRAMESIZE_QVGA (320x240)
// FRAMESIZE_VGA (640x480)
// FRAMESIZE_SVGA (800x600)  ← Current
// FRAMESIZE_XGA (1024x768)
// FRAMESIZE_UXGA (1600x1200)

config.jpeg_quality = 15;  // 0-63 (lower = better)
// 10 = Excellent (~80 KB)
// 15 = Good (~50 KB)       ← Current
// 20 = Acceptable (~30 KB)
```

#### WiFi Settings
```cpp
WiFi.setTxPower(WIFI_POWER_19_5dBm);  // Max power
// Options: WIFI_POWER_19_5dBm (max) to WIFI_POWER_2dBm (min)

WiFi.setSleep(false);  // Disable power saving
// true = Power saving (slower response)
// false = Always on (faster, more power)
```

#### Watchdog Timeout
```cpp
#define WIFI_TIMEOUT_MS 6000  // 6 seconds
// Increase for unstable networks
// Decrease for faster recovery
```

---

## Security Considerations

### Current Implementation

1. **BLE**: Short-range only (~10 meters)
2. **WiFi Credentials**: Sent in plain text over BLE
3. **HTTP**: No authentication, no HTTPS
4. **Hotspot**: Randomized credentials per session

### Vulnerabilities

| Attack Vector | Risk | Mitigation |
|---------------|------|------------|
| **BLE Sniffing** | Low | Short range, temporary credentials |
| **WiFi Eavesdropping** | Medium | Local network only, no sensitive data |
| **HTTP Injection** | Low | No user input processing |
| **DoS (Spam Requests)** | Medium | No rate limiting |

### Recommended Improvements

1. **HTTPS**: Use mbedTLS for encrypted HTTP (requires more RAM)
2. **API Token**: Add authentication header to HTTP requests
3. **BLE Pairing**: Require PIN for BLE connection
4. **Rate Limiting**: Limit `/capture` requests to 1 per second

---

## Firmware Updates

### OTA (Over-The-Air) Updates

**Current**: Disabled (Partition: "Huge APP 3MB No OTA")

**Why Disabled?**  
OTA requires 2 app partitions (1.5 MB each), leaving insufficient space for camera firmware.

**Alternative**: USB Serial Update
1. Connect ESP32-CAM via USB-to-Serial adapter
2. Arduino IDE → Upload
3. Firmware flashed via bootloader

---

## Debugging

### Serial Monitor Output

**Example Boot Sequence**:
```
=== SmartGlasses v4.1 ===
Bluetooth Serial: SmartGlasses-Debug
BLE UART: SmartGlasses
IMPORTANT: Set Partition Scheme to 'Huge APP (3MB No OTA)'
Flash LED initialized
BLE Ready - Device: SmartGlasses
Format: SSID,PASSWORD
Ready for WiFi credentials!

Credentials received via BLE
Connecting to: DIRECT-xy-AndroidAP
Starting WiFi...
..........
=== WiFi Connected Successfully ===
IP Address: 192.168.49.78
SSID: DIRECT-xy-AndroidAP
====================================

Sending IP via BLE: IP:192.168.49.78
BLE notification sent!
Shutting down BLE to free memory...
Initializing camera...
Warming up camera sensor...
Warm-up frame discarded
Warm-up frame discarded
Warm-up frame discarded
Camera warm-up complete
Camera initialized successfully!

=== Web Server Started ===
Access camera at: http://192.168.49.78
Endpoints:
  - /
  - /capture
  - /status
  - /control?var=flash&val=1
==========================
```

### Logging Functions

```cpp
void log(const char* message);       // Print without newline
void logln(const char* message);     // Print with newline
void logStr(const String& message);  // Print String without newline
void loglnStr(const String& message);// Print String with newline
```

---

## Code Statistics

| Metric | Value |
|--------|-------|
| **Total Lines** | 466 |
| **Functions** | 12 |
| **HTTP Endpoints** | 5 |
| **BLE Characteristics** | 2 |
| **Global Variables** | 15 |
| **Memory-Safe Buffers** | 2 (ssid_buf, pass_buf) |

---

## Version History

### v4.1 (Current)
- WiFi watchdog with auto-restart
- Camera warm-up sequence (3 frames)
- BLE shutdown after handshake
- `/ping` endpoint for health checks
- Improved error logging

### v4.0
- Nordic UART Service BLE implementation
- LocalOnlyHotspot support
- Flash LED control via `/control`

### v3.x
- Basic HTTP server
- Manual WiFi configuration

---

## References

### Libraries Used
- **esp_camera.h**: ESP32 camera driver
- **WiFi.h**: ESP32 WiFi stack
- **WebServer.h**: HTTP server
- **BLEDevice.h**: Bluetooth LE stack
- **BLE2902.h**: GATT descriptor for notifications

### External Documentation
- [ESP32-CAM Datasheet](https://github.com/raphaelbs/esp32-cam-ai-thinker)
- [OV2640 Camera Module](https://www.uctronics.com/download/cam_module/OV2640DS.pdf)
- [Nordic UART Service Spec](https://developer.nordicsemi.com/nRF_Connect_SDK/doc/latest/nrf/libraries/bluetooth_services/services/nus.html)

---

**Firmware Version**: v4.1  
**Last Updated**: January 9, 2026  
**Maintainer**: Kerem Göbekcioğlu  
**Hardware**: ESP32-CAM (AI-Thinker)

**Changelog v1.1**:
- Updated BLE handshake timing (2-3 seconds actual performance)
- Corrected total connection time estimates
