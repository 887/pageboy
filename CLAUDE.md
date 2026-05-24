# pageboy — Claude instructions

Modern Android e-reader and document viewer. Kotlin + Jetpack Compose + Material 3 Expressive + Room + DataStore + SAF. Reads Markdown, DOCX, XLSX, ODT, plain text, PDF (view + annotate + sign), and EPUB. Built entirely from the CLI, no Android Studio required. Textual sibling to [whisperboy](https://github.com/887/whisperboy) (audiobook player) — same toolchain and conventions, different data model and feature set. See [`docs/plans/sharing-analysis.md`](docs/plans/sharing-analysis.md) for the cross-app shared-code analysis (stub at seed time; revisit after Phase A).

## Architectural decisions (locked)

- **Language:** Kotlin only. No Java.
- **UI:** Jetpack Compose + **Material 3 Expressive**. No Android Views.
- **Data, library:** Room for cached document metadata (URI, format, title, last-read position, bookmarks, annotation index, signature placements). DataStore (Preferences) for user prefs (theme, font, default zoom, reading mode).
- **Storage, documents:** **SAF only** (Storage Access Framework, `DocumentFile` + persisted URI permissions). No `READ_EXTERNAL_STORAGE`. Document collections live wherever the user keeps them — `Documents/`, `Books/`, `Calibre Library/`, sync-folder roots, etc. We will wrap `DocumentFile` in a `CachedDocumentFile` to mitigate SAF's well-known performance pain (lifted as a pattern from whisperboy, not vendored).
- **Settings, preferences:** DataStore (Preferences) for app-wide defaults. Per-document state (last-read position, custom zoom, annotation visibility) lives on the `DocumentEntity` row, not in DataStore — same single-source-of-truth pattern whisperboy uses for per-book state.
- **Per-format renderer modules:** each format lives in its own package (`format/markdown/`, `format/pdf/`, `format/docx/`, `format/xlsx/`, `format/odt/`, `format/txt/`, `format/epub/`) behind a narrow `DocumentRenderer` interface. Library selection per format is the explicit subject of the next-round research phase (see [`docs/plans/format-research.md`](docs/plans/format-research.md)). One research agent per format; one `format-<name>.md` plan per format.
- **Build front-end:** [Google's Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps project creation, SDK management, build, install, and run. **Do not introduce Android Studio project files** (`.idea/`, `*.iml`).
- **Build back-end:** Gradle (driven by the Android CLI; the wrapper is committed to the repo).
- **Tests, unit:** Robolectric. JVM-only. No device required. The format parsers (PDF text extract, DOCX ZIP walk, EPUB spine walk, Markdown → AST) all run here.
- **Tests, UI:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp), Claude-driven over ADB. **Current target: headless AVD `medium_phone`** (Android 16 / API 36, RSS ~3.2 GB), started without window/audio/snapshot. Shared with tonearmboy / whisperboy / shutterboy. Phone via wifi-adb is the long-term home once signature-stylus latency and edge-to-edge insets in the reader chrome matter on real hardware.

### Why SAF (not MediaStore)

Documents live everywhere on a typical Android device — `Documents/`, `Download/`, `Books/`, `Calibre Library/`, `Obsidian Vault/`, app-private folders for synced Markdown notes, encrypted vaults mounted via the user's file manager. MediaStore indexes a handful of well-known categories (images, audio, video, downloads) and does not surface the user's manuscript collection reliably. SAF is the modern Android-correct path for opt-in folder libraries the user explicitly points the app at:

- The user wants pageboy to look in `Obsidian Vault/` *and* `Calibre Library/` *and* the SD card's `Books/`, but not at every `.md` file on the device. MediaStore's all-or-nothing visibility is the wrong shape.
- PDF + EPUB + DOCX collections are frequently on SD card / OTG drives that MediaStore does not always index reliably.
- We need stable per-document identifiers across re-scans (so annotations and last-read position survive). Hashing the SAF tree URI + relative path gives us that for free.

So: SAF picker, persisted URI permissions, multi-root, `CachedDocumentFile` wrapper for performance, document IDs as SHA-256 of `(treeUri + relativePath)`.

### Why per-format renderer modules

Each supported format is a different parsing world. PDF wants a native renderer (Pdfium / MuPDF) with page bitmap caching and an annotation overlay layer. DOCX is a ZIP of XML parts that we walk with `kotlinx.serialization-xml` or a SAX cursor. EPUB is a ZIP of XHTML with a spine manifest. Markdown is a streaming parser to AST plus a Compose renderer. Plain text is a font-rendered text flow with reflow on font-size change. They share *zero* parser code.

Pageboy is structured to make that explicit:

- One `DocumentRenderer` interface in `format/` with three methods (open, render-page, close) and an `enum class DocumentFormat`. UI consumes only the interface.
- One package per format. One research agent per format in the next round. One `docs/plans/format-<name>.md` per format containing the library decision + APK-size budget + version pinning + edge-case notes.
- A `FormatDetector` (extension → magic-byte sniff) routes an opened `DocumentFile` to the right renderer.

This shape exists so the next-round research can be fanned out without merge conflicts: each agent owns one folder and one plan file.

## Required CLIs and MCP servers

These are user-machine prerequisites. The plan tracks each in Phase 0.

### Android CLI

The new (April 2026) `android` command from Google wraps everything we need.

Install (userspace, this user's setup):

```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android
chmod +x ~/.local/bin/android
android --version  # self-bootstraps the runtime on first call
```

The CLI bundles its own JDK 21 at `~/.android/cli/bundles/<hash>/jre/`. **Caveat:** the bundled JRE is *minimized* — it's missing modules including `java.rmi`, which Gradle 9.1's Kotlin DSL classpath fingerprinter loads. Direct `./gradlew` invocations against the bundled JRE will fail at configuration time with `java.lang.NoClassDefFoundError: java/rmi/Remote`. For direct Gradle calls, export `JAVA_HOME` to a full system JDK 17+ instead — for example `/usr/lib/jvm/java-26-openjdk` on this user's machine. Going through `android run` / other `android` subcommands is fine and uses the bundled toolchain internally.

Practical rule of thumb:
- `android run --apks=…` → just works.
- `./gradlew assembleDebug` → `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug` (or equivalent system JDK 17+ path).

**Worktree caveat:** Gradle reads the SDK path from `local.properties`, which is gitignored. Worktrees created off `main` for subagents start without a `local.properties`, so direct `./gradlew` calls there will fail with `SDK location not found` unless `ANDROID_HOME` is exported (or `local.properties` is generated locally in the worktree). Always export `ANDROID_HOME=$HOME/Android/Sdk` alongside `JAVA_HOME` when invoking Gradle directly in an agent worktree.

Useful subcommands:

```bash
android create list                                  # browse project templates
android create --name=pageboy --output=. <template>  # scaffold a new project
android sdk install platforms/android-34 build-tools/34.0.0
android run --apks=app/build/outputs/apk/debug/pageboy-debug.apk
android docs search <query>                          # query the Android Knowledge Base
android docs fetch <kb-url>                          # fetch a specific KB doc
android skills list --long                           # browse official Android skills
android info                                         # show detected SDK + version
```

`android docs search` is **the first place to look** when uncertain about Android APIs. It returns up-to-date guidance from the official Android Knowledge Base — beats grepping web search results, and is on-machine.

### `mobile` MCP server (UI driving)

Registered at **project scope** in `.mcp.json` (committed to the repo) and allowed in `.claude/settings.json` (also committed). When a Claude Code session starts in this repo with `enableAllProjectMcpServers: true` (set in the project settings), the `mcp__mobile__*` tools become available automatically.

To re-register on a fresh checkout if for any reason the project config drops the entry:

```bash
claude mcp add mobile --scope project -- npx -y @mobilenext/mobile-mcp@latest
```

What it gives you: list connected ADB targets, install APKs, launch the app, read the accessibility tree (the screen state, the way Playwright reads the DOM), tap by label / coordinates, assert UI state.

### `android-skills` MCP server (official Android skills)

Registered at **project scope** in `.mcp.json` and allowed in `.claude/settings.json`. Surfaces Google's official Android Skills (Compose migration, Navigation 3, Edge-to-Edge, AGP 9, R8 config, etc.) as MCP tools inside Claude Code. **Consult these before hand-rolling any Android-specific pattern** that could be load-bearing on platform conventions — especially edge-to-edge insets in the reader chrome (the surface where the system gesture nav, the document scroll, and the annotation toolbar all fight for the bottom inset).

To re-register on a fresh checkout:

```bash
claude mcp add android-skills --scope project -- npx -y android-skills-mcp
```

### Test target

One of:

- **wifi-adb to the user's phone** (preferred long-term — zero machine RAM cost, real stylus / signature latency, real edge-to-edge inset behaviour against actual gesture navigation):
  ```bash
  adb pair <ip>:<pair-port>      # pair once
  adb connect <ip>:<connect-port>
  adb devices                    # confirm
  ```
- **Headless AVD `medium_phone`** (Android 16 / API 36 — see Phase 0):
  ```bash
  ~/Android/Sdk/emulator/emulator -avd medium_phone \
    -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect &
  ```
- **Waydroid** declined (LXC, ~1-2 GB resident, but needs root for SAF tree access).

For pageboy specifically, the AVD is fine for the reader-chrome / SAF-picker / format-render / annotation overlay layers, but **freehand signature stamp behaviour should ultimately be verified on a real phone with a stylus / finger** — input event timing on the AVD's emulated touch pipeline is reliable but doesn't reflect what stylus pressure / palm rejection / Bluetooth-LE pen lookup actually feel like.

## Test loop

```bash
./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/pageboy-debug.apk
# mobile-mcp tools take over for UI interaction
```

### UI changes are verified on the running AVD

Any change that touches Compose UI (layout, composable structure, navigation, theming, reader chrome, annotation toolbar) MUST be verified by installing the rebuilt debug APK on the running headless AVD (`emulator-5554`) and inspecting the result — Robolectric unit tests do not catch real-device layout bugs (overflow, clipping, off-screen widgets, edge-to-edge inset breakage, signature-pad coordinate drift, etc.).

Canonical loop:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/pageboy-debug.apk
adb -s emulator-5554 shell am start -n com.eight87.pageboy/.PageboyActivity
adb -s emulator-5554 exec-out screencap -p | magick - -resize 50% /tmp/pageboy.png   # then Read the PNG
```

The AVD is 1080x2400 native, which is too big to read comfortably — pipe screencaps through `magick - -resize 50%` to land at 540x1200 (quarter the pixels, easier to inspect, tap coords are still computed against the device's native 1080x2400, just multiply scaled image coords by 2). Skip the resize only when you genuinely need pixel-accurate detail.

Also: clean up `/tmp/*.png` periodically — these accumulate fast across sessions and a few hundred stale screenshots makes file listings noisy.

Prefer `mobile-mcp` tools when they're loaded in the session (they give the accessibility tree + tap-by-label, much more precise than coordinate input). When mobile-mcp isn't available, fall back to `adb exec-out screencap -p` + visual inspection of the PNG via the Read tool — it's lower-resolution evidence than the a11y tree but enough to confirm widget presence, position, and overflow behaviour.

Do not report a UI task as done on the strength of unit tests + a successful build alone.

For raw ADB inspection during dev:

```bash
adb logcat -s pageboy:* pageboy.render:* pageboy.format:* pageboy.sign:*
adb shell am start -n com.eight87.pageboy/.PageboyActivity
```

### SAF-specific test-loop notes

The SAF picker presents a system UI surface that the AVD reproduces faithfully but slowly. Two practical tips:

1. The folder a Phase D smoke test wants is `/sdcard/Documents/pageboy-test/`. Push fixtures via `scripts/push-test-documents.sh` (created in Phase D), then walk the picker UI via mobile-mcp.
2. Persisted URI permissions survive app reinstall *only if* the `--user 0` is preserved on `adb install -r`. If a smoke test reports "tree URI permission lost", check that.

## File conventions

- Single-module to start. Split into `:core` / `:data` / `:ui` / `:format-*` only when the single-module size warrants it; do not premature-modularize. (Same prior whisperboy validated.) The per-format *packages* exist from day one even though they all live inside `:app` — they get promoted to Gradle modules only if a format renderer pulls a heavy native lib that R8 can't tree-shake out of unrelated debug builds.
- Package root: `com.eight87.pageboy`.
- Composable functions: PascalCase, no `@Composable` on private helpers unless they take a Modifier.
- ViewModels: one per screen, talk to the data layer via repository interfaces.
- No DI framework in v1 (Hilt/Koin/Metro) — pass dependencies as constructor params via a hand-rolled `AppGraph` composition root, the same pattern tonearmboy / whisperboy use. Add DI later if/when the manual wiring hurts.
- No reflection-based JSON. Use `kotlinx.serialization` if any serialization is needed (annotation overlays, signature placements).

## Design principles — SOLID, applied to Kotlin + Compose

The codebase follows SOLID where it earns its keep. Kotlin + Compose change *how* the principles cash out (top-level functions instead of `interface ServiceImpl`, sealed types instead of Visitor, `Flow<T>` instead of Observer wiring), but the underlying tests still apply. **When introducing a new file or refactoring an existing one, sanity-check it against these five questions.** When in doubt, prefer the principle over the shortcut.

- **S — Single Responsibility.** A type / file / composable should have *one reason to change*. The per-format renderer split is the load-bearing SRP boundary in pageboy — keep parsing, rendering, and persistence separate within each `format/<name>/` package.
- **O — Open/Closed.** New supported formats land as new `DocumentRenderer` impls + a new `DocumentFormat` enum case; the reader screen's dispatch is an exhaustive `when` that grows by one line. No `if (extension == "pdf") { ... }` chains anywhere outside `FormatDetector`.
- **L — Liskov Substitution.** Every `DocumentRenderer` impl honours the contract totally — including the "doc cannot be rendered" case, which is a `RenderResult.Unsupported(reason)` return, not an exception.
- **I — Interface Segregation.** UI takes narrow data interfaces (`DocumentSource`, `BookmarkSource`, `AnnotationSource`), not the whole `LibraryRepository`. The signature pad takes a `SignaturePlacementSink` (one method), not the whole annotation repo.
- **D — Dependency Inversion.** The `PageboyActivity` and the `AppGraph` are the only files that know concrete types. Renderers are constructed inside the graph; ViewModels see interfaces.

## Plan files

- [`docs/plans/main.md`](docs/plans/main.md) — phased build plan, per the user's global CLAUDE.md rule (numbered phases, sub-step checkboxes). Phase 0 + A are sketched in detail; phases B+ are stub headers awaiting the next-round research.
- [`docs/plans/seed-prompt.md`](docs/plans/seed-prompt.md) — the user's original brief, verbatim, with a family-context intro for fresh agents.
- [`docs/plans/ui-shell.md`](docs/plans/ui-shell.md) — prescription to copy tonearmboy's vertical-rail + top-bar + settings catalog DSL + AboutScreen + LicensesScreen patterns. **Do not redesign these surfaces** — adapt content only.
- [`docs/plans/m3-expressive.md`](docs/plans/m3-expressive.md) — Material 3 Expressive starter, inheriting the five gotchas tonearmboy paid for so pageboy starts with the bugs already fixed.
- [`docs/plans/oss-licenses.md`](docs/plans/oss-licenses.md) — open-source-licenses sub-page plan. Same Licensee plugin + Compose-rendered sub-screen + Robolectric catalog test pattern tonearmboy and whisperboy use.
- [`docs/plans/format-research.md`](docs/plans/format-research.md) — the seed prompt for next-round per-format research agents. One `docs/plans/format-<name>.md` per supported format, produced by a dedicated research agent, before the format's renderer is implemented.
- [`docs/plans/sharing-analysis.md`](docs/plans/sharing-analysis.md) — stub for the cross-app shared-code analysis. Revisit after Phase A scaffold.

When working on a phase:

- Tick its sub-steps (`- [x]`) in the same commit that lands the work.
- Add `shipped in change <jj-id>` (or `shipped in commit <git-sha>` if this repo stays plain git) to the phase header when *all* its sub-steps are ticked.
- Mark the whole plan `## Status: ✅ DONE` once every phase is ticked.
- If a phase header has no sub-step checkboxes, *write them first*. No vibes-based progress.

## Editorial — user-facing copy

The user follows Paul Graham's *Keep Your Identity Small*. App copy (settings descriptions, error messages, About text, onboarding) should be plain, factual, useful. No "vibes" copy, no personal opinions, no humor that pins identity. This applies double to onboarding and to the signature flow — those are the surfaces the user shows other people.

## Open with… (Phase N) — Android handles per-MIME defaults OS-side

When users want pageboy to be the default app for a MIME type, **Android
itself handles it** via the long-press → "Open with…" → "Always" /
"Open by default" flow at the OS level (System Settings → Apps →
Default apps → Opening links / Open by default per app). Pageboy
declares its intent filters on `.openwith.OpenWithActivity`
(`content://` only — locked decision #2 in
[`docs/plans/open-with.md`](docs/plans/open-with.md)) and the OS does
the rest.

**Do not** implement an in-app per-MIME default-app picker. Future
agents who notice that pageboy doesn't have one should leave it that
way: the OS surface is universal and the per-app duplicate is
redundant. The only in-app surface is the **Open with** settings
section (retention slider, save-to-library default, auto-classify
toggle) — those are pageboy-internal behaviour, not Android default-app
behaviour.

## Subagent dispatching

Subagents working on this repo run in worktrees. Each agent prompt must:

- name the phase + sub-steps it owns
- be told to tick checkboxes and add the change/commit ID to the phase header as it lands work
- be told to keep the work scoped to its phase (no opportunistic refactors of unrelated code, no touching the other formats' packages)
- be told to never modify `~/.claude/` files (those are not under this repo)
- be told to consult `android docs search <query>` before hitting general web search for Android API questions
- be told to consult the `android-skills` MCP for any pattern Google has codified (Compose migration, Navigation 3, edge-to-edge insets, R8 config, etc.)
- be told that **the UI shell is locked** — see [`docs/plans/ui-shell.md`](docs/plans/ui-shell.md). Vertical rail + top bar + settings catalog DSL + About + Licenses screens come from tonearmboy by direct adaptation. Research agents do not redesign these surfaces.
- be told that **the M3 Expressive gotchas are inherited** — see [`docs/plans/m3-expressive.md`](docs/plans/m3-expressive.md). Apply the five fixes up front; do not relearn them.
