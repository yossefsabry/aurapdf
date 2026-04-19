# AuraPDF Phase 1 Design Spec

**Date:** 2026-04-19
**Status:** Approved

## Goal

Build Phase 1 of AuraPDF: a clean Kotlin/Compose Android app with a PDF library screen, SAF-based file picker, PDF rendering with page/scroll position tracking, and persistent storage via Room.

## Architecture

Clean Architecture with Hilt DI. Three layers: `data` (Room, SAF, RepositoryImpl), `domain` (models, interfaces, use cases), `presentation` (Compose screens + ViewModels). Layers communicate through well-defined interfaces — domain never imports data.

## Tech Stack

- **Language:** Kotlin, Jetpack Compose (BOM 2024.02.00)
- **DI:** Hilt 2.51.1 + hilt-navigation-compose 1.2.0
- **Database:** Room 2.6.1 (SQLite)
- **PDF Rendering:** Android built-in `PdfRenderer` (API 21+, no extra deps)
- **File Picking:** SAF `ACTION_OPEN_DOCUMENT` with `takePersistableUriPermission`
- **Navigation:** Navigation Compose 2.7.7
- **Preferences:** DataStore Preferences 1.1.1
- **Lifecycle:** ViewModel Compose 2.7.0

## File Structure

```
app/src/main/java/com/aurapdf/app/
├── AuraPdfApplication.kt           @HiltAndroidApp entry point
├── MainActivity.kt                  @AndroidEntryPoint, hosts NavHost
│
├── di/
│   ├── AppModule.kt                 DataStore, RepositoryImpl binding
│   └── DatabaseModule.kt            Room database + DAO provision
│
├── data/
│   ├── local/
│   │   ├── db/AuraPdfDatabase.kt    Room database definition
│   │   ├── dao/PdfDocumentDao.kt    CRUD + recent query
│   │   └── entity/PdfDocumentEntity.kt  Room entity (uri, name, lastPage, scrollOffset, dateAdded)
│   └── repository/PdfRepositoryImpl.kt  SAF URI management + DAO delegation
│
├── domain/
│   ├── model/PdfDocument.kt         Clean domain model (no Room annotations)
│   ├── repository/PdfRepository.kt  Interface (getAllDocuments, insert, delete, updatePosition)
│   └── usecase/
│       ├── GetPdfLibraryUseCase.kt  Returns Flow<List<PdfDocument>>
│       ├── AddPdfDocumentUseCase.kt Persists SAF URI with permissions
│       ├── DeletePdfDocumentUseCase.kt Removes doc + releases URI permission
│       └── SaveReadingPositionUseCase.kt Updates lastPage + scrollOffset
│
└── presentation/
    ├── navigation/NavGraph.kt        Compose nav graph (Home → Viewer)
    ├── home/
    │   ├── HomeScreen.kt            Library grid, FAB to pick PDF, empty state
    │   └── HomeViewModel.kt         Hilt ViewModel, exposes StateFlow<HomeUiState>
    └── viewer/
        ├── PdfViewerScreen.kt       Renders PDF pages via PdfRenderer in LazyColumn
        └── PdfViewerViewModel.kt    Loads document, tracks page/scroll, saves position

app/src/main/res/
├── drawable/
│   ├── ic_launcher_foreground.xml   Black & white vector: document page + aura rays
│   └── ic_launcher_background.xml  Solid white background
└── mipmap-anydpi-v26/
    ├── ic_launcher.xml              Adaptive icon
    └── ic_launcher_round.xml        Round adaptive icon
```

## Data Model

**PdfDocumentEntity (Room)**
```
id: Long (PK autoGenerate)
uri: String (SAF URI, persistable)
name: String (display name from SAF)
lastPage: Int (0-indexed, default 0)
scrollOffset: Int (pixel offset within page, default 0)
dateAdded: Long (System.currentTimeMillis())
totalPages: Int (cached on first open)
```

## Key Design Decisions

1. **PdfRenderer over barteksc/pdfium**: Built-in, no extra deps, works natively in Compose with `Image` composable. Renders each page to `Bitmap` on IO dispatcher.
2. **Position tracking**: Save `lastPage` + `scrollOffset` to Room on `onStop` of viewer, restore on open.
3. **SAF permissions**: Call `contentResolver.takePersistableUriPermission` immediately after user picks file. Store the string URI in Room.
4. **Hilt + @AndroidEntryPoint**: Application, MainActivity annotated; ViewModels use `@HiltViewModel`.
5. **Icon**: Pure vector XML (no PNG assets needed for API 24+ with `useSupportLibrary = true`).
