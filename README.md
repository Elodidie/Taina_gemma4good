# Taina 🌿

Taina is an Android app for field biodiversity observation, built for NGO rangers and naturalists who work in areas with no internet connectivity. It uses an on-device AI assistant (Google Gemma) to guide users through recording species observations, stores them in the [Darwin Core](https://dwc.tdwg.org/) standard, and syncs to GBIF when WiFi is available.

---

## Features

- **AI-guided observation recording** — chat with Taina to log species name, count, habitat, locality, and notes. The AI asks follow-up questions and produces a structured Darwin Core record automatically.
- **Photo attachment** — attach a photo from your gallery (including the Downloads folder) or take one with the camera. The photo is stored with the record.
- **GPS coordinates** — resolved in priority order:
  1. EXIF data embedded in the attached photo (most accurate — reflects where the photo was taken)
  2. Locality name stated in conversation, geocoded offline via Android Geocoder
  3. Device satellite GPS (PRIORITY_HIGH_ACCURACY, works fully offline)
- **Fully offline** — the AI model runs on-device, GPS uses satellite only, and the canvas map requires no internet. The app is designed for fieldwork in remote areas.
- **Records gallery** — browse all past observations with photos, GPS coordinates, and Darwin Core fields.
- **Stats screen** — timeline chart, top species bar chart, and an offline canvas map with pinned observation locations.
- **GBIF sync** — pending records are automatically uploaded to a GBIF-compatible endpoint over WiFi when available.

---

## Architecture

| Layer | Technology |
|---|---|
| UI | Jetpack Compose (Material 3) |
| AI | Google Gemma via LiteRT-LM (on-device inference) |
| Database | Room (Darwin Core schema) |
| GPS | FusedLocationProviderClient — PRIORITY_HIGH_ACCURACY |
| Background sync | WorkManager (WiFi-only constraint) |
| Image loading | Coil |

### Key files

```
app/src/main/java/com/example/gemma/
├── MainActivity.kt        # Navigation, ChatScreen UI
├── ChatViewModel.kt       # Observation flow, GPS resolution, Gemma integration
├── GemmaInferenceModel.kt # On-device Gemma inference wrapper
├── LocationHelper.kt      # GPS: background fix + one-shot satellite fix
├── DarwinRecord.kt        # Room entity (Darwin Core fields)
├── DarwinDatabase.kt      # Room database and DAO
├── RecordsScreen.kt       # Observation gallery UI
├── RecordsViewModel.kt    # Records list state
├── StatsScreen.kt         # Timeline, top species, offline canvas map
├── StatsViewModel.kt      # Aggregated stats queries
├── SyncWorker.kt          # WorkManager GBIF upload job
├── TainaTheme.kt          # NGO brand color scheme
└── LocationHelper.kt      # Satellite GPS helper
```

---

## Setup

### Requirements

- Android Studio Hedgehog or later
- Android device or emulator with **arm64-v8a** system image (required for on-device Gemma)
- Android API 26+

> **Apple Silicon (M1/M2/M3) users:** use an arm64-v8a emulator image — x86_64 images are not supported on ARM hosts.

### Model

Taina uses Google Gemma running via LiteRT-LM. Download the model file and place it in the app's assets or the expected path configured in `GemmaInferenceModel.kt`.

### Build

```bash
git clone https://github.com/Elodidie/Taina_gemma4good.git
cd Taina_gemma4good
./gradlew assembleDebug
```

### GBIF sync endpoint

The sync is stubbed in `SyncWorker.kt`. Uncomment and configure the HTTP POST block with your GBIF-compatible API endpoint before production use.

---

## GPS behavior

| Situation | GPS source used |
|---|---|
| Photo with EXIF location | EXIF coordinates (location where photo was taken) |
| Photo without EXIF + locality stated | Android Geocoder (offline, may need network on some devices) |
| No photo, no geocodable locality | Device satellite GPS (5 s timeout, then cached background fix) |

The background GPS fix starts at app launch so a satellite position is ready by the time the user saves a record.

---

## Color palette

| Role | Color |
|---|---|
| Primary (action) | `#30CF89` — NGO green |
| Background (dark) | `#282828` — NGO black |
| Background (light) | `#F2EEE4` — NGO beige |

---

## License

This project was developed for the Google Gemma for Good program.
