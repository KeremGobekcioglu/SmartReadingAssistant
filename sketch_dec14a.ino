#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include <string.h>

// Check if Bluetooth is available
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to enable it
#endif


// ======================= PIN DEFINITIONS =======================
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22
#define FLASH_LED_PIN      4
// ======================= BLE SETTINGS (Nordic UART Service) =======================
#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" 
#define RX_CHARACTERISTIC   "6E400002-B5A3-F393-E0A9-E50E24DCCA9E" // App writes here
#define TX_CHARACTERISTIC   "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // ESP32 notifies here

BLEServer* pServer = NULL;
BLECharacteristic* pTxCharacteristic = NULL;
BLECharacteristic* pRxCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// ======================= MEMORY SAFE VARIABLES =======================
char ssid_buf[33] = {0};
char pass_buf[65] = {0};
volatile bool credentialsReady = false;
bool wifiConnected = false;
bool cameraInitialized = false;

WebServer server(80);

// ======================= LOGGING HELPERS =======================
void log(const char* message) {
  Serial.print(message);
}

void logln(const char* message) {
  Serial.println(message);
}

void logStr(const String& message) {
  Serial.print(message);
}

void loglnStr(const String& message) {
  Serial.println(message);
}
void setupFlash() {
  pinMode(FLASH_LED_PIN, OUTPUT);
  digitalWrite(FLASH_LED_PIN, LOW);
  logln("Flash LED initialized");
}

void setFlash(bool state) {
  digitalWrite(FLASH_LED_PIN, state ? HIGH : LOW);
  Serial.print("Flash: ");
  Serial.println(state ? "ON" : "OFF");
}
// ======================= CAMERA SETUP =======================
void setupCamera() {
  logln("Initializing camera...");
  
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  
  // Memory optimized settings
config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  
  // ==========================================================
  //                *** MAX QUALITY SETTINGS ***
  // ==========================================================
  if(psramFound()){
    // Use the highest standard resolution
    config.frame_size = FRAMESIZE_UXGA;    // 1600x1200 pixels
    
    // JPEG Quality: 0 is highest quality (least compression), 63 is lowest.
    // We target 10 or lower for high quality.
    config.jpeg_quality = 10;
    
    // Use 2 buffers if PSRAM is plentiful and we need speed, but 1 is safest for RAM.
    config.fb_count = 1;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    // If no PSRAM, we must stick to lower resolutions.
    config.frame_size = FRAMESIZE_SVGA;    // 800x600 pixels (Max safe for no-PSRAM)
    config.jpeg_quality = 12;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    char buf[50];
    sprintf(buf, "Camera init failed: 0x%x", err);
    logln(buf);
    cameraInitialized = false;
    return;
  }
  
  cameraInitialized = true;
  logln("Camera initialized successfully!");
}

// ======================= HTTP HANDLERS =======================
void handleCapture() {
  if (!cameraInitialized) {
    server.send(503, "text/plain", "Camera not initialized");
    return;
  }
  
  WiFi.setSleep(false);
  
  camera_fb_t * fb = esp_camera_fb_get();
  if(!fb) {
    logln("Camera capture failed");
    server.send(500, "text/plain", "Camera capture failed");
    return;
  }
  
  char buf[50];
  sprintf(buf, "Captured: %d bytes", fb->len);
  logln(buf);
  
  WiFiClient client = server.client();
  client.write("HTTP/1.1 200 OK\r\n");
  client.write("Content-Type: image/jpeg\r\n");
  client.write("Content-Disposition: inline; filename=capture.jpg\r\n");
  client.write("Access-Control-Allow-Origin: *\r\n");
  client.write("Content-Length: ");
  client.print(fb->len);
  client.write("\r\n\r\n");
  client.write(fb->buf, fb->len);
  
  esp_camera_fb_return(fb);
}
void handleControl() {
  if (!server.hasArg("device") || !server.hasArg("value")) {
    server.send(400, "text/plain", "Missing device or value parameter");
    return;
  }
  
  String device = server.arg("device");
  int value = server.arg("value").toInt();
  
  Serial.print("Control request - Device: ");
  Serial.print(device);
  Serial.print(", Value: ");
  Serial.println(value);
  
  if (device == "flash") {
    setFlash(value == 1);
    server.send(200, "text/plain", "OK");
  } else {
    server.send(400, "text/plain", "Unknown device");
  }
}

void handleStatus() {
  String status = cameraInitialized ? "READY" : "CAMERA_NOT_INITIALIZED";
  server.send(200, "text/plain", status);
}

void handleRoot() {
  String html = "<!DOCTYPE html><html><body>";
  html += "<h1>SmartGlasses Camera</h1>";
  html += "<p>WiFi: Connected</p>";
  html += "<p>Camera: " + String(cameraInitialized ? "Ready" : "Not Initialized") + "</p>";
  html += "<p><a href='/capture'>Capture Image</a></p>";
  html += "<p><a href='/status'>Status</a></p>";
  html += "<p><a href='/control?device=flash&value=1'>Flash ON</a></p>";
  html += "<p><a href='/control?device=flash&value=0'>Flash OFF</a></p>";
  html += "</body></html>";
  server.send(200, "text/html", html);
}

// ======================= BLE CALLBACKS =======================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      logln("BLE device connected");
    };
    
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      logln("BLE device disconnected");
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        // Use String briefly to get value
        String tempRxValue = pCharacteristic->getValue();
        const char* rawData = tempRxValue.c_str();
        int len = tempRxValue.length();

        if (len > 0) {
            // Find comma separator
            const char* commaPtr = strchr(rawData, ',');
            
            if (commaPtr != NULL) {
                size_t ssidLen = commaPtr - rawData;
                const char* passPtr = commaPtr + 1;
                size_t passLen = len - ssidLen - 1;

                // Copy to safe buffers
                if (ssidLen < sizeof(ssid_buf) && passLen < sizeof(pass_buf)) {
                    strncpy(ssid_buf, rawData, ssidLen);
                    ssid_buf[ssidLen] = '\0';
                    
                    strncpy(pass_buf, passPtr, passLen);
                    pass_buf[passLen] = '\0';

                    credentialsReady = true;
                    logln("Credentials received via BLE");
                }
            }
        }
    }
};

void setupBLE() {
  logln("Starting BLE...");
  BLEDevice::init("SmartGlasses");
  
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // RX Characteristic (Phone writes here)
  pRxCharacteristic = pService->createCharacteristic(
                      RX_CHARACTERISTIC,
                      BLECharacteristic::PROPERTY_WRITE
                    );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  // TX Characteristic (ESP32 sends notifications here)
  pTxCharacteristic = pService->createCharacteristic(
                        TX_CHARACTERISTIC,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  
  BLEDevice::startAdvertising();
  logln("BLE Ready - Device: SmartGlasses");
  logln("Format: SSID,PASSWORD");
}

// ======================= MAIN SETUP =======================
void setup() {
  // Disable brownout detector
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  
  Serial.begin(115200);
  delay(1000);
  
  // Start Bluetooth Serial for wireless logging
  delay(500);
  
  logln("\n=== SmartGlasses v4.0 ===");
  logln("Bluetooth Serial: SmartGlasses-Debug");
  logln("BLE UART: SmartGlasses");
  logln("IMPORTANT: Set Partition Scheme to 'Huge APP (3MB No OTA)'");
  
  // Initialize camera first
  // setupCamera();
  
  // Initialize BLE
  setupBLE();
  
  logln("Ready for WiFi credentials!");
}

void loop() {
  // Handle credentials received via BLE
  if (credentialsReady) {
    credentialsReady = false;

    if (!wifiConnected) {
      Serial.print("Connecting to: ");
      Serial.println(ssid_buf);
      
      // NOTICE: We do NOT kill BLE yet. We need it to send the IP.
      
      Serial.println("Starting WiFi...");
      WiFi.mode(WIFI_STA);
      WiFi.begin(ssid_buf, pass_buf);
      
      int retries = 0;
      // Wait up to 15 seconds for WiFi
      while (WiFi.status() != WL_CONNECTED && retries < 30) {
        delay(500);
        Serial.print(".");
        retries++;
      }
      Serial.println("");

if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    String ipAddr = WiFi.localIP().toString();
      Serial.println("\n=== WiFi Connected Successfully ===");
    Serial.print("IP Address: ");
    Serial.println(ipAddr);
    Serial.print("SSID: ");
    Serial.println(ssid_buf);
    Serial.println("====================================\n");
    // 1. Send IP via BLE first
    if (deviceConnected && pTxCharacteristic != NULL) {
        String msg = "IP:" + ipAddr;
        pTxCharacteristic->setValue(msg.c_str());
        pTxCharacteristic->notify();
        delay(1000); // Give BLE time to actually push the data out
    }

    // 2. SHUT DOWN BLE COMPLETELY to free RAM
    logln("Shutting down BLE to free memory...");
    BLEDevice::deinit(true); 
    delay(500); // Let the heap settle

    // 3. NOW initialize the camera
    setupCamera(); 

    // 4. Start Web Server
    if (cameraInitialized) {
        server.on("/", handleRoot);
        server.on("/capture", handleCapture);
        server.on("/status", handleStatus);
        server.on("/control", handleControl);
        server.begin();
        logln("Web Server Started");
    }
}else {
        Serial.println("WiFi Failed! Wrong Password?");
        // Blink LED or restart advertising here if needed
      }
    }
  }

  // Handle web requests
  if (wifiConnected && WiFi.status() == WL_CONNECTED) {
    server.handleClient();
  }
  
  delay(10);
}