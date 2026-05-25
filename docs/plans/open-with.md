# pageboy — "Open with…" + filetype registration plan

## Status: ✅ DONE — Phase N shipped (OpenWithActivity, OpenWithResolver, AdHocDocumentStore, OpenWithEphemeralCleanupWorker, manifest intent filters all in place)

This plan covers what happens when the user taps a document file in any other Android app (file manager, email attachment, browser download, messaging app, cloud-storage app) and picks pageboy from the "Complete action using" chooser. The system intent flow is well-defined; the load-bearing work is the permission lifecycle, the ad-hoc `DocumentEntity` creation for "open-with" documents that aren't in any library root, and the recents-list integration.

## The Android "Open with…" flow (overview)

```
┌─────────────────────────┐                ┌──────────────────────┐
│ Another app             │  Intent.VIEW   │ Android system       │
│ (file manager, email,   │ ───────────>   │ (PackageManager      │
│  browser, messaging)    │   content://   │  resolves intent)    │
└─────────────────────────┘                └──────────────────────┘
                                                       │
                                                       │ matches every app whose
                                                       │ <intent-filter> covers
                                                       │ the MIME or extension
                                                       ▼
                                            ┌──────────────────────┐
                                            │ "Complete action     │
                                            │  using" chooser      │
                                            │  (the screenshot)    │
                                            └──────────────────────┘
                                                       │
                                                       │ user picks pageboy
                                                       ▼
                                            ┌──────────────────────┐
                                            │ Pageboy receives     │
                                            │ Intent.ACTION_VIEW   │
                                            │ + content:// URI     │
                                            │ + FLAG_GRANT_READ_   │
                                            │   URI_PERMISSION     │
                                            └──────────────────────┘
```

The URI permission is **ephemeral by default** — granted only for the lifetime of the activity instance. For "open this once" that's fine. For pageboy to remember the document in recents + re-open later, the permission lifecycle needs care (see N.3).

## Locked decisions

1. **A single launcher Activity remains the "main" entry; an additional `OpenWithActivity` handles `ACTION_VIEW` intents.** Two reasons:
   - Separating the two surfaces keeps `PageboyActivity`'s job (open library) distinct from `OpenWithActivity`'s job (resolve a foreign URI → reader). Single Responsibility per R.X.1.
   - `OpenWithActivity` can be marked `android:exported="true"` + restrict to `<intent-filter>` only, while `PageboyActivity` stays the cleaner launcher entry.
2. **`content://` only.** Pageboy does NOT handle `file://` URIs. Modern Android (≥ N / API 24) deprecated cross-process `file://` exposure, and any app passing a `file://` URI to pageboy in 2026+ is misbehaving. Document the rejection.
3. **Persistent permission is opt-in, per-document.** By default, "open with" gets the ephemeral read grant; pageboy reads the document into a cached extract for the reader session and forgets the URI. The user can tap "Keep this document" in the reader overflow → pageboy calls `takePersistableUriPermission()` → the document gets a permanent `DocumentEntity` row + appears in Recents next time the app opens. Without that tap, the document is one-shot.
4. **Ad-hoc documents do not pollute scanned-library state.** `DocumentEntity.source = AdHocOpen` (a sealed variant; library-root-scanned docs have `source = LibraryRoot(rootId)`). The Library tabs filter by source — Started / All show only LibraryRoot docs; Recents shows both; Pinned shows whatever the user pinned.
5. **Recents is the universal landing point.** Every "open with" feeds the Recents tab even when the user doesn't keep the document, so the next session shows the most-recently-touched docs at the top of Recents (with a "release URI permission expired" badge if the original grant has lapsed and the user didn't tap Keep).
6. **MIME-type discovery is best-effort.** Some senders (notably email clients, messaging apps, browsers) send documents as `application/octet-stream` because they don't trust the file's claimed MIME. Pageboy treats this as "extension/magic-byte sniff required" — opens the InputStream, reads the first 4 KiB, runs the same `DocumentClassifier` used by the library scanner. If classification fails, show a friendly "Pageboy doesn't recognize this file as a supported document" error.
7. **Package visibility (Android 11+ / API 30+).** Pageboy needs `<queries>` only if it itself launches other apps (e.g. "Open this in another reader" from the share-sheet). Phase N doesn't need this; defer to Phase O if/when share-from-pageboy is added.

## Manifest intent filters (Phase A.6 status check)

Phase A.6 shipped intent filters for all 8 formats per the format-research recommendations:

| Format | MIME types declared | Extensions (path-pattern fallback) |
| --- | --- | --- |
| Markdown | `text/markdown`, `text/x-markdown` | `.md`, `.markdown` |
| TXT | `text/plain` | — (covered by MIME) |
| PDF | `application/pdf` | — |
| EPUB | `application/epub+zip` | `.epub` |
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `.docx` |
| XLSX | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `.xlsx` |
| ODT | `application/vnd.oasis.opendocument.text` | `.odt` |
| ODS | `application/vnd.oasis.opendocument.spreadsheet` | `.ods` |

**Per-MIME quirks the implementation must handle:**

- **`text/markdown` is not universally registered.** Many file managers / share intents emit `.md` files as `text/plain`. Pageboy's `text/plain` filter is intentionally broad; the classifier (N.4) distinguishes plain text from markdown via extension after the intent lands.
- **`text/plain` overshare risk.** Pageboy *does* claim `text/plain` (per the TXT format research) — meaning it appears in every plain-text-file "Open with" chooser. This is desirable. The chooser shows pageboy alongside text editors; user picks per task.
- **`application/octet-stream`** is what email clients / browsers / messaging apps often emit. Pageboy must **also** declare a filter for `application/octet-stream` with extension constraints (per `<data android:pathPattern=".*\\.pdf"/>` etc.) so the chooser shows pageboy when the sender is unhelpful about MIME. **This is a Phase N addition — verify Phase A.6 didn't add it and add it now.**
- **EPUB MIME varies.** Some senders use `application/epub+zip` (correct); some use `application/zip` (lazy). Add `application/zip` + extension constraint `.epub` for that case.
- **OOXML wrapping.** Some Microsoft files come over with `application/zip` MIME + extension `.docx` / `.xlsx`. Same treatment.

**Manifest intent-filter pattern** (concrete):

```xml
<activity
    android:name=".openwith.OpenWithActivity"
    android:exported="true"
    android:label="@string/open_with_activity_label"
    android:theme="@style/Theme.Pageboy.OpenWith">

  <!-- MIME-typed filters (covers well-behaved senders) -->
  <intent-filter android:label="@string/open_with_pdf">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="content"/>
    <data android:mimeType="application/pdf"/>
  </intent-filter>

  <!-- repeat per format... -->

  <!-- Extension-fallback filter for misbehaving senders (application/octet-stream + path pattern) -->
  <intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="content"/>
    <data android:mimeType="application/octet-stream"/>
    <data android:pathPattern=".*\\.pdf"/>
    <data android:pathPattern=".*\\.epub"/>
    <!-- repeat per extension... -->
  </intent-filter>
</activity>
```

The `<data android:scheme="content"/>` (without `file`) intentionally rejects `file://` URIs per decision 2.

## Permissions

Pageboy requests **zero new runtime permissions** for this feature. The intent grants its own URI permission (`Intent.FLAG_GRANT_READ_URI_PERMISSION`). The user's act of picking pageboy in the chooser is the consent gesture.

If the user later taps "Keep this document" (decision 3), pageboy calls:

```kotlin
contentResolver.takePersistableUriPermission(
    uri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION
)
```

This requires the URI to have been issued via a SAF tree picker OR with the `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` set by the sender. Not every sender sets it. Pageboy must defensively try-catch the call; on `SecurityException`, show the user "This sender did not grant persistent access; pageboy can read the document for this session but cannot remember it. Save a copy to a folder pageboy scans to keep access." with a one-tap "Save to library root" action that copies the bytes into a SAF tree pageboy already has access to.

## Architecture (per refactor-solid.md)

Per R.X.1 (narrow data interfaces) and R.X.3 (composition root only wires concrete classes), the "open with" subsystem ships as narrow interfaces in `data/openwith/`:

```kotlin
interface OpenWithResolver {
    suspend fun resolve(intent: Intent): OpenWithResult
}

sealed class OpenWithResult {
    data class Ready(val documentId: Long, val ephemeral: Boolean) : OpenWithResult()
    data class UnknownFormat(val displayName: String?) : OpenWithResult()
    data class PermissionRefused(val reason: String) : OpenWithResult()
    data class Failure(val reason: String) : OpenWithResult()
}

interface AdHocDocumentStore {
    suspend fun createAdHoc(uri: Uri, classified: DocumentFormat, displayName: String): Long
    suspend fun keepAdHoc(documentId: Long): KeepResult  // tries takePersistableUriPermission
}

sealed class KeepResult {
    object Kept : KeepResult()
    data class CannotPersist(val reason: String) : KeepResult()
}
```

Composables / Activities take only these interfaces; concrete impls live in `AppGraph`.

`OpenWithActivity` is a thin orchestrator (~80 LOC target — well under R.X.4 thresholds): receives the intent, hands to `OpenWithResolver`, dispatches per `OpenWithResult` variant (Ready → launch reader + finish; UnknownFormat → toast + finish; PermissionRefused → toast + finish; Failure → toast + finish).

## Phase N — sub-steps

- [ ] **N.1** Verify the Phase A.6 manifest entries match the table above; add the `application/octet-stream` + extension-pathPattern catch-all filter; add the `application/zip` + extension-pathPattern filter for EPUB / OOXML lazy senders. Per-MIME `<intent-filter android:label>` localized strings added to `strings.xml`.
- [ ] **N.2** `OpenWithActivity` skeleton — separate Activity class, `exported=true`, only reachable via intent filter (no launcher entry). Defines its own theme (`Theme.Pageboy.OpenWith` with a translucent splash so the user doesn't see chrome flash before reader launch).
- [ ] **N.3** URI permission lifecycle:
  - On intent arrival: read the incoming `FLAG_GRANT_READ_URI_PERMISSION` (always present). InputStream readable for the lifetime of the activity.
  - For "Keep this document": call `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`. Catch `SecurityException`. On failure, surface the "save-to-library-root" UX.
- [ ] **N.4** `OpenWithResolver` impl:
  - Resolve display name via `contentResolver.query(uri, OpenableColumns)`.
  - Resolve declared MIME via `contentResolver.getType(uri)`.
  - If MIME is `application/octet-stream` / `application/zip` / `text/plain`: open the InputStream + delegate to `DocumentClassifier.classify(firstNBytes, displayName)`.
  - Insert `DocumentEntity(source = AdHocOpen(uri, ephemeral = true), format = classified, …)` via `AdHocDocumentStore`.
  - Return `OpenWithResult.Ready(documentId, ephemeral = true)`.
- [ ] **N.5** `DocumentEntity.source` sealed migration. Phase B's `DocumentEntity` has columns that imply library-root scanning; Phase N extends the entity with a discriminator + `AdHocOpen` URI. Room migration v1 → v2. (If Phase B foresaw this, the column may already exist; check.)
- [ ] **N.6** Reader integration. `OpenWithActivity` finishes by launching `PageboyActivity` with `ReaderRoute(documentId)` as the navigation destination + `FLAG_ACTIVITY_NEW_TASK`-cleared so back-from-reader returns to the system, not to pageboy's library. (Polish: "back-from-reader" in the open-with flow does NOT go to the library; goes to whoever launched the intent. Standard Android intent-chain behaviour.)
- [ ] **N.7** Recents-feed integration. Every `OpenWithResolver.resolve()` Ready result records an open via `ReadProgressStore.recordOpen(documentId)` — which Phase B's Recents tab already observes. Ad-hoc documents appear in Recents on next library launch.
- [ ] **N.8** "Keep this document" overflow. Reader top-bar overflow gets a new entry "Keep document" (only visible when `documentEntity.source is AdHocOpen` AND `ephemeral == true`). Tap → `AdHocDocumentStore.keepAdHoc(documentId)` → result UX (Kept toast / CannotPersist + save-to-library-root prompt).
- [ ] **N.9** "Save to library root" fallback. When `keepAdHoc` returns `CannotPersist`: prompt the user to pick a library root (existing roots presented + "Use another folder…" option). Copy the bytes via `contentResolver.openInputStream(uri).use { input -> targetUri.openOutputStream().use { output -> input.copyTo(output) } }`. Update the DocumentEntity's source to `LibraryRoot(rootId)`. Document now appears in All + Recents permanently.
- [ ] **N.10** Ephemeral cleanup. WorkManager job runs daily (`OpenWithEphemeralCleanupWorker`); removes ad-hoc `DocumentEntity` rows where `source is AdHocOpen && ephemeral && lastOpenedAt < (now - 7 days)`. The 7-day window gives users a chance to come back and Keep. Configurable per `OpenWithSettings.ephemeralRetentionDays` (defaults 7).
- [ ] **N.11** Per-MIME-association UX (system Settings). User can long-press a document file in their file manager and pick "Open by default" — Android handles this OS-side. Pageboy doesn't need to do anything special; the intent filter declaration is sufficient. Document this in CLAUDE.md so future agents don't try to "implement" what Android already does.
- [ ] **N.12** Settings: new "Open with" section in the catalog. Entries:
  - "Ephemeral retention days" (slider 1–30, default 7).
  - "Save ad-hoc opens to library by default" (boolean, default off — opt-in to skip the Keep prompt).
  - "Auto-classify unknown MIME types" (boolean, default on — disable to require explicit MIME match).
- [ ] **N.13** Tests:
  - `OpenWithResolverTest` — feeds mocked Intents with various MIME / display-name combinations; asserts the resolver classifies correctly + dispatches to the right `OpenWithResult` variant.
  - `AdHocDocumentStoreTest` — Room + in-memory; verifies `createAdHoc` + `keepAdHoc` (success + SecurityException paths).
  - `OpenWithActivityTest` — Robolectric Activity test; launches the activity with a synthetic Intent + asserts the right reader navigation lands.
  - `OpenWithEphemeralCleanupWorkerTest` — verifies the 7-day cleanup window.
- [ ] **N.14** AVD smoke. From a shell, dispatch a synthetic intent:
  ```bash
  adb shell am start -a android.intent.action.VIEW \
    -d "content://com.android.providers.downloads.documents/document/raw%3A%2Fsdcard%2FDownload%2Ftest.pdf" \
    -t "application/pdf" \
    -n com.eight87.pageboy/com.eight87.pageboy.openwith.OpenWithActivity \
    --grant-read-uri-permission
  ```
  Verify the reader launches with the document. Screencap at `/tmp/pageboy-N-openwith-smoke.png`.
- [ ] **N.15** Update `docs/plans/main.md` Phase N header from stub to "shipped: N.1–N.14 in commit `<hash>`". Update Status line at top.

## Risks + edge cases

- **App-link verification.** Pageboy does NOT need app-link verification (`android:autoVerify="true"`) because document MIMEs don't have associated http(s) URIs. App-link verification is for deep-link `https://` filters; not relevant here.
- **MIME ambiguity for shared text.** If someone shares a `.md` file from a messaging app, the MIME is often just `text/plain` and the display name has `.md` extension. The classifier handles this by sniffing the file extension regardless of the MIME claim.
- **Email attachment URIs are short-lived.** Many email apps emit `content://` URIs that are valid only for that one open — `takePersistableUriPermission` raises `SecurityException`. The "save to library root" fallback is the only way to retain access.
- **Files >> RAM.** A 500 MB PDF opened via "Open with" must not be slurped into memory. The reader pipeline reads from `contentResolver.openInputStream(uri)` lazily per page. Verify this works through the Phase F PDF renderer's streaming path.
- **`SecurityException` mid-read.** If the sender app's process dies between intent arrival and our `openInputStream`, the URI is revoked. Catch, display "The source app revoked access" + finish.
- **Multiple files in one intent.** `ACTION_SEND_MULTIPLE` exists for batch share; pageboy v1 does NOT handle it (each "Open with" is single-document). Document the limitation; revisit Phase N+ if user demand surfaces.

## SOLID compliance (per refactor-solid.md)

- **R.X.1** narrow data interfaces: `OpenWithResolver`, `AdHocDocumentStore` defined narrow; `OpenWithActivity` takes them via AppGraph.
- **R.X.2** sealed types: `OpenWithResult`, `KeepResult`, `DocumentEntity.Source` all sealed.
- **R.X.3** composition root: concrete impls (`AndroidOpenWithResolver`, `RoomAdHocDocumentStore`) wired in `AppGraph` only.
- **R.X.4** file size: `OpenWithActivity` ~80 LOC; `AndroidOpenWithResolver` ~150 LOC; `RoomAdHocDocumentStore` ~100 LOC. None past 250.
- **R.X.5** `NotImplementedError` — none expected.
- **R.X.6** wrong-direction imports — `data/openwith/` does not import `ui/`.
- **R.X.7** Compose ISP — `OpenWithActivity` is not a Composable; the UX surfaces (Keep prompt dialog, save-to-library prompt) take narrow params.
- **R.X.8** test discipline — N.13 enumerates the test classes.
- **R.X.9** `DocumentRenderer` — irrelevant here; the resolver hands off a `DocumentEntity` and the reader dispatches via the registry as usual.

## What this is NOT

- **Not a share-from-pageboy feature.** Pageboy v1 receives "Open with" but does not source share-sheet outbound — that's Phase O+ if added.
- **Not an "Open in browser" / "Open in another app" feature.** No `<queries>` element needed for Phase N.
- **Not a custom intent-chooser.** Pageboy uses the system chooser (the screenshot). No bespoke chooser UI.
- **Not a file-association preference UI.** Android handles per-MIME default-app preference at the OS level (long-press → "Open by default"). Pageboy doesn't replicate it.
- **Not URI write-access.** Pageboy reads `content://` URIs; if a future feature (PDF annotation save-back) needs write access, that's a separate plan + likely needs a different SAF flow.
