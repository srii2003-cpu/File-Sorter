# Phase 0: Skeleton and Guardrails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that launches, asks for photo access correctly on Android 11–12, 13 and 14+, opens an encrypted Room database with a Keystore-wrapped key, and fails its own build if a network permission ever appears in the merged manifest.

**Architecture:** Two Gradle modules plus an included build. `:classifier` is plain Kotlin (domain types now, all rules in Phase 1). `:app` is single-activity Compose + Hilt; it owns the database, key store and permission flow. `build-logic` holds the manifest guard as a unit-tested Gradle plugin applied to `:app`. No navigation library yet: the top-level screen is chosen from permission state (navigation arrives with Phase 2 screens).

**Tech Stack:** Gradle 9.8.2 (wrapper, SHA-256 pinned), AGP 9.5.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1, Compose BOM 2026.09.00, Room 2.8.5, `net.zetetic:sqlcipher-android` 4.20.0, JUnit 4. Versions live in `gradle/libs.versions.toml` (already written).

**Spec:** V3 spec "Private Image Organiser – Spec Design" (8 Nov 2026), to be saved as `docs/spec/spec-v3.pdf`; build prompt in `docs/spec/build-prompt.md`; agreed deviations in `docs/decisions.md`. Rules for agents: `CLAUDE.md`.

## Global Constraints

- Merged manifest must not contain `INTERNET` or `ACCESS_NETWORK_STATE`; build fails if it does.
- Never request `MANAGE_EXTERNAL_STORAGE`, accessibility services or notification listeners.
- No exported components except the launcher activity.
- `allowBackup="false"` and `dataExtractionRules` excluding all app data from cloud backup and device transfer.
- No OCR text, file paths or ID numbers logged; release builds strip `android.util.Log`.
- minSdk 30; compileSdk/targetSdk 37 (installed platform `android-37.0`).
- Database key: random 256-bit, wrapped by an Android Keystore AES-GCM key with no user-authentication requirement. Key file lives in `noBackupFilesDir`.
- Database is a rebuildable cache: open failure or key loss → delete database and key, create fresh. Originals are never touched.
- OCR search uses FTS4 through Room (D3). `image` has `in_review` and `review_reason` (D2); `direction` is nullable (D4).
- Gradle runs on Android Studio's bundled JBR: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (JDK 25; see D12).
- No library beyond the version catalog without saying why.

## Review Focus

1. **Photo access revoked in Settings while the app is in the background** → on return the app shows the access screen, never a crash or stale "granted" home. Test: Task 6 Step 5.
2. **Keystore key gone but the wrapped key file still exists** (device restore, OEM Keystore reset) → app opens an empty, working database. Test: Task 5 `keyLoss_rebuildsEmptyDatabase`.
3. **Database file corrupted** (garbage bytes) → rebuild, not a crash loop. Test: Task 5 `corruptFile_rebuildsEmptyDatabase`.
4. **Android 14+ partial access with zero photos selected** → treated as partial (banner with 0 and "Allow all"), not as denied. Test: Task 4 `sdk34_userSelectedOnly_isPartial` plus Task 6 Step 5.
5. **A future library sneaks in `INTERNET` via `uses-permission-sdk-23`** → guard catches both tag forms. Test: Task 2 `sdk23Form_isViolation`.

---

### Task 1: Classifier module with V1 taxonomy types

**Files:**
- Create: `classifier/build.gradle.kts`
- Create: `classifier/src/main/kotlin/com/narasa/imageorganiser/classifier/Taxonomy.kt`
- Test: `classifier/src/test/kotlin/com/narasa/imageorganiser/classifier/TaxonomyTest.kt`

**Interfaces:**
- Produces (all in package `com.narasa.imageorganiser.classifier`):
  - `enum class PrimaryCategory { DOCUMENT, SCREENSHOT, PHOTO, GRAPHIC, REVIEW }`
  - `enum class TagName { SENSITIVE, RECEIPT, SCHOOLWORK, OFFICE, EVENT_INFO, QR, JUNK_CANDIDATE }`
  - `enum class TagState { SUGGESTED, CONFIRMED, REJECTED, TRASHED }`
  - `enum class TagKind { BUILTIN, USER }`
  - `enum class ImageStatus { PENDING, DONE, OCR_FAILED, UNREADABLE }`
  - `enum class Direction { SENT, RECEIVED }`
  - `enum class Source { WHATSAPP, TELEGRAM, DOWNLOADS, SCREENSHOTS, SHARED_IN }`

- [ ] **Step 1: Write `classifier/build.gradle.kts`** applying `libs.plugins.kotlin.jvm`, JVM target 17, `testImplementation(libs.junit)`.
- [ ] **Step 2: Write the failing test** `TaxonomyTest`:
  - `primaryCategories_matchSpec`: `PrimaryCategory.entries.map { it.name } == listOf("DOCUMENT","SCREENSHOT","PHOTO","GRAPHIC","REVIEW")`
  - `tags_matchBuildPrompt`: `TagName.entries.map { it.name } == listOf("SENSITIVE","RECEIPT","SCHOOLWORK","OFFICE","EVENT_INFO","QR","JUNK_CANDIDATE")`
  - `tagStates_matchStateMachine`: `TagState.entries.map { it.name } == listOf("SUGGESTED","CONFIRMED","REJECTED","TRASHED")`
- [ ] **Step 3: Run** `./gradlew :classifier:test` — expect compile FAIL (types missing).
- [ ] **Step 4: Implement** the enums in `Taxonomy.kt`.
- [ ] **Step 5: Run** `./gradlew :classifier:test` — expect PASS. These names are persisted by Room as text, so renaming one later is a schema migration; the test pins them.
- [ ] **Step 6: Commit** gradle wrapper, root build files, `.gitignore`, `classifier/`: `build: gradle skeleton and classifier taxonomy`.

### Task 2: Manifest guard plugin (build-logic)

**Files:**
- Create: `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts` (`kotlin-dsl`, depends on AGP `com.android.tools.build:gradle` 9.5.1 as `compileOnly`, JUnit for tests)
- Create: `build-logic/src/main/kotlin/ManifestGuard.kt`
- Create: `build-logic/src/main/kotlin/ManifestGuardPlugin.kt` (plugin id `imageorganiser.manifest-guard`)
- Test: `build-logic/src/test/kotlin/ManifestGuardTest.kt`
- Modify: `settings.gradle.kts` (`pluginManagement { includeBuild("build-logic") }`)

**Interfaces:**
- Produces:
  - `object ManifestGuard { fun violations(manifestXml: String, allowedExported: Set<String>): List<String> }`. Parse with a namespace-aware `DocumentBuilder`. Violations:
    - any `uses-permission` or `uses-permission-sdk-23` named `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE` or `android.permission.MANAGE_EXTERNAL_STORAGE`;
    - any `service` whose `android:permission` is `android.permission.BIND_ACCESSIBILITY_SERVICE` or `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`;
    - any `activity`, `activity-alias`, `service`, `receiver` or `provider` with `android:exported="true"` whose `android:name` is not in `allowedExported`.
    - Each violation string names the element and the offending name.
  - Plugin: for each variant `X`, registers `check<X>Manifest` reading `SingleArtifact.MERGED_MANIFEST`, writing `build/reports/manifest-guard/<variant>.txt`, failing with all violations listed. `check`, `package<X>` and `bundle<X>` depend on it. Extension `manifestGuard { allowedExported.add("…") }`.

- [ ] **Step 1: Write failing tests** in `ManifestGuardTest` using inline XML strings:
  - `cleanManifest_hasNoViolations` (launcher activity exported and allowed) → `emptyList()`
  - `internet_isViolation` → one violation containing `"android.permission.INTERNET"`
  - `accessNetworkState_isViolation`
  - `sdk23Form_isViolation` (`<uses-permission-sdk-23 android:name="android.permission.INTERNET"/>`)
  - `manageExternalStorage_isViolation`
  - `accessibilityService_isViolation`
  - `exportedReceiver_notAllowed_isViolation` (`androidx.profileinstaller.ProfileInstallReceiver`, exported true)
  - `exportedFalse_isFine`
- [ ] **Step 2: Run** `./gradlew -p build-logic test` — expect FAIL.
- [ ] **Step 3: Implement** `ManifestGuard` and `ManifestGuardPlugin`.
- [ ] **Step 4: Run** `./gradlew -p build-logic test` — expect PASS.
- [ ] **Step 5: Commit** `build: manifest guard plugin with tests`. (The guard is applied in Task 3, once `:app` exists.)

### Task 3: App shell, manifest hardening, release log stripping

**Files:**
- Create: `app/build.gradle.kts` — plugins: android.application, kotlin.compose, ksp, hilt, room, `imageorganiser.manifest-guard`. `namespace`/`applicationId` = `com.narasa.imageorganiser`; minSdk 30; compileSdk/targetSdk 37; Java 17; release `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`; `room { schemaDirectory("$projectDir/schemas") }`; `manifestGuard { allowedExported.add("com.narasa.imageorganiser.MainActivity") }`; depends on `:classifier`.
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/java/com/narasa/imageorganiser/App.kt` (`@HiltAndroidApp`), `MainActivity.kt` (`@AndroidEntryPoint`, shows a placeholder `Text` for now)
- Create: `scripts/check-release-no-logging.sh`

**Interfaces:**
- Produces: `App`, `MainActivity` (filled in Task 6); the variant tasks `checkDebugManifest`, `checkReleaseManifest`.

Manifest contents (exact):
- `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` and the same for `ACCESS_NETWORK_STATE` (strips library additions, e.g. ML Kit transport in Phase 1).
- `READ_EXTERNAL_STORAGE` with `android:maxSdkVersion="32"`; `READ_MEDIA_IMAGES`; `READ_MEDIA_VISUAL_USER_SELECTED`.
- `<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" tools:node="remove"/>` (exported benchmark receiver pulled in by Compose).
- `<application android:allowBackup="false" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules" …>`
- `data_extraction_rules.xml`: under both `<cloud-backup>` and `<device-transfer>`, `<exclude domain="X" path="."/>` for `root`, `file`, `database`, `sharedpref`, `external`.

`proguard-rules.pro`:
```
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...); public static int d(...); public static int i(...);
    public static int w(...); public static int e(...); public static int wtf(...);
    public static int println(...);
}
-keep class net.zetetic.database.** { *; }
```

`check-release-no-logging.sh <apk>`: unzip `classes*.dex` to a temp dir, run `$ANDROID_HOME/build-tools/36.0.0/dexdump -d`, count lines matching `Landroid/util/Log;\.(v|d|i|w|e|wtf|println):`; print the count, exit 1 if non-zero.

- [ ] **Step 1: Write the files above; run** `./gradlew :app:assembleDebug` — expect BUILD SUCCESSFUL and `app/build/reports/manifest-guard/debug.txt` reporting no violations.
- [ ] **Step 2: Prove the guard fails the build.** Temporarily add `<uses-permission android:name="android.permission.INTERNET"/>` to a new `app/src/debug/AndroidManifest.xml`; run `./gradlew :app:assembleDebug` — expect FAILED naming `android.permission.INTERNET`. Delete the file.
- [ ] **Step 3: Prove the log check detects calls.** `scripts/check-release-no-logging.sh app/build/outputs/apk/debug/app-debug.apk` — expect non-zero count, exit 1 (debug keeps library `Log` calls).
- [ ] **Step 4: Run** `./gradlew :app:assembleRelease` then the script on `app/build/outputs/apk/release/app-release-unsigned.apk` — expect `0`, exit 0.
- [ ] **Step 5: Install and launch** `./gradlew :app:installDebug && adb shell am start -n com.narasa.imageorganiser/.MainActivity` — expect the placeholder on the phone.
- [ ] **Step 6: Commit** `feat: app shell with manifest guard, backup off, release log stripping`.

### Task 4: Photo permission logic

**Files:**
- Create: `app/src/main/java/com/narasa/imageorganiser/permission/PhotoPermissions.kt`
- Test: `app/src/test/java/com/narasa/imageorganiser/permission/PhotoPermissionsTest.kt`

**Interfaces:**
- Produces:
  - `enum class PhotoAccess { FULL, PARTIAL, NONE }`
  - `object PhotoPermissions { fun required(sdkInt: Int): Array<String>; fun access(sdkInt: Int, isGranted: (String) -> Boolean): PhotoAccess }`
  - `required`: `< 33` → `[READ_EXTERNAL_STORAGE]`; `33` → `[READ_MEDIA_IMAGES]`; `>= 34` → `[READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED]` (Android requires both in one request on 34+).
  - `access`: `>= 34`: IMAGES → FULL, else USER_SELECTED → PARTIAL, else NONE. `33`: IMAGES → FULL else NONE. `< 33`: READ_EXTERNAL_STORAGE → FULL else NONE.

- [ ] **Step 1: Write failing tests** (permission strings as literals, e.g. `"android.permission.READ_MEDIA_IMAGES"`):
  - `sdk30_requiresReadExternalOnly`, `sdk32_requiresReadExternalOnly`, `sdk33_requiresMediaImagesOnly`, `sdk34_requiresBoth`, `sdk35_requiresBoth`
  - `sdk30_granted_isFull`, `sdk30_denied_isNone`, `sdk33_granted_isFull`, `sdk33_readExternalOnly_isNone`
  - `sdk34_mediaImages_isFull`, `sdk34_userSelectedOnly_isPartial`, `sdk35_nothing_isNone`
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*PhotoPermissionsTest'` — expect FAIL.
- [ ] **Step 3: Implement** `PhotoPermissions.kt`.
- [ ] **Step 4: Run** the same command — expect PASS.
- [ ] **Step 5: Commit** `feat: photo permission rules per Android version`.

### Task 5: Keystore-wrapped key and encrypted database

**Files:**
- Create: `app/src/main/java/com/narasa/imageorganiser/data/crypto/DatabaseKeyStore.kt`
- Create: `app/src/main/java/com/narasa/imageorganiser/data/db/Entities.kt`, `Daos.kt`, `AppDatabase.kt`, `DatabaseProvider.kt`
- Create: `app/src/main/java/com/narasa/imageorganiser/di/DataModule.kt`
- Create: `app/schemas/` (generated by Room; committed)
- Test: `app/src/androidTest/java/com/narasa/imageorganiser/data/DatabaseKeyStoreTest.kt`, `EncryptedDatabaseTest.kt`

**Interfaces:**
- Consumes: Task 1 enums.
- Produces:
  - `class DatabaseKeyStore(context: Context, keyAlias: String, keyFileName: String)` with `fun getOrCreateKey(): ByteArray` (32 bytes), `fun reset()` (deletes alias and file). Throws `KeyUnavailableException` (its own class in the same file) when the file exists but cannot be unwrapped (alias missing or any `GeneralSecurityException`). File format: `[version byte 1][12-byte IV][ciphertext+tag]` in `context.noBackupFilesDir`. Keystore key: AES-256, GCM, `NoPadding`, `setUserAuthenticationRequired(false)`.
  - Entities (Room table names exact): `ImageEntity` → `image` (`id` autogen, `media_id` unique index, `source: Source`, `direction: Direction?`, `taken_date: Long`, `dhash: Long?`, `width: Int`, `height: Int`, `primary_category: PrimaryCategory?` (null while pending), `primary_reason: String?`, `in_review: Boolean = false`, `review_reason: String?`, `status: ImageStatus`, `model_version: String?`); `OcrTextEntity` → `ocr_text`, `@Fts4`, `@PrimaryKey @ColumnInfo(name = "rowid") imageId: Long`, `text: String` (rowid = `image.id`; FTS tables cannot hold foreign keys, so Phase 1 deletion sync deletes these rows explicitly); `TagEntity` → `tag` (`id`, `name` unique, `kind: TagKind`); `ImageTagEntity` → `image_tag` (PK `image_id`+`tag_id`, FKs to `image` and `tag` with `CASCADE`, `reason: String`, `state: TagState`, `changed_at: Long`).
  - `ImageDao`: `suspend fun insert(image: ImageEntity): Long`, `suspend fun count(): Int`. `OcrDao`: `suspend fun insert(row: OcrTextEntity)`, `suspend fun search(query: String): List<ImageEntity>` (`SELECT image.* FROM image JOIN ocr_text ON image.id = ocr_text.rowid WHERE ocr_text MATCH :query`).
  - `AppDatabase` version 1, `exportSchema = true`, no destructive-migration fallback.
  - `class DatabaseProvider(context: Context, keyStore: DatabaseKeyStore, dbName: String)` with `fun get(): AppDatabase` (synchronized, call off the main thread) and `var lastOpenRebuilt: Boolean`. Opening: `System.loadLibrary("sqlcipher")`; key passed as the SQLCipher raw-key string `"x'<64 hex>'"` bytes to `SupportOpenHelperFactory`; force open with `openHelper.writableDatabase`. On `KeyUnavailableException` or `SQLiteException` (`android.database.sqlite`): close, delete the database files (`deleteDatabase`), `keyStore.reset()`, open once more, set `lastOpenRebuilt = true`.
  - `DataModule` (Hilt, `SingletonComponent`): provides `DatabaseKeyStore(ctx, "index_key_wrap", "index_key.bin")` and `DatabaseProvider(ctx, keyStore, "index.db")`.

- [ ] **Step 1: Write failing instrumented tests.** Each test uses its own alias/file/db name (e.g. `"test_wrap_" + UUID`) and resets in `@After`.
  - `DatabaseKeyStoreTest.createsThirtyTwoByteKey`
  - `DatabaseKeyStoreTest.sameKeyOnSecondCall`
  - `DatabaseKeyStoreTest.keyFileDoesNotContainRawKey` (file bytes do not contain the 32 key bytes as a run)
  - `DatabaseKeyStoreTest.aliasDeleted_throwsKeyUnavailable`
  - `EncryptedDatabaseTest.fts4Search_findsInsertedText` (insert image + OCR "Tutorial 3 Linear Algebra"; `search("algebra")` returns it) — this confirms SQLCipher ships FTS4 (D3)
  - `EncryptedDatabaseTest.dataSurvivesReopen` (close, new provider, same names → `count() == 1`)
  - `EncryptedDatabaseTest.fileHeaderIsNotPlainSqlite` (first 16 bytes ≠ `"SQLite format 3\u0000"`)
  - `EncryptedDatabaseTest.plainSqliteCannotOpenFile` (`android.database.sqlite.SQLiteDatabase.openDatabase(path, null, OPEN_READONLY)` then a query throws)
  - `EncryptedDatabaseTest.keyLoss_rebuildsEmptyDatabase` (insert, delete the Keystore alias, new provider → `count() == 0`, `lastOpenRebuilt == true`)
  - `EncryptedDatabaseTest.corruptFile_rebuildsEmptyDatabase` (overwrite the db file with 4 KB of random bytes → `count() == 0`, `lastOpenRebuilt == true`)
- [ ] **Step 2: Run** `./gradlew :app:connectedDebugAndroidTest` — expect compile FAIL.
- [ ] **Step 3: Implement** the files above.
- [ ] **Step 4: Run** `./gradlew :app:connectedDebugAndroidTest` on the Nord 4 — expect 10/10 PASS. If FTS4 fails, stop and report (D3 depends on it).
- [ ] **Step 5: Commit** `feat: SQLCipher Room database with Keystore-wrapped key`, including `app/schemas/`.

### Task 6: Access screens, revocation check, database status

**Files:**
- Create: `app/src/main/java/com/narasa/imageorganiser/MainViewModel.kt`
- Create: `app/src/main/java/com/narasa/imageorganiser/ui/AccessScreen.kt`, `ui/HomePlaceholder.kt`, `ui/Theme.kt`
- Create: `app/src/main/java/com/narasa/imageorganiser/permission/SelectedPhotoCounter.kt`
- Modify: `MainActivity.kt`, `res/values/strings.xml`

**Interfaces:**
- Consumes: `PhotoPermissions`, `PhotoAccess` (Task 4); `DatabaseProvider` (Task 5).
- Produces:
  - `SelectedPhotoCounter(context)`: `suspend fun count(): Int` — number of rows in `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` (projection `_ID` only), on `Dispatchers.IO`.
  - `MainViewModel` (`@HiltViewModel`): `val state: StateFlow<UiState>` where `data class UiState(val access: PhotoAccess, val askedOnce: Boolean, val permanentlyDenied: Boolean, val selectedCount: Int?, val index: IndexStatus)` and `enum class IndexStatus { OPENING, READY, REBUILT, FAILED }`; `fun refreshAccess(isGranted: (String) -> Boolean)`; `fun onPermissionResult(isGranted: (String) -> Boolean, showRationale: Boolean)`. In `init`, opens the database on `Dispatchers.IO` and sets `index`.
  - `MainActivity`: `RequestMultiplePermissions` launcher with `PhotoPermissions.required(SDK_INT)`; calls `refreshAccess` in `onStart` (every app open, covers revocation); `permanentlyDenied` = denied after asking and `shouldShowRequestPermissionRationale` false → button opens app settings (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).

Copy (strings.xml, exact):
- Access title: "Find documents from your chats"
- Access body: "This app looks through images saved from WhatsApp, Telegram, Downloads and Screenshots so you can search the text inside them. It has no internet access: nothing leaves your phone."
- Button: "Allow photo access"; after denial: "Try again"; permanently denied: "Open settings"
- Partial banner: "Showing %1$d selected photos · Allow all" and second line "New images won't be added automatically until you allow all photos."
- Home placeholder: "Photo access: %1$s" and "Encrypted index: %1$s" (READY → "ready", REBUILT → "rebuilt", FAILED → "could not open", OPENING → "opening…"); "Indexing arrives in Phase 1."

- [ ] **Step 1: Implement** the files above; `./gradlew :app:installDebug`.
- [ ] **Step 2: Fresh install on the Nord 4 (Android 15)** — `adb shell pm clear com.narasa.imageorganiser`, launch, tap "Allow photo access" → system dialog offers "Allow all" and "Select photos". Choose Allow all → home shows "Photo access: FULL", "Encrypted index: ready".
- [ ] **Step 3: Denial path** — `pm clear`, launch, deny → "Try again"; deny again → "Open settings" opens the app's settings page.
- [ ] **Step 4: Partial path** — `pm clear`, choose "Select photos", pick 3 → banner "Showing 3 selected photos · Allow all". Tap "Allow all" → system dialog again.
- [ ] **Step 5: Review Focus 1 and 4** — with access granted, background the app, `adb shell pm revoke com.narasa.imageorganiser android.permission.READ_MEDIA_IMAGES` (and `READ_MEDIA_VISUAL_USER_SELECTED`), relaunch → access screen, no crash. Then select photos with zero chosen → banner shows 0 and "Allow all".
- [ ] **Step 6: Run all tests** `./gradlew :classifier:test :app:testDebugUnitTest :app:connectedDebugAndroidTest && ./gradlew -p build-logic test` — expect all PASS.
- [ ] **Step 7: Commit** `feat: photo access flow, revocation check and index status`.

### Task 7: Other Android versions (emulators)

Only `android-37` is installed. Android 11 (API 30), 13 (33) and 14 (34) need emulator images, roughly 1.5 GB each.

- [ ] **Step 1: Ask the owner before downloading.** Then `sdkmanager "system-images;android-30;google_apis;arm64-v8a"` (and 33, 34), create AVDs with `avdmanager`.
- [ ] **Step 2: On each emulator** repeat Task 6 Steps 2, 3 and 5 (Step 4 only on 34): API 30 dialog has no "Select photos" option, and revocation via `READ_EXTERNAL_STORAGE`; API 33 asks for photos only.
- [ ] **Step 3: Run** `./gradlew :app:connectedDebugAndroidTest` with each emulator attached — expect PASS.
- [ ] **Step 4: Record results** in the Phase 0 report (device, Android version, pass/fail per step).

### Phase 0 report

Stop after Task 7 and report: what was built, test results per device, what's not done, open questions. Do not start Phase 1.
