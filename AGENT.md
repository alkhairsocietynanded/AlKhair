# Alkhair Project Architecture & Patterns

This document outlines the architectural patterns, technology stack, and coding standards used in the Alkhair Android application. It serves as a guide for developers and AI agents working on the codebase.

## 🏗 Architecture Overview

The project follows the **MVVM (Model-View-ViewModel)** architecture with the **Repository Pattern** and **Clean Architecture** principles.

-   **UI Layer**: Activities/Fragments (View) observe data from ViewModels.
-   **ViewModel Layer**: Manages UI state, handles business logic, and interacts with Repositories. Uses `StateFlow` for reactive data streams.
-   **Data Layer**:
    -   **Repositories/Managers**: Abstract data sources. Manage synchronization between Local DB and Remote Backend.
    -   **Local Data**: Room Database (SQLite) as the single source of truth for the UI.
    -   **Remote Data**: Supabase (Backend-as-a-Service).
    -   **Synchronization**: `AppDataSyncManager` handles data consistency.

## 🛠 Technology Stack

-   **Language**: Kotlin
-   **DI**: Hilt (Dagger)
-   **Async**: Coroutines & Flow
-   **Database**: Room
-   **Network**: Retrofit (for n8n/custom APIs) & Supabase SDK (implied)
-   **UI**: XML Layouts & ViewBinding (Transitioning to optimal patterns, no Compose yet per user rules)
-   **Background Work**: WorkManager

## 🧩 Key Patterns

### 1. Synchronization (`AppDataSyncManager`)
The app uses a robust "Offline-First" synchronization strategy.
-   **Single Source of Truth**: The UI *always* observes the local Room database.
-   **Sync Process**:
    1.  **Trigger**: `syncAllData()` is called (e.g., on app start, refresh, or periodic worker).
    2.  **Locking**: A `Mutex` ensures only one sync runs at a time.
    3.  **Metadata First**: Syncs Classes and Divisions sequentially first.
    4.  **Role-Based Sync**:
        -   **Admin**: Syncs ALL data (global sync).
        -   **Teacher**: Syncs Global Announcements, Personal Salary, and Class-specific data (Students, Attendance, etc.).
        -   **Student**: Syncs Personal Profile, Fees, Attendance, and Class-specific data.
    5.  **Deletions**: Checks a `deleted_records` table (Tombstone pattern) via `SupabaseDeletionRepository` to remove local stale data.
    6.  **Timestamp**: Tracks `last_sync_timestamp` in `AppDataStore` to fetch only delta changes (`updated_at > last_sync`).

### 2. Repository / Manager Pattern
Data access is managed by "RepoManagers" (e.g., `UserRepoManager`, `TimetableRepoManager`).
-   **Structure**:
    -   `observeLocal()`: Returns `Flow<List<T>>` from Room DAO.
    -   `sync(after: Long)`: Fetches remote data and inserts/updates local DB.
    -   **Creation/Updates**:
        -   **User Creation**: Remote First (Create in Supabase Auth -> Sync to DB).
        -   **Other Entities**: **Local First** (Insert to Room -> Schedule `WorkManager` for background upload).

### 3. UI State Management
ViewModels use Sealed Classes to represent UI states.
```kotlin
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Error(val message: String) : UiState()
}
```
-   `StateFlow` requires a `SharingStarted.WhileSubscribed(5000)` strategy to conserve resources when the UI is not visible.

### 4. Dependency Injection (Hilt)
-   **AppModule**: Provides singletons like `AppDatabase`, DAOs, `Gson`, `AppDataStore`.
-   **ViewModels**: Annotated with `@HiltViewModel` and `@Inject`.

## 📂 Project Structure

-   `com.aewsn.alkhair`
    -   `di`: Hilt Data Injection Modules (`AppModule`)
    -   `data`:
        -   `local`: Room DB (`dao`, `database`, `entities`)
        -   `remote`: Networking (`supabase`, `api`)
        -   `models`: Data classes
        -   `manager`: Repository Managers (`AppDataSyncManager`, `*RepoManager`)
        -   `datastore`: Preferences (`AppDataStore`)
    -   `ui`: Feature-based packaging (e.g., `dashboard`, `timetable`, `student`)
    -   `utils`: Constants, extensions (`Roles`, constants)

## 📝 Coding Standards

1.  **Naming**:
    -   Classes: PascalCase (e.g., `TimetableFragment`)
    -   Functions/Variables: camelCase (e.g., `syncAllData`)
    -   Layouts: snake_case (e.g., `activity_teacher_dashboard.xml`)
2.  **Logging**: Use `TAG` constants in companion objects.
3.  **Comments**: Document complex logic (like Sync strategies).
4.  **Safe Calls**: extensive use of `?` and safe casts `as?` to prevent crashes.

## 🚀 Workflows

### Adding a New Feature
1.  **Data**: Define Entity in `data/models`, create DAO in `data/local/dao`, add to `AppDatabase`.
2.  **Repo**: Create `FeatureRepoManager` handling sync and CRUD. Add to `AppDataSyncManager`.
3.  **UI**: Create `Fragment/Activity` with ViewBinding.
4.  **ViewModel**: Inject Repo, expose `StateFlow`s.
