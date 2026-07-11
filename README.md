<div align="center">

# 🔵 Circle VPN

### The Official Android App for the Circle VPN Provider

Fast. Secure. Simple.

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](#-license)
[![Built with AI Studio](https://img.shields.io/badge/Built%20with-Google%20AI%20Studio-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.studio)

<br/>

[Features](#-features) • [Getting Started](#-getting-started) • [Tech Stack](#-tech-stack) • [Configuration](#-configuration) • [Contributing](#-contributing)

</div>

---

## 📖 About

**Circle VPN** is the official native Android client for the Circle VPN provider, built entirely in **Kotlin**. It's designed to give users a clean, fast, and reliable way to connect to the VPN network with minimal friction — no clutter, no confusion, just a single tap to get protected.

This project was bootstrapped from the [Google AI Studio](https://ai.studio) repository template and is actively evolving.

> 🔗 **Live in AI Studio:** [View this app](https://ai.studio/apps/1821eee6-d175-4667-9570-68d5be9b9b13)

---

## ✨ Features

- ⚡ **One-Tap Connect** — Get connected to the Circle VPN network instantly
- 🔒 **Secure by Design** — Built with modern Android security best practices
- 🎨 **Clean, Modern UI** — A minimal interface that stays out of your way
- 📱 **Native Android Experience** — 100% Kotlin, no cross-platform overhead
- 🤖 **AI-Powered Foundation** — Developed using Google AI Studio's Gemini-powered tooling

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Build System | Gradle (Kotlin DSL) |
| Platform | Android |
| Tooling | Google AI Studio / Gemini API |

---

## 🚀 Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable version recommended)
- A [Gemini API key](https://ai.google.dev/) if you plan to use AI-related features

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/ItsOsitaro/Circle-VPN.git
   cd Circle-VPN
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select **Open** and choose the cloned project directory
   - Let Android Studio sync and resolve any dependencies

3. **Configure your environment**
   - Create a `.env` file in the project root
   - Add your Gemini API key (see [`.env.example`](./.env.example) for the expected format):
     ```env
     GEMINI_API_KEY=your_api_key_here
     ```

4. **Adjust the signing config**
   - Remove the following line from `app/build.gradle.kts`:
     ```kotlin
     signingConfig = signingConfigs.getByName("debugConfig")
     ```

5. **Run the app**
   - Select an emulator or connect a physical device
   - Click **Run ▶️** in Android Studio

---

## ⚙️ Configuration

| Variable | Description | Required |
|---|---|---|
| `GEMINI_API_KEY` | Your Google Gemini API key | ✅ |

> ⚠️ Never commit your `.env` file. It's already excluded via `.gitignore`.

---

## 📂 Project Structure

```
Circle-VPN/
├── app/                  # Main Android application module
├── assets/.aistudio/     # AI Studio project metadata
├── gradle/               # Gradle wrapper files
├── .env.example          # Example environment configuration
├── build.gradle.kts      # Project-level build configuration
├── settings.gradle.kts   # Gradle project settings
└── metadata.json         # App metadata
```

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the project
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](./LICENSE) file for details.

---

<div align="center">

Made with ❤️ by [ItsOsitaro](https://github.com/ItsOsitaro)

⭐ If you like this project, don't forget to give it a star!

</div>
