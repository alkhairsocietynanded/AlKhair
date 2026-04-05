# AlKhair Project Architecture & Patterns

This document outlines the architectural patterns, technology stack, and coding standards used in the AlKhair Android application. It serves as a guide for developers and AI agents working on the codebase.

## 🏗 Architecture Overview

The project follows the **MVVM (Model-View-ViewModel)** architecture with the **Repository Pattern** and **Clean Architecture** principles.

- **UI Layer**: Activities/Fragments (View) observe data from ViewModels. ViewBinding is strictly used (No Jetpack Compose yet).
- **ViewModel Layer**: Manages UI state, handles business logic, and interacts with RepoManagers. Uses `StateFlow` for reactive data streams.
- **Data Layer**:
    - **Managers/Repositories**: Abstract data sources (`*RepoManager` classes). Control the synchronization logic and data flow.
    - **Local Data**: Room Database (SQLite) acting as the Single Source of Truth for the UI.
    - **Remote Data**: Supabase (Backend-as-a-Service) handles Auth, PostgREST (Database), and Storage.
    - **Background Sync**: `WorkManager` heavily utilized for offline-first operations (`*UploadWorker`).

## 🛠 Technology Stack

Detailed in `libs.versions.toml`:
- **Android & Kotlin**: AGP 9.1.0, Kotlin 2.3.20, KSP 2.3.6.
- **Dependency Injection**: Hilt (Dagger) (`@HiltAndroidApp`, `@HiltWorker`, `@HiltViewModel`).
- **Async & Reactive**: Kotlin Coroutines & Flow.
- **Database**: Room 2.8.4.
- **Network**: Retrofit (n8n/Custom APIs) & Supabase SDK (Auth, Postgrest, Storage, Functions).
- **Push Notifications**: Firebase Cloud Messaging (FCM) via Supabase Edge Functions.
- **UI & Layouts**: XML Layouts, ViewBinding, Material 3, ConstraintLayout, MPAndroidChart (Graphs), Kizitonwose Calendar, SwipeRefreshLayout.
- **Background Work**: WorkManager (for generic and data upload work).
- **Utilities**: MLKit Barcode Scanning + CameraX (Scanner feature), Markwon (Markdown rendering).

## 🧩 Key Patterns

### 1. Synchronization (`AppDataSyncManager`)
The app uses a robust "Offline-First" server synchronization strategy.
- **Single Source of Truth**: The UI *always* observes the local Room database (`Flow<List<T>>`).
- **Sync Process**:
    1. **Trigger**: `syncAllData()` is called (e.g., on app start, refresh).
    2. **Locking**: A `Mutex` ensures only one sync runs at a time.
    3. **Metadata First**: Syncs core hierarchies (Classes, Divisions, Subjects) sequentially first.
    4. **Role-Based Sync**:
        - **Admin**: Syncs ALL data (global sync).
        - **Teacher**: Syncs Global Announcements, Personal Salary, and Class-specific data (Students, Attendance, Homework, etc.).
        - **Student**: Syncs Personal Profile, Fees, Attendance, and Class-specific data.
    5. **Deletions**: Checks a `deleted_records` table (Tombstone pattern) to remove local stale data via `SupabaseDeletionRepository`.
    6. **Timestamp Delta**: Tracks `last_sync_timestamp` in `AppDataStore` to fetch only delta changes (`updated_at_ms > last_sync`).

### 2. The Repository/Manager Pattern (`*RepoManager`)
Data access logic is completely encapsulated within "RepoManager" classes (e.g., `UserRepoManager`, `TimetableRepoManager`).
- **Structure**:
    - `observeLocal()`: Returns `Flow<List<T>>` directly from the Room DAO to the ViewModel.
    - `sync(after: Long)`: Fetches delta remote data from Supabase and performs bulk inserts/updates to the local DB.
    - **Creation/Updates (Local First)**: Most entity creation functions first write to the Room database, then enqueue a `*UploadWorker` via WorkManager for background syncing to Supabase.
    - **Exceptions (Remote First)**: User creation happens in Supabase Auth first, then syncs locally.

### 3. Background Workers (`*UploadWorker`)
Extensive use of `WorkManager` for asynchronous uploads:
- Every data model capable of offline mutation has a corresponding worker (e.g., `HomeworkUploadWorker`, `AttendanceUploadWorker`).
- This guarantees that offline changes are pushed to Supabase reliably once network connectivity is restored.

### 4. Push Notifications (FCM + Supabase)
- Devices register their FCM tokens locally and sync them to Supabase.
- Supabase Edge Functions coupled with Database Webhooks are utilized to trigger push notifications when certain database events occur (e.g., new Homework, new Announcement).

### 5. Dependency Injection (Hilt)
- **AppModule**: Provides global singletons like `AppDatabase`, all DAOs, `Gson`, `AppDataStore`, Retrofit instances.
- **ViewModels**: Initialized with `@HiltViewModel` and constructor `@Inject`.
- **Workers**: Initialized with `@HiltWorker`.

### 6. UI State Management
ViewModels must map repository `Flow`s to a sealed UI state.
```kotlin
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```
- `StateFlow` requires a `SharingStarted.WhileSubscribed(5000)` strategy to conserve resources when the UI is not visible.

## 📂 Project Structure

- `com.aewsn.alkhair`
    - `di`: Hilt Dependency Injection Modules (`AppModule.kt`).
    - `data`:
        - `local`: Room Database implementation (`dao`, `database`, `entities`).
        - `remote`: Networking logic (`supabase` API, Retrofit `api`).
        - `models`: Universal data models/DTOs.
        - `manager`: Abstract Repositories (`AppDataSyncManager`, `base`, `*RepoManager`).
        - `datastore`: Jetpack DataStore implementations (`AppDataStore`).
        - `worker`: WorkManager classes (`*UploadWorker`).
        - `repository`: Core repositories without specific manager-level abstractions (like Auth, Deletion logic).
    - `ui`: Feature-based packaging (e.g., `dashboard`, `timetable`, `student`, `scanner`, `auth`).
    - `utils`: Shared constants, extensions, role definitions.

## 📝 Coding Standards

1. **Naming**:
    - Classes: `PascalCase` (e.g., `TimetableFragment`).
    - Functions/Variables: `camelCase` (e.g., `syncAllData()`, `loadHomework()`).
    - Layouts: `snake_case` (e.g., `fragment_teacher_dashboard.xml`, `item_student_attendance.xml`).
2. **Safety & Kotlin Idioms**:
    - Avoid `!!`. Extensive use of `?` and safe casts `as?` to prevent `NullPointerException` crashes.
3. **Logging**: Utilize `companion object { const val TAG = "ClassName" }` and Android `Log` for debugging.
4. **Resources**: Hardcoded strings are discouraged in layouts. Use `strings.xml`.

## 🚀 Workflows

### 1. Adding a New Data Model/Feature
1. **Data layer**: Define the Room `Entity` in `data/local/entities`. Create its `Dao` in `data/local/dao`. Add the `Dao` to `AppDatabase` and `AppModule`.
2. **Worker**: Create a `ModelUploadWorker` in `data/worker` to handle offline-first background syncing to Supabase.
3. **RepoManager**: Create a `ModelRepoManager` in `data/manager`. Implement `sync()`, `observeLocal()`, and local-first upload methods. Incorporate it into `AppDataSyncManager`.
4. **UI Layer**: Create the necessary `Fragment` inside a new package in `ui/`. Handle XML layouts (`fragment_model.xml`).
5. **ViewModel**: Create a `ModelViewModel` utilizing Hilt, exposing a `StateFlow<UiState>` mapped directly from `ModelRepoManager.observeLocal()`.

### 2. Modifying Database Schema
If altering an existing Room entity:
1. Increment the `version` in `@Database` annotation of `AppDatabase`.
2. Write a Room Migration function if moving beyond destructive actions (though destructive migration may be preferred depending on stage).
3. Ensure Supabase PostgREST table schema mirrors the local changes precisely.
