# 🧠 MEMEX: Your Private AI Second Brain

[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](https://opensource.org/licenses/MIT)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-00D4AA.svg)](https://developer.android.com)
[![AI: Offline](https://img.shields.io/badge/AI-100%25%20Offline-7B5CF0.svg)](#features)

> **"Your memories, truly yours. Forever secure, forever offline, zero cloud costs."**

MEMEX is a revolutionary privacy-first "second brain" companion for Android. It captures your life—documents, voice notes, thoughts—and uses on-device Small Language Models (SLMs) to summarize, tag, and answer questions about your history without ever sending a single byte to the cloud.

---

## ✨ Key Features

### 🔐 100% Privacy & Security
*   **Zero Cloud Inference:** All AI processing (Vision, Speech, LLM) happens locally on your Snapdragon/MediaTek/Tensor chip.
*   **Cryptographic Proof:** Every memory is hashed (SHA-256) at the moment of capture, providing a tamper-proof audit trail of your life.
*   **Biometric Vault:** Secure your memories behind fingerprint/face unlock.
*   **AES-256 Encryption:** Your database is fully encrypted at rest.

### 🎭 Context Resurrection
*   Pick any collection of past memories and let MEMEX weave them into a coherent narrative.
*   Perfect for preparing for meetings, recalling trip details, or reliving old ideas.

### 🎙️ Instant Voice Query
*   Ask complex questions about your past: *"What was that project idea I mentioned during the Bangalore trip?"*
*   Multilingual support (English & Hindi) with low-latency offline Speech-to-Text.

### 💎 Premium Experience
*   **Deep Dark UI:** A stunning, premium aesthetic designed for focus.
*   **Haptic Interface:** Tactile feedback for every major interaction.
*   **₹0 Cost:** No API subscriptions. The "Inference Cost Counter" shows you exactly how much you're saving compared to GPT-4.

---

## 🛠️ Technology Stack

*   **Language:** 100% Kotlin
*   **Framework:** Jetpack Compose (Modern Declarative UI)
*   **AI Engine:** [RunAnywhere AI SDK](https://runanywhere.ai) (VLM, LLM, STT, TTS, VAD)
*   **Storage:** Room Database + AES Encryption
*   **Navigation:** Compose Navigation with strict Type Safety
*   **Dependency Injection:** Hilt
*   **Concurrency:** Kotlin Coroutines & Flow

---

## 🚀 Getting Started

### Prerequisites
*   Android Studio Ladybug or later.
*   Android device with API 24+ (Recommended: 8GB+ RAM for LLM performance).
*   **RunAnywhere API Key:** Required for SDK initialization.

### Setup
1.  **Clone the repo:**
    ```bash
    git clone https://github.com/shivam-singh-tech/memex-app.git
    ```
2.  **Configure API Key:**
    Add your key to `local.properties`:
    ```properties
    RUNANYWHERE_API_KEY=your_key_here
    ```
3.  **Sync & Run:** Open in Android Studio, sync Gradle, and run on your physical device.

---

## 📂 Project Structure
```text
com.memex.app
├── ai             # RunAnywhere SDK wrappers (Vision, STT, LLM)
├── data           # Room DB, MemoryRepository, Crypto utilities
├── domain         # Domain models and entities
├── navigation     # NavGraph and route definitions
├── ui
│   ├── components # Reusable premium UI components
│   ├── screens    # Capture, Home, Voice Query, Resurrection
│   └── theme      # The "Memex" Design System (Colors, Type)
└── util           # General helpers (Haptics, Permissions)
```

---

## ⚖️ License
Distributed under the MIT License. See `LICENSE` for more information.

---

## 🙌 Credits
Built with ❤️ for the Microsoft Hackathon by **Shivam Singh** & **Ankur Verma**. Powered by **RunAnywhere AI**.
