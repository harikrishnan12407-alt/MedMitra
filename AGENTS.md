# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. Kotlin and Jetpack Compose UI live in `app/src/main/java/com/medmitra/app/`; the manifest is in `app/src/main/AndroidManifest.xml`. Root Gradle configuration and the Gradle wrapper live at the repository root, while app-specific dependencies and SDK settings are in `app/build.gradle.kts`.

## Build, Test, and Development Commands

Use JDK 17 or newer, Android SDK Platform 35, and the checked-in Gradle wrapper. Build a debug APK with `./gradlew :app:assembleDebug`; install it on a connected device with `./gradlew :app:installDebug`. Run JVM unit tests with `./gradlew :app:testDebugUnitTest`. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` if the SDK is not in its standard location. The wrapper may need network access on its first run to download Gradle.

## Coding Style & Naming Conventions

Follow the conventions of the chosen language and framework, and use the repository's formatter and linter when configured. Prefer consistent indentation within each file, descriptive names, and small modules with a clear responsibility. Use names that reflect domain concepts; reserve abbreviations for widely understood terms. Add or update formatter and lint configuration as part of establishing the stack.

## Testing Guidelines

Compose screens are currently sample-driven and keep navigation and interaction state in the UI. Add JVM tests under `app/src/test/` and device/UI tests under `app/src/androidTest/` when behavior warrants them. Run unit tests with `./gradlew :app:testDebugUnitTest`; no coverage threshold is currently set.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects (for example, `Add medication search`) and keep each commit focused. Pull requests should explain the change and its motivation, list validation performed, link related issues when available, and include screenshots for user-facing changes.

## Security & Configuration

The app intentionally requests no phone, SMS, location, microphone, or background sensor permissions. Emergency, voice, location, and fall-detection screens are informational UI/sample interactions only. Keep real credentials and environment-specific secrets out of version control; request only permissions needed by a future implemented feature.
