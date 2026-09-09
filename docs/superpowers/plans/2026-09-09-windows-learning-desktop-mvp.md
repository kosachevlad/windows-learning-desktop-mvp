# Windows Learning Desktop MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline Android APK for Chromebooks that teaches Windows 10-style desktop, file, Notepad, and Paint skills.

**Architecture:** A single Android application uses Kotlin and Jetpack Compose. `domain` owns the filesystem model and clipboard semantics; `data` persists metadata in Room and text/PNG bytes in app-private storage; independent `desktop`, `explorer`, `notepad`, and `paint` features render Compose windows from that state. No component requests broad filesystem or network permissions.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, DataStore Preferences, AndroidX ViewModel, Compose UI tests, JUnit.

---

## File structure

- `app/src/main/java/ua/school/windowsdesktop/MainActivity.kt` — Android entry point.
- `app/src/main/java/ua/school/windowsdesktop/App.kt` — dependency graph and top-level app state.
- `app/src/main/java/ua/school/windowsdesktop/domain/` — `FileNode`, clipboard, filenames, file operations.
- `app/src/main/java/ua/school/windowsdesktop/data/` — Room entities/DAO, private content store, repository.
- `app/src/main/java/ua/school/windowsdesktop/desktop/` — taskbar, desktop icons, windows, context menus.
- `app/src/main/java/ua/school/windowsdesktop/explorer/` — Explorer state and four-column file table.
- `app/src/main/java/ua/school/windowsdesktop/notepad/` — text editor state and unsaved-change dialog.
- `app/src/main/java/ua/school/windowsdesktop/paint/` — canvas, tools, PNG persistence.
- `app/src/main/res/values/strings.xml` and `values-uk/strings.xml` — EN and UA UI strings.
- `app/src/test/` — domain and repository unit tests.
- `app/src/androidTest/` — keyboard, context menu, editor, and locale UI tests.

### Task 1: Bootstrap a buildable Android application

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/java/ua/school/windowsdesktop/MainActivity.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/App.kt`
- Test: `app/src/androidTest/java/ua/school/windowsdesktop/AppLaunchTest.kt`

- [ ] **Step 1: Write the failing launch test.**

```kotlin
@Test fun app_shows_desktop() {
    composeTestRule.onNodeWithContentDescription("Робочий стіл").assertExists()
}
```

- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest`.** Expected: compilation fails because the app and test dependencies do not exist.
- [ ] **Step 3: Create the Gradle Android application using minSdk 26, targetSdk 35, Kotlin, Compose, Room, DataStore, JUnit and AndroidX test dependencies.** Create `MainActivity` that calls `setContent { WindowsLearningDesktopApp() }` and make the root semantic node `contentDescription = "Робочий стіл"`.
- [ ] **Step 4: Re-run `./gradlew assembleDebug` and `./gradlew connectedDebugAndroidTest`.** Expected: `BUILD SUCCESSFUL` and the launch test passes.
- [ ] **Step 5: Commit.**

```bash
git add settings.gradle.kts build.gradle.kts gradle app
git commit -m "feat: bootstrap Android learning desktop"
```

### Task 2: Define and test the isolated learning filesystem

**Files:**
- Create: `app/src/main/java/ua/school/windowsdesktop/domain/FileNode.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/domain/FileOperations.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/domain/Clipboard.kt`
- Test: `app/src/test/java/ua/school/windowsdesktop/domain/FileOperationsTest.kt`

- [ ] **Step 1: Write failing tests for create, copy/paste, rename, trash/restore, and name conflicts.**

```kotlin
@Test fun paste_copies_text_document_with_unique_name() {
    val source = document("Історія.txt", rootId, "Привіт")
    val result = operations.copy(source.id).paste(rootId)
    assertThat(result.name).isEqualTo("Історія (копія).txt")
}
```

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*FileOperationsTest'`.** Expected: fail because `FileNode` and `FileOperations` are missing.
- [ ] **Step 3: Implement immutable `FileNode(id, parentId, name, kind, modifiedAt, sizeBytes, trashedAt)` where `kind` is `FOLDER`, `TEXT`, or `PAINT`. Implement `createFolder`, `createText`, `createPaint`, `copy`, `paste`, `rename`, `moveToTrash`, and `restore`; reject blank names, `/`, and duplicate sibling names.**
- [ ] **Step 4: Re-run the unit test.** Expected: all creation, copy, rename, and trash assertions pass.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/ua/school/windowsdesktop/domain app/src/test/java/ua/school/windowsdesktop/domain
git commit -m "feat: add isolated learning filesystem rules"
```

### Task 3: Persist file metadata and content offline

**Files:**
- Create: `app/src/main/java/ua/school/windowsdesktop/data/FileEntity.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/data/FileDao.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/data/LearningDatabase.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/data/AppPrivateContentStore.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/data/LearningFileRepository.kt`
- Test: `app/src/test/java/ua/school/windowsdesktop/data/LearningFileRepositoryTest.kt`

- [ ] **Step 1: Write a failing repository test that creates `Моя історія.txt`, saves `Привіт`, reopens the repository, and reads the same text.**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*LearningFileRepositoryTest'`.** Expected: fail because no database or repository exists.
- [ ] **Step 3: Store `FileNode` metadata in Room. Store text as UTF-8 and Paint images as PNG under `context.filesDir/learning-content/<id>`; calculate file size from bytes. Make each metadata update and content write complete before publishing the updated list flow.**
- [ ] **Step 4: Re-run the repository test.** Expected: persisted text and metadata are equal after reopening.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/ua/school/windowsdesktop/data app/src/test/java/ua/school/windowsdesktop/data
git commit -m "feat: persist offline learning files"
```

### Task 4: Build the desktop shell, windows, taskbar, and language switcher

**Files:**
- Create: `app/src/main/java/ua/school/windowsdesktop/desktop/DesktopScreen.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/desktop/Taskbar.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/desktop/DesktopWindow.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/desktop/DesktopViewModel.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/settings/LanguagePreferences.kt`
- Modify: `app/src/main/java/ua/school/windowsdesktop/App.kt`
- Test: `app/src/androidTest/java/ua/school/windowsdesktop/DesktopKeyboardTest.kt`

- [ ] **Step 1: Write failing UI tests for the taskbar search icon, `УКР` language control, F2 rename dispatch, Ctrl+C/Ctrl+V dispatch, and Delete moving the selected item to trash.**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest --tests '*DesktopKeyboardTest'`.** Expected: fail because the controls and actions are absent.
- [ ] **Step 3: Implement a blue desktop, desktop icons, bottom taskbar, magnifier search button, pinned Explorer/Notepad/Paint buttons, time, and `УКР`/`ENG` menu. Route pointer secondary clicks to context menus and keyboard shortcuts through `DesktopViewModel`. Persist the chosen locale with DataStore and recompose resources immediately.**
- [ ] **Step 4: Re-run the UI test.** Expected: controls are accessible by semantic labels and keyboard actions change the test state.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/ua/school/windowsdesktop/desktop app/src/main/java/ua/school/windowsdesktop/settings app/src/main/java/ua/school/windowsdesktop/App.kt app/src/androidTest
git commit -m "feat: add Windows-style desktop shell"
```

### Task 5: Implement the Explorer with Windows-style columns and menus

**Files:**
- Create: `app/src/main/java/ua/school/windowsdesktop/explorer/ExplorerViewModel.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/explorer/ExplorerScreen.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/explorer/FileTable.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/explorer/ContextMenus.kt`
- Test: `app/src/androidTest/java/ua/school/windowsdesktop/explorer/ExplorerTest.kt`

- [ ] **Step 1: Write failing UI tests asserting headers appear in this order: `Ім’я`, `Дата змінення`, `Тип`, `Розмір`; clicking `Дата змінення` sorts descending; right-clicking blank space exposes `Створити`; and right-clicking a row exposes `Копіювати`, `Перейменувати`, `Видалити`.**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest --tests '*ExplorerTest'`.** Expected: fail because Explorer is absent.
- [ ] **Step 3: Render a `LazyColumn` whose header and every row use exactly `grid-template` equivalent Compose widths: name `weight(1f).widthIn(min = 210.dp)`, date `135.dp`, type `145.dp`, size `80.dp`. Put it in horizontal scroll; do not wrap one cell’s text into another column. Implement Explorer navigation, sort state, create dialog, row selection, and context actions by calling `LearningFileRepository`.**
- [ ] **Step 4: Re-run the Explorer test.** Expected: four non-overlapping columns, correct sort, and menus.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/ua/school/windowsdesktop/explorer app/src/androidTest/java/ua/school/windowsdesktop/explorer
git commit -m "feat: add file explorer and context actions"
```

### Task 6: Add Notepad with safe saving

**Files:**
- Create: `app/src/main/java/ua/school/windowsdesktop/notepad/NotepadViewModel.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/notepad/NotepadScreen.kt`
- Test: `app/src/androidTest/java/ua/school/windowsdesktop/notepad/NotepadTest.kt`

- [ ] **Step 1: Write failing UI tests for typing text, Ctrl+S updating `modifiedAt`, Save As creating a second `.txt`, and closing dirty text showing `Зберегти зміни?`.**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest --tests '*NotepadTest'`.** Expected: fail because Notepad is absent.
- [ ] **Step 3: Implement plain-text editor state `{ fileId, text, savedText }`, dirty state `text != savedText`, save/save-as dialogs, and close confirmation. Use repository text methods only; never access external storage.**
- [ ] **Step 4: Re-run Notepad UI tests.** Expected: text persists, duplicate names show a localized error, and discard/save both resolve the close dialog.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/ua/school/windowsdesktop/notepad app/src/androidTest/java/ua/school/windowsdesktop/notepad
git commit -m "feat: add offline notepad"
```

### Task 7: Add the first Paint canvas and PNG saving

**Files:**
- Create: `app/src/main/java/ua/school/windowsdesktop/paint/PaintTool.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/paint/PaintViewModel.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/paint/PaintScreen.kt`
- Create: `app/src/main/java/ua/school/windowsdesktop/paint/PaintCanvas.kt`
- Test: `app/src/androidTest/java/ua/school/windowsdesktop/paint/PaintTest.kt`

- [ ] **Step 1: Write failing UI tests for selecting Pencil, drawing one pointer stroke, undoing it, choosing a color, Ctrl+S creating non-zero PNG bytes, and closing unsaved drawing showing the save dialog.**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest --tests '*PaintTest'`.** Expected: fail because Paint is absent.
- [ ] **Step 3: Implement `PaintTool` values `PENCIL`, `BRUSH`, `ERASER`, `FILL`, `LINE`, `RECTANGLE`, `OVAL`, and `TEXT`. Start with a bitmap-backed canvas, action history for undo/redo, primary/secondary colors, shapes, and text placement. Encode bitmap as PNG through `LearningFileRepository`; maintain dirty state until save.**
- [ ] **Step 4: Re-run Paint tests.** Expected: exported PNG has content and saved output reopens after app restart.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/ua/school/windowsdesktop/paint app/src/androidTest/java/ua/school/windowsdesktop/paint
git commit -m "feat: add MVP paint editor"
```

### Task 8: Localize, package, and run the pilot checklist

**Files:**
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-uk/strings.xml`
- Create: `docs/pilot-checklist.md`
- Modify: `README.md`
- Test: `app/src/androidTest/java/ua/school/windowsdesktop/LocaleSwitchTest.kt`

- [ ] **Step 1: Write failing test that presses `УКР`, chooses English, asserts `My files`, closes/relaunches, and asserts English remains active.**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest --tests '*LocaleSwitchTest'`.** Expected: fail because resources and preference binding are incomplete.
- [ ] **Step 3: Add every visible string in EN and UA resources. Add a pilot checklist covering offline reboot, mouse/trackpad, physical keyboard shortcuts, create/copy/paste/rename/delete/restore, Notepad save, Paint save, and UA/EN persistence. Document debug APK output at `app/build/outputs/apk/debug/app-debug.apk`.**
- [ ] **Step 4: Run `./gradlew testDebugUnitTest assembleDebug connectedDebugAndroidTest`.** Expected: all unit/UI tests pass and `app-debug.apk` exists.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/res README.md docs/pilot-checklist.md app/src/androidTest/java/ua/school/windowsdesktop/LocaleSwitchTest.kt
git commit -m "docs: prepare offline MVP pilot"
```

## Plan self-review

- Spec coverage: Tasks 1–4 cover Android/ChromeOS shell, Windows-style interaction, offline storage, language switcher, keyboard, and safety. Task 5 covers the four Explorer columns and file operations. Tasks 6–7 cover Notepad and Paint. Task 8 covers localization, tests, APK packaging, and the pilot checklist.
- No deferred implementation placeholders are present; the later Paint redesign is explicitly excluded from MVP and retained in the design document’s roadmap.
- Type consistency: all UI features call `LearningFileRepository`; `FileNode.kind` values are consistently `FOLDER`, `TEXT`, and `PAINT`.
