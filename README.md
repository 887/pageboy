# pageboy

Modern Android e-reader and document viewer. Built on Jetpack Compose + Material 3 Expressive. Reads Markdown, DOCX, XLSX, ODT, plain text, PDF (view + annotate + sign), and EPUB. Textual sibling to [whisperboy](https://github.com/887/whisperboy) — same intellectual content (books, documents), different sensory modality. Same toolchain and conventions as [tonearmboy](https://github.com/887/tonearmboy) (music), whisperboy (audiobooks), and [shutterboy](https://github.com/887/shutterboy) (photos).

## Status

Pre-Phase 0. Repo skeleton + plans only. The next round of opus research agents will land Phase 0 (toolchain verification, mostly no-ops on this user's machine), Phase A (scaffold), and per-format research plans. See [`docs/plans/main.md`](docs/plans/main.md) for the phased build plan, [`docs/plans/format-research.md`](docs/plans/format-research.md) for the per-format research prompts, and [`docs/plans/seed-prompt.md`](docs/plans/seed-prompt.md) for the original brief.

## Goals

- **One app for every "page".** Markdown (.md), DOCX, XLSX, ODT (and likely ODS), TXT, PDF, EPUB. Open from a SAF folder library, from the system share sheet, or from a `content://` deep-link.
- **PDF is first-class.** View, annotate, and sign — both digital signatures (cryptographic) and freehand signature stamps. The signing surface is part of v1, not a follow-on.
- **Per-format renderer modules.** Each format lives behind a narrow `DocumentRenderer` interface so a researcher / sub-agent can own one format end-to-end without touching the others. See [`docs/plans/format-research.md`](docs/plans/format-research.md).
- **Modern stack:** Kotlin + Jetpack Compose + Material 3 Expressive + Room + DataStore + SAF. No legacy Android Views, no Java.
- **Built entirely from the CLI**, no Android Studio required. Same `android` CLI front-end the rest of the family uses.

## Non-goals (v1)

- Document editing beyond annotation / signing. Pageboy reads and signs; it doesn't author. (For writing prose: any editor that produces Markdown / DOCX, then opens in pageboy to read.)
- Cloud sync. The library is a set of SAF folder roots; sync happens at the filesystem layer (Syncthing, rclone, etc.).
- OCR. Out of scope for v1; revisit if a clear use-case emerges.
- Translation. Out of scope.
- Form-fill on PDFs beyond the bits that fall out of annotation + signing for free.
- Cast / Wear OS / tablet-specific layouts (works on phone, scales later).

## Why "pageboy"?

Three loadings, in the same triple-loaded register as the sibling app `strictlykeptboy`:

1. **Medium.** Every supported format is *pages*. Markdown, DOCX, XLSX, ODT, TXT, PDF, EPUB — the page is the unit. The "boy" suffix family marks the app's modality by an object from that medium (shutter for cameras, tonearm for music, whisper for voice, page for documents).
2. **Kept-boy.** Historically a pageboy is a young male servant attached to a household — kept, neat, on hand. Direct peer of the naming logic in `strictlykeptboy`.
3. **Bonus.** A pageboy is also a haircut — a cute-boy aesthetic. Named once and left at that.

## Why a sibling app, not a combo with whisperboy?

Same intellectual content (a book, a document, a manuscript), different sensory modality (read vs listen) — but the data model, the interactions, and the feature surfaces diverge enough that one app would compromise both.

- **Whisperboy** tracks: per-book position in milliseconds, chapter markers, sleep timer, variable speed, skip silence, embedded MP4 chapter atoms, `MediaLibraryService` for Android Auto. Listen-oriented. Background-service-shaped.
- **Pageboy** tracks: per-document scroll / page position, text reflow at the user's font size, syntax-highlighted code blocks, PDF annotation overlays, signature placements, ZIP-extracted DOCX / XLSX / ODT internals, EPUB CSS sanitization. Read-oriented. Foreground-screen-shaped.

The stack is shared (Compose + Room + SAF + the CLI build pipeline), the codebase is not. See [`docs/plans/sharing-analysis.md`](docs/plans/sharing-analysis.md) for the cross-app shared-code analysis (stub at seed time; revisit after pageboy ships Phase A scaffold).

## Install on Android via Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) is an open-source Android
app store that pulls APKs directly from GitHub Releases. No Play Store, no
sideload dance, auto-update on every new release. Works on de-Googled Androids
(GrapheneOS / CalyxOS / LineageOS).

> **Note:** pageboy has not shipped a release yet. The instructions below
> describe the intended install path once the first release lands.

### One-tap install (if Obtainium is already on your phone)

Tap this link on your phone:

[`obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Fpageboy`](obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Fpageboy)

Obtainium opens, prefills the source, and shows **Add**.

### Manual install

1. Install Obtainium from [F-Droid](https://f-droid.org/en/packages/dev.imranr.obtainium.fdroid/) or [its GitHub releases](https://github.com/ImranR98/Obtainium/releases/latest).

2. In Obtainium, tap **Add App** → paste this **Source URL**:

   ```
   https://github.com/887/pageboy
   ```

3. The other fields auto-detect, but if you need to set them by hand:

   | Field            | Value                |
   | ---------------- | -------------------- |
   | Source type      | GitHub               |
   | APK filter regex | `^pageboy-.*\.apk$`  |
   | Update channel   | Releases             |

4. Tap **Add**. Obtainium fetches `pageboy-<version>-<sha7>.apk` from the latest release and offers Install. Future releases trigger an auto-update notification.

### Verifying a build

Each release ships a "Verify build" table in its notes with the APK SHA-256.
After installing, confirm what you got matches:

```bash
adb shell pm path com.eight87.pageboy           # find the installed APK on your device
adb pull <path-from-above> /tmp/installed.apk   # pull it back
sha256sum /tmp/installed.apk                    # compare to the release notes
```

---

The rest of this README is for **developers** — building locally, running the
AVD, running smoke tests, shipping a release. Skip if you just want the app.

## Prerequisites (one-time, Linux)

```bash
# Android CLI 0.7+
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android \
  -o ~/.local/bin/android && chmod +x ~/.local/bin/android
android --version          # self-bootstraps the runtime + bundled JDK 21

# SDK packages
android sdk install platforms/android-34 build-tools/34.0.0

# Test target — headless AVD (shared with the rest of the family)
android emulator create --profile=medium_phone

# Mirror utility (optional but recommended for visual QA)
sudo pacman -S scrcpy      # Arch / Manjaro
# (or your distro's equivalent — Debian/Ubuntu: `sudo apt install scrcpy`)
```

The Android CLI also fetches the emulator binary and a system image on first
`android emulator create`. Expect ~600 MB of downloads on a fresh box. The
`medium_phone` AVD is shared across tonearmboy / whisperboy / shutterboy /
pageboy — if it already exists from another sibling app, skip the create step.

## Run the AVD + scrcpy

```bash
scripts/start-avd.sh             # boot AVD if not running, then attach scrcpy
scripts/start-avd.sh --no-mirror # AVD only, no scrcpy window
scripts/start-avd.sh --kill      # stop both
```

The AVD boots headless (`-no-window -no-audio -no-snapshot`) for ~3 GB resident
RAM. `scrcpy` then mirrors the display to a host window (Wayland / X11) without
restarting the emulator. Once running, `adb devices` shows `emulator-5554`.

## Build + install

```bash
# Direct gradlew calls need both env vars (see CLAUDE.md for the why):
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/pageboy-debug.apk --device=emulator-5554
```

Or via the Android CLI directly (which handles the toolchain internally):

```bash
android run --apks=app/build/outputs/apk/debug/pageboy-debug.apk
```

## Build a release APK

The canonical happy path is **"phone-vibing"**: you're on your phone, you tell
Claude (in the Claude app) to ship a new build of pageboy. Claude opens a
session against this repo on your dev machine, runs:

```bash
scripts/build-release-apk.sh --gh-release
```

…and the new APK shows up on `https://github.com/887/pageboy/releases/latest`.
You then pull it to your phone via [Obtainium](#install-on-android-via-obtainium), which
auto-detects the new release and offers an in-place update. No Play Store, no
Android Studio, no manual `adb`.

The script (which lands in a later phase, modelled on whisperboy's) will
support three flags, individually or combined:

```bash
# 1. Build only — APK lands at release/pageboy-<version>-<sha7>.apk
scripts/build-release-apk.sh

# 2. Build + upload to GitHub Releases (uses gh CLI; creates a vN.N.N-<sha7> tag)
scripts/build-release-apk.sh --gh-release

# 3. Build + adb install onto the connected device (AVD or wifi-adb phone)
scripts/build-release-apk.sh --install

# Combine flags — the full local one-shot:
scripts/build-release-apk.sh --gh-release --install
```

## Test

See [`CLAUDE.md`](CLAUDE.md) for the full Claude-driven test loop.

- **Unit / data layer** — Robolectric, JVM-only, zero device. The format parsers
  (PDF text-extract, DOCX ZIP-walk, EPUB spine walk, Markdown → AST) all run
  here without a device.
- **UI / integration** — [`mobile-mcp`](https://github.com/mobile-next/mobile-mcp) over ADB driving the headless AVD (or a real phone via wifi-adb).

## Acknowledgements

Pageboy's design space is mapped by a few exemplary open-source Android
readers that the next-round research agents should study before inventing:
**Markor** (Markdown / text, GPLv3), **Librera** (EPUB + PDF, GPLv3), **MuPDF**
viewer (PDF, AGPL — code license, not the library — see format-research),
**Collabora Office** for the OOXML / ODF rendering surface (MPL-2.0). We don't
fork or vendor any of those — pageboy is written from scratch, MIT-licensed —
but the design space they map is the design space pageboy starts in. Library
selection (which OOXML parser, which PDF renderer, which Markdown stack) is
the explicit subject of [`docs/plans/format-research.md`](docs/plans/format-research.md).

## Translations

Translations are produced by the user + Claude per-language; English (`app/src/main/res/values/strings.xml`) is canonical and missing keys fall back to English at runtime. The table below is regenerated by `scripts/translation-progress.sh` once the first locale lands.

<!-- TRANSLATIONS-START -->

_No translations yet — English only._

<!-- TRANSLATIONS-END -->

## License

MIT. See [`LICENSE`](LICENSE).
