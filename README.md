# Taina 🌿

Taina is an Android app for field biodiversity observation, built for NGO rangers and naturalists who work in areas with limited or no internet connectivity. An on-device AI assistant (Google Gemma) guides users through recording species sightings via natural conversation, stores them in the [Darwin Core](https://dwc.tdwg.org/) standard, and syncs to GBIF when WiFi is available.

---

## Features

- **AI-guided observation recording** — chat with Taina to log species name, count, habitat, locality, and notes
- **Photo attachment** — attach a photo from your gallery or take one with the camera
- **Automatic GPS** — resolved in order: EXIF from photo → stated locality (geocoded) → device satellite GPS
- **Fully offline capable** — AI runs on-device, GPS uses satellite only, the map needs no internet
- **Records gallery** — browse all past observations with photos and Darwin Core fields
- **Stats screen** — timeline chart, top species, and an offline canvas map with observation pins
- **GBIF sync** — records upload automatically over WiFi when available

---

## How it works (quick overview)

1. You open the app and either take a photo or start typing
2. Taina (the AI) asks you a series of short questions: species name, count, habitat, location, notes
3. When you confirm, it saves a Darwin Core record to the local database with GPS coordinates
4. When you connect to WiFi, the record syncs to GBIF

---

## Two ways to run the AI

Taina supports two AI backends, controlled by a single flag in `GemmaInferenceModel.kt`:

```kotlin
private val useOllama = true   // true = Ollama on your computer | false = on-device LiteRT
```

| Mode | Best for | Requirement |
|---|---|---|
| **Ollama** (`useOllama = true`) | Development, emulator, demo | Ollama running on your computer |
| **LiteRT on-device** (`useOllama = false`) | Real device, offline fieldwork | Model file on the device |

---

## Setup — step by step

### 1. Install Android Studio

Download and install [Android Studio](https://developer.android.com/studio) (Hedgehog 2023.1 or later).

During installation, make sure the following components are checked:
- Android SDK
- Android SDK Platform (API 35)
- Android Virtual Device

---

### 2. Clone the project

Open a terminal and run:

```bash
git clone https://github.com/Elodidie/Taina_gemma4good.git
```

Then open Android Studio, choose **File → Open**, and select the `Taina_gemma4good` folder.

Wait for Gradle to sync (bottom status bar). This downloads all dependencies automatically.

---

### 3. Create an emulator (Android Virtual Device)

> **Apple Silicon Mac (M1/M2/M3):** you **must** use an arm64-v8a system image. x86_64 images will not run on ARM hosts.

1. In Android Studio, open **Device Manager** (right sidebar phone icon, or **Tools → Device Manager**)
2. Click **+** → **Create Virtual Device**
3. Choose a hardware profile — **Pixel 8** is a good default
4. Click **Next** → on the System Image screen, click the **Other Images** tab
5. Find an image with ABI = **arm64-v8a**, API 34 or 35 → click **Download** next to it → then **Next**
6. Keep default settings → **Finish**
7. Press the **Play ▶** button next to your new device to start it

---

### 4. Run with Ollama (recommended for development)

Ollama lets you run Gemma on your computer and have the emulator talk to it over the local network. This is faster to set up than copying model files to a device.

#### 4a. Install Ollama

Go to [https://ollama.com](https://ollama.com) and download the installer for your OS (Mac, Windows, or Linux). Install and run it — Ollama runs as a background service on port `11434`.

#### 4b. Pull the Gemma model

Open a terminal and run:

```bash
ollama pull gemma4:2b
```

This downloads the ~3 GB Gemma 3 4B model. It only needs to be done once.

To verify it works:

```bash
ollama run gemma4:2b "Hello, describe a rainforest in one sentence."
```

#### 4c. Check the app is pointing to Ollama

Open `app/src/main/java/com/example/gemma/GemmaInferenceModel.kt` and confirm:

```kotlin
private val useOllama   = true
private val ollamaModel = "gemma4:2b"
```

The emulator reaches your computer at `10.0.2.2` (Android's alias for the host machine's `localhost`), which is already configured:

```kotlin
private val ollamaBaseUrl = "http://10.0.2.2:11434"
```

No other changes are needed.

#### 4d. Run the app

In Android Studio, make sure your emulator is selected in the device dropdown (top toolbar), then click the green **Run ▶** button. The app will install and launch on the emulator.

When you send a message in Taina, the emulator calls Ollama on your computer and streams the response back. You should see activity in the Ollama terminal window.

---

### 5. Run with on-device LiteRT (for real device / offline use)

This mode runs Gemma entirely on the Android device — no computer or internet needed.

#### 5a. Download the model file

Download the LiteRT model file from Google's model repository. The app expects:

```
/data/local/tmp/llm/gemma-4-E2B-it.litertlm
```

#### 5b. Push the model to your device

Connect your Android device via USB (enable **USB Debugging** in Developer Options), then run:

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/
```

#### 5c. Switch the flag

In `GemmaInferenceModel.kt`, set:

```kotlin
private val useOllama = false
```

#### 5d. Run the app

Click **Run ▶** in Android Studio with your physical device selected.

---

## Grant location permission

When the app first launches it will ask for location access. Tap **Allow while using the app** so GPS coordinates are captured with each observation.

---

## Project structure

```
app/src/main/java/com/example/gemma/
├── MainActivity.kt          # Navigation and ChatScreen UI
├── ChatViewModel.kt         # Observation flow, GPS resolution, Gemma integration
├── GemmaInferenceModel.kt   # Ollama and LiteRT backends (switch with useOllama flag)
├── LocationHelper.kt        # Background satellite GPS fix
├── DarwinRecord.kt          # Room entity — Darwin Core fields
├── DarwinDatabase.kt        # Room database and DAO
├── RecordsScreen.kt         # Observation gallery
├── RecordsViewModel.kt      # Records list state
├── StatsScreen.kt           # Timeline chart, species chart, offline map
├── StatsViewModel.kt        # Aggregated stats queries
├── SyncWorker.kt            # WorkManager GBIF upload (WiFi-only)
└── TainaTheme.kt            # NGO brand color scheme
```

---

## GPS behavior

| Situation | GPS source |
|---|---|
| Photo with EXIF location | Coordinates embedded in the photo at capture time |
| No EXIF + locality named in chat | Android Geocoder (may need network on some devices) |
| No EXIF + no geocodable locality | Fresh satellite fix from device GPS (5s timeout) |

---

## Color palette

| Role | Hex |
|---|---|
| Primary green | `#30CF89` |
| Dark background | `#282828` |
| Beige / light background | `#F2EEE4` |

---

## GBIF sync

The sync worker is stubbed in `SyncWorker.kt`. To enable real uploads, uncomment the HTTP POST block and replace the URL with your GBIF-compatible endpoint:

```kotlin
// val url = URL("https://your-api.example.com/occurrences")
```

The worker runs automatically on WiFi and retries failed uploads.

---

## Built with

- [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI
- [Google Gemma via LiteRT](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference) — on-device AI
- [Ollama](https://ollama.com) — local AI server for development
- [Room](https://developer.android.com/training/data-storage/room) — local database
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) — background sync
- [Coil](https://coil-kt.github.io/coil/) — image loading

---

## License

Developed for the Google Gemma for Good program.
