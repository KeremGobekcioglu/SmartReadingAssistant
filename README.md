# 🕶️ Smart Reading Assistant for Visually Impaired Users

A wearable AI-powered reading assistant that helps visually impaired users understand printed content — menus, documents, labels — through natural voice interaction.

**Demo Video:** https://www.youtube.com/watch?v=DPq8-S-o64U.
English subtitles are a bit problematic but i ll update. 

---

## Overview

Traditional OCR apps require the user to manually aim a phone at text — awkward, physically demanding, and socially conspicuous. This project puts an **ESP32-CAM on a glasses frame**, aligning the camera with the user's natural line of sight, and pairs it with an Android app powered by **Gemini 2.5 Flash**.

The user presses a physical button or holds the in-app PTT (Push-to-Talk) button to capture an image and ask a voice question. The AI responds in real-time via streaming audio. Follow-up questions reuse the same image context without re-uploading.

---

## System Architecture

```
[ESP32-CAM on Glasses]
        |
   1. BLE Handshake (WiFi credential transfer)
   2. HTTP over local hotspot
        |
[Android App]
        |
   SSE Streaming
        |
[Gemini 2.5 Flash API]
```

Three layers:
- **Wearable Module** — ESP32-CAM: image capture, HTTP server, BLE credential receiver
- **Mobile Processing Hub** — Android: connection management, voice I/O, AI orchestration
- **Cloud Intelligence** — Gemini 2.5 Flash: multimodal reasoning, contextual Q&A

---

## Key Technical Highlights

### ESP32 Firmware (C++ / ESP-IDF)
- **Sequential Resource Allocation**: BLE (~100KB) and camera DMA (~200KB) can't coexist in 520KB SRAM. BLE stack is fully deinitialized after WiFi handshake before camera is initialized.
- **Zero-Config BLE Handshake**: Android creates a hotspot, sends credentials over Nordic UART Service (custom 128-bit UUID). ESP32 responds with its assigned IP via BLE notify.
- **Camera Warm-up**: First 3 frames are discarded to stabilize OV2640 AEC/AWB. Locked at SVGA (800×600) for the best latency/quality balance.
- **WiFi Watchdog**: Monitors connection every 10ms. Triggers `ESP.restart()` after 6 seconds of disconnection — no manual intervention needed.

### Android App (Kotlin / Jetpack Compose)
- **Clean Architecture + MVVM**: Presentation → Domain → Data. Domain layer has zero Android dependencies.
- **SSE Streaming**: `LLMRepository` holds an infinite-timeout HTTP connection to Gemini. Text chunks are parsed and emitted to UI immediately as they arrive.
- **Word-by-Word TTS Buffer**: `TextToSpeechManager` flushes the buffer on punctuation/newline, creating natural-sounding speech that starts within ~0.2–0.5s of the first token.
- **Multimodal Image Caching**: First upload returns a Gemini `fileUri`. Subsequent follow-up questions reuse this URI — no redundant uploads.
- **PTT State Machine**: `Idle → Listening → Processing → Streaming`. TTS is silenced on PTT press to prevent audio feedback loops.
- **DI**: Dagger Hilt, singleton-scoped managers, ViewModel-scoped per navigation entry.

---

## Performance

| Metric | Result |
|---|---|
| Image capture + HTTP transfer | ~1s |
| First Gemini upload | 1.5–2.5s |
| First token latency | 0.5–1.5s |
| **End-to-end (first word spoken)** | **~3–4s** |
| Follow-up questions (cached image) | ~2–3s |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Firmware | C++, ESP-IDF, Arduino framework |
| Hardware | ESP32-CAM, OV2640, BLE (Nordic UART) |
| Android | Kotlin, Jetpack Compose, MVVM, Hilt, Room, Retrofit |
| AI | Gemini 2.5 Flash (multimodal, SSE streaming) |
| Protocols | BLE → WiFi handshake, HTTP REST, Server-Sent Events |

---

## Functional Verification

| Feature | Result |
|---|---|
| BLE auto-connect (no manual credential entry) | ✅ Pass |
| Connection recovery after ESP32 restart | ✅ Pass |
| Multi-turn context (cached image URI) | ✅ Pass |
| TTS interrupt on PTT press | ✅ Pass |
| Voice command vs. AI query routing | ✅ Pass |
| OCR accuracy ≥ 80% in moderate lighting | ✅ Pass |
| System response time ≤ 5s | ✅ Pass |

---

## Accessibility Design

- High-contrast color scheme (WCAG AA compliant)
- Large PTT button (~60% screen width)
- TTS announcements for all state transitions ("Photo captured. What would you like to know?")
- Haptic feedback on button press/state change
- Voice commands: "take a photo", "flash on", "clear conversation"

---

## Limitations

- Requires internet for Gemini API (no offline fallback)
- Best results at 10–20cm from target, perpendicular angle, moderate lighting
- Single image context per session (no multi-image comparison)
- Prototype powered externally; battery integration not yet implemented
- No autofocus on OV2640

---

## Project Context

Graduation project at **Gebze Technical University**, Department of Computer Engineering.  
Supervisor: Prof. Dr. Erkan Zergeroğlu  
Presented: January 15, 2026 — Faculty of Engineering Graduation Projects Exhibition

---

## Author

**Kerem Göbekcioğlu**  
[LinkedIn](https://linkedin.com/in/keremgobekcioglu) · [GitHub](https://github.com/KeremGobekcioglu)
