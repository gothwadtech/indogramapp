# GrixChat 🚀

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**A modern, offline-first Android messaging client built with Jetpack Compose, Material 3, Room Database, and Firebase Cloud Messaging.**

[Features](#-key-features) • [Tech Stack](#-tech-stack) • [Getting Started](#-getting-started) • [CI/CD & Releases](#-cicd--signing-secrets) • [Architecture](#-architecture)

</div>

---

## 📱 Overview

**GrixChat** is an open-source Android messaging and communication app designed for fluid performance, offline reliability, and clean aesthetics. It integrates native Jetpack Compose interfaces with advanced WebView caching mechanisms, local Room database persistence, and Firebase Push Notifications.

---

## ✨ Key Features

- 🎨 **Material 3 & Edge-to-Edge**: Modern UI design following the latest Material Design 3 guidelines, dynamic theming with dark mode support, and seamless edge-to-edge drawing.
- ⚡ **Offline-First Reliability**: Integrated Room Database along with WebView ServiceWorker caching ensuring fast load times and uninterrupted offline experience.
- 🔔 **Push Notifications**: Full Firebase Cloud Messaging (FCM) integration with custom Android notification channels for background and heads-up alerts.
- 🔄 **Modern State Management**: MVVM architecture utilizing Kotlin Coroutines, `StateFlow`, and `collectAsStateWithLifecycle`.
- 🛡️ **Automated CI/CD Workflows**: Fully automated GitHub Actions for building signed Release APKs, Play Store AAB bundles, and multi-platform distribution packages with strict secret validation.

---

## 🛠 Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose (BOM), Material 3, Accompanist |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Local Storage** | Room Database + SQLite, Android Keystore |
| **Networking & API**| Retrofit, OkHttp 4, Moshi (Kotlin codegen) |
| **Push Notifications** | Firebase Cloud Messaging (FCM) |
| **Build System** | Gradle 9.3.1 (Kotlin DSL), Android Gradle Plugin (AGP) |
| **Testing** | Robolectric, Roborazzi, JUnit 4, AndroidX Test |

---

## 📂 Project Structure

```text
GrixChat/
├── .github/
│   └── workflows/
│       ├── build.yml          # Build & Sign APK / AAB on push to main
│       └── release.yml        # Build & Publish to GitHub Releases on tag (v*)
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/        # App assets & graphics
│   │   │   ├── java/com/gothwad/grixchat/
│   │   │   │   ├── data/      # Room Database, DAO, Repository
│   │   │   │   ├── ui/        # Compose Screens, ViewModels, Theme
│   │   │   │   └── utils/     # FCM Service, Notification Helpers
│   │   │   ├── res/           # Layouts, mipmaps, drawables, strings
│   │   │   └── AndroidManifest.xml
│   │   └── test/              # Local JVM and Robolectric unit tests
│   ├── build.gradle.kts       # App module configuration & dependencies
│   └── proguard-rules.pro     # ProGuard / R8 rules
├── gradle/
│   ├── libs.versions.toml     # Version catalog
│   └── wrapper/               # Gradle wrapper executable & properties
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Project settings & plugin resolution
└── README.md                  # Documentation
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Ladybug (2024.2.1+) or newer recommended.
- **JDK**: Java 17 or Java 21 (Temurin / Eclipse Adoptium recommended).
- **Android SDK**: API Level 35 (compileSdk & targetSdk), Minimum API Level 23.

### Local Installation & Build

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/GrixChat.git
   cd GrixChat
   ```

2. **Setup environment variables:**
   ```bash
   cp .env.example .env
   ```

3. **Build the Debug APK:**
   ```bash
   chmod +x ./gradlew
   ./gradlew assembleDebug
   ```
   The debug APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🔐 CI/CD & Signing Secrets

The repository includes pre-configured GitHub Actions workflows for continuous integration and automated release deployments.

### Required GitHub Secrets

To build and sign Release APKs & Play Store AAB bundles automatically, add the following secrets to your GitHub repository under **Settings > Secrets and variables > Actions**:

| Secret Name | Description | Required |
| :--- | :--- | :---: |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release `.jks` or `.keystore` file | **Yes** |
| `KEYSTORE_PASSWORD` | Password for your release keystore | **Yes** |
| `KEY_ALIAS` | Key alias name inside the keystore | Optional |
| `KEY_PASSWORD` | Password for the key alias | Optional |

> **Note**: For security, if `RELEASE_KEYSTORE_BASE64` or `KEYSTORE_PASSWORD` is not configured, the release build step will automatically abort to prevent deploying unverified or improperly signed builds.

### Generating `RELEASE_KEYSTORE_BASE64`

You can convert your local `.jks` or `.keystore` file into Base64 using:

**Linux / macOS:**
```bash
base64 -i my-release-key.jks | tr -d '\n' > keystore_base64.txt
```

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-release-key.jks")) | Set-Content keystore_base64.txt
```
Copy the contents of `keystore_base64.txt` and paste it into GitHub Secrets as `RELEASE_KEYSTORE_BASE64`.

---

## 🏷️ Triggering a Release

To trigger an official GitHub Release:

1. Create a version tag locally:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. The `Release to GitHub Releases` workflow will automatically:
   - Validate signing secrets.
   - Self-heal Gradle wrapper if needed.
   - Build signed Release APK, Debug APK, and Play Store AAB.
   - Publish a new GitHub Release with generated release notes and downloadable assets.

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the Apache License 2.0. See `LICENSE` for more information.
