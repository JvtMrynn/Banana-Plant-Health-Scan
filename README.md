# Banana Plant Health Scan (Android)

A mobile app that detects banana plant diseases from photos using a YOLOv8‑Lite model on‑device, with expert‑curated disease knowledge, admin oversight, and robust offline capabilities for farmers/guests.

## Features
- Image capture or gallery import; on‑device YOLOv8 inference and bounding‑box overlays
- Result screen enrichment: disease name, scientific name, caused by, symptoms, treatment, prevention
- Tap result image to view the original image with scroll and correctly aligned boxes
- Offline mode (farmer/guest): analyze images and view expert data from local cache
- Expert: manage disease info (CRUD), read histories, consult requests (read/update)
- Admin: approve registrations, view recent logins, publish global model updates
- Forced light theme to avoid unreadable auto‑dark

## Roles and Access
- Farmer/Guest
  - Guest: analyze offline, saves history locally; can sync after login
  - Farmer: analyze; history saved to Firestore; can view disease info
- Expert
  - Write disease_info; read analysis histories; read/update consultation requests
- Admin
  - Approve registrations; read login_events; publish model metadata; read‑only on disease/consultation

## Architecture (high‑level)
- On‑device model: PyTorch Mobile (Lite); default asset model with remote updates
- Data:
  - Firestore: users, analysis_history, consultation_requests, disease_info, login_events, ml_models
  - Room (local): disease_info cache, local analysis_history for guest/offline
- Repositories coordinate Firestore (online) and Room (offline)
- WorkManager sync pushes guest/offline history after login + connectivity

## Project Structure (key paths)
- Detection overlay: `app/src/main/java/com/example/capstoneprojectapp/DetectionImageView.java`
- Result screen: `app/src/main/java/com/example/capstoneprojectapp/ResultActivity.java`
- Fullscreen image: `app/src/main/java/com/example/capstoneprojectapp/FullscreenImageActivity.java`, layout `app/src/main/res/layout/activity_fullscreen_image.xml`
- Offline data layer (Room):
  - DB: `app/src/main/java/com/example/capstoneprojectapp/data/local/AppDatabase.java`
  - Entities: `app/src/main/java/com/example/capstoneprojectapp/data/local/entity/DiseaseInfoEntity.java`, `app/src/main/java/com/example/capstoneprojectapp/data/local/entity/AnalysisHistoryEntity.java`
  - DAOs: `app/src/main/java/com/example/capstoneprojectapp/data/local/dao/DiseaseInfoDao.java`, `app/src/main/java/com/example/capstoneprojectapp/data/local/dao/AnalysisHistoryDao.java`
  - Repository: `app/src/main/java/com/example/capstoneprojectapp/data/repo/DataRepository.java`
- Sync worker: `app/src/main/java/com/example/capstoneprojectapp/sync/HistorySyncWorker.java`
- Offline routing + farmer UI: `app/src/main/java/com/example/capstoneprojectapp/FarmerDashboardActivity.java`, `app/src/main/java/com/example/capstoneprojectapp/SplashActivity.java`
- Admin users + model publishing: `app/src/main/java/com/example/capstoneprojectapp/AdminUsersActivity.java`, `app/src/main/java/com/example/capstoneprojectapp/AdminDashboardActivity.java`

## Build & Run
- Open in Android Studio (Arctic Fox or newer)
- Min SDK 24, Target/Compile SDK 36
- Dependencies: Firebase Auth/Firestore, Room, WorkManager, PyTorch Mobile
- App enforces light theme via `App` Application class; manifest declares `android:name=".App"`

## First‑Run and Offline Flow
1. On first online launch, the app seeds `disease_info` into the Room cache in the background.
2. When offline at app start, Splash routes to the farmer dashboard with limited UI (Contact/Profile hidden). The toolbar shows “(Offline)”.
3. ResultActivity loads expert details from Firestore when online and from Room when offline; an offline banner is shown when using cached data.
4. Guest analyses are stored locally; after login & network return, WorkManager auto‑syncs to Firestore.

## Global Model Updates
- Admin publishes a direct HTTPS URL (GitHub Releases asset recommended) and a new `version` to Firestore doc `ml_models/yolov8` with fields:
  ```json
  { "version": 1.0.0-ptl-v1, "url": "https://github.com/JvtMrynn/Banana-Plant-Health-Scan/releases/download/v1.0.0-ptl-v1/yolov8_disease_v1.ptl", "updatedAt": 1700000000000 }
  ```
- Splash checks this metadata; if newer than local, downloads the model, validates (content type/size), and loads it. On failure, removes the bad file and falls back to the asset model.

## Firestore Security (summary)
- `users`: owners read/write limited profile; admin updates only `status`
- `analysis_history`: owner read/write; experts/admins read all
- `consultation_requests`: farmer create/read own; expert update; admin read
- `disease_info`: experts write; all authed read; admin read‑only
- `login_events`: admin read; signed‑in users create own
- `ml_models`: admin write; clients read

> Ensure your deployed rules reflect this model. Some queries (e.g., composite orderBy with where) may require indexes—Firestore will surface console links.

## Room Schema Export & Migrations
Room schema JSONs are exported for each DB version to support proper migrations.

- Gradle (already configured):
  ```kts
  android {
    defaultConfig {
      javaCompileOptions {
        annotationProcessorOptions {
          arguments["room.schemaLocation"] = "$projectDir/schemas"
        }
      }
    }
  }
  ```
- Schema output location: `app/schemas/` (tracked in VCS)
- Current DB: `AppDatabase` version 2. The builder uses `fallbackToDestructiveMigration()`; this is convenient in development but erases local data on version bumps.

Recommended production steps when changing the schema:
1. Bump `version` in `@Database` (e.g., 2 → 3)
2. Create a `Migration(2, 3)` object with appropriate SQL, e.g.:
   ```java
   static final Migration MIGRATION_2_3 = new Migration(2, 3) {
     @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
       db.execSQL("ALTER TABLE disease_info ADD COLUMN extraField TEXT");
     }
   };
   ```
3. In `AppDatabase.get(...)`, replace `fallbackToDestructiveMigration()` with `.addMigrations(MIGRATION_2_3 /*, more */)`
4. Build once to generate the new schema JSON in `app/schemas/` and commit it

This preserves user data across app updates.

## Developer Notes
- Detection overlays: `DetectionImageView` now computes the displayed image rect from the ImageView matrix and clips drawings so boxes never exceed the bitmap. Works with centerCrop, fitCenter, and matrix (original size in fullscreen).
- Registration validation: strict email format and strong password (≥8 chars; upper, lower, digit, symbol).
- Admin/Expert lists fetch with `Source.SERVER` and show a “Network required” dialog if offline.

## Troubleshooting
- “Schema export directory was not provided”: already configured by Gradle; ensure `app/schemas/` exists (a `.gitkeep` file is included).
- Model update fails with “Format error”: ensure URL points to a PyTorch Lite `.ptl` binary; avoid HTML download pages.
- Firestore permission errors: verify rules and user roles/approval; check for required indexes.
- Overlays misaligned: ensure `Detection` bounding boxes are normalized [0–1] relative to the original image size fed to the model.

## License
This project contains third‑party libraries; review their licenses (Firebase, PyTorch Mobile, AndroidX).

