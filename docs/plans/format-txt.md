# pageboy — plain-text (.txt) renderer plan

## Status: ✅ DONE — Phase E shipped (hand-rolled renderer in format/txt/)

## Recommendation

**No third-party library.** Use the JDK + Kotlin stdlib + Compose `Text` inside a `LazyColumn`, with a hand-rolled BOM-sniff + UTF-8-first encoding fallback. Estimated implementation: ~250 LOC across renderer + encoding sniffer + line-windowing source.

This is the trivial-degenerate case of the Markdown renderer: no formatting, no inline runs, just raw lines in a monospace font with reflow. The interesting work is all in (1) encoding detection without pulling juniversalchardet, (2) line-ending normalization, and (3) not OOM'ing on a 50 MB log file.

## Alternatives considered

### `juniversalchardet` (Mozilla universal-charset-detector port) — **rejected (license blocker)**

- The active fork is `com.github.albfernandez:juniversalchardet`. Per [its README](https://github.com/albfernandez/juniversalchardet), the license is "MPL-1.1 OR GPL-2.0-or-later OR LGPL-2.1-or-later" (verified 2026-05).
- **MPL-1.1 is not on the allowlist** (only MPL-2.0 is). GPL/LGPL are disqualifying.
- Even if we could use it, a 250 KB library for what we can solve with a 60-LOC BOM-sniff + chardet-lite heuristic on a viewer-only path is poor budget hygiene.

### `icu4j` / `ICU4J CharsetDetector` — **rejected**

- License is fine (ICU is permissive, Unicode-DFS-style — but it's a 12 MB jar even after AGP shrinking. Massively overshoots the TXT < 50 KB budget. Use cases for ICU on Android exist (BiDi text, complex script shaping), but TXT viewing is not one of them — Compose's text engine already shapes scripts.

### Hand-roll (recommended) — **accepted**

- BOM sniff covers UTF-8 BOM (`EF BB BF`), UTF-16-LE BOM (`FF FE`), UTF-16-BE BOM (`FE FF`), UTF-32 BOMs (`FF FE 00 00` / `00 00 FE FF`). Detects ~30% of real-world non-UTF-8 files for free.
- Without a BOM: try `UTF_8` with `CodingErrorAction.REPORT` on a 64 KB head sample. If it decodes clean, use UTF-8. If it fails, fall back to **Windows-1252** (which is a superset of ISO-8859-1 for the 0x80–0x9F range and is what 95% of legacy ASCII-ish English/European text actually is). Annotate the document with the detected encoding so the UI can show a "detected: cp1252 — switch to…" affordance.
- This is the same strategy ripgrep uses by default and is documented to work for the realistic plain-text-viewer use case.

## License gate summary

| Candidate | SPDX | On allowlist? | Decision |
|---|---|---|---|
| (none — JDK + stdlib + Compose) | — | n/a | ✅ chosen |
| albfernandez/juniversalchardet | MPL-1.1 OR GPL-2.0-or-later OR LGPL-2.1-or-later | ❌ | rejected |
| ICU4J CharsetDetector | Unicode-DFS-style (permissive) | ✅ | rejected on size (~12 MB) |

## APK-size budget

Target: **< 50 KB** TXT APK delta (per `format-research.md`'s "small" guidance). Hand-roll lands at ~25–35 KB of DEX (renderer + encoding sniffer + line source). Comfortably inside budget.

Per-ABI: pure JVM. No native code.

## JVM-vs-native

Pure JVM. No `.so`.

## Performance characteristics

The interesting performance question is **the 50 MB log-file case** flagged in the seed prompt — we must not OOM on the user opening `logcat.txt`.

Strategy:

1. **Encoding sniff on a 64 KB head buffer** — small, fast, one read.
2. **`LineSource` interface** with two implementations:
   - `InMemoryLineSource(lines: List<String>)` — for files < 1 MB, eager-read everything.
   - `WindowedLineSource(uri: Uri, lineIndex: LongArray)` — for files ≥ 1 MB. Builds a line-offset index on first open (one streaming pass via `BufferedReader.lines()` capturing byte offsets), then services `getLine(i)` by `RandomAccessFile.seek(lineIndex[i])` + `readLine()`. The index is cached in `DocumentEntity.lineIndexBlob`.
3. **`LazyColumn(itemsCount = lineSource.size)`** with `key = { it }` — only the visible window is materialized into Compose nodes. A 50 MB file with ~500k lines renders the visible 30 lines at ~60 fps; index-build is the one-time cost (~600 ms for 50 MB on a mid-range device, reading sequentially).
4. **Re-flow on font-size change** — re-flow is implicit in `LazyColumn` because each line is its own composable; changing the typography token re-measures lazily as the user scrolls. No global reflow pass needed.

Expected timings (rough):
- 10 KB plain ASCII → first paint < 20 ms.
- 1 MB UTF-8 text → first paint < 80 ms (eager path).
- 50 MB log file → index build 500–800 ms on first open, cached in Room. Subsequent opens skip the index pass. First paint < 100 ms post-index.

## Android-version compatibility

JDK 8 byte-code only. No `java.nio.file.Files` (which is API 26+ but we're on 28 — would be fine), no platform-version-specific text APIs. `minSdk` 28 is comfortably supported.

`StandardCharsets.UTF_8` and `Charset.forName("windows-1252")` are both available since API 1 and 9 respectively. No compatibility shims.

## Maintenance status

No upstream — this is our own code. The relevant maintenance burden is keeping the Windows-1252 fallback honest (no realistic spec drift here — that codepage is frozen).

## Spec gotchas

### G1. Encoding detection failure modes

The three places encoding detection bites in practice:

- **BOM-less UTF-16** — vanishingly rare in 2026 but does exist in legacy Windows Notepad output. Heuristic: if every other byte in the head sample is `0x00`, it's UTF-16-LE; flag and decode accordingly. Costs 5 LOC.
- **Mixed-encoding files** — concatenated log files where someone catted a cp1252 file onto a UTF-8 one. We detect on the head; if mid-file we hit a decode error, switch the offending line to `windows-1252` and keep going. Never throw to the user.
- **GB2312 / Shift-JIS / EUC-KR** — CJK encodings. Out of scope for v1 (our user is not the target). If detection sees lots of >0x7F bytes that don't form valid UTF-8 *or* valid cp1252 (cp1252 always succeeds), surface a "detected: unknown encoding — try UTF-8 / cp1252 / Shift-JIS" affordance and let the user override. The override stores the chosen charset on the `DocumentEntity`.

### G2. Line-ending normalization

Real text files in the wild have:

- **LF** (`\n`) — Unix, modern macOS, most code, Markdown from Obsidian.
- **CRLF** (`\r\n`) — Windows-native, most `.txt` files from Notepad.
- **CR** (`\r`) — classic Mac OS (pre-OS-X). Extinct except in artifact files from the 90s.
- **Mixed** — concatenated logs, files round-tripped through bad tooling. Most painful case.

Strategy: line splitter uses `Regex("\r\n|\r|\n")` — the order matters (CRLF first so it's never decomposed into two lines). Trailing-empty-string after a final newline is dropped. No internal CR/LF representation in the windowed index — store byte offsets of *line starts*, not line contents.

### G3. Very-long single lines (the 50 MB minified-JSON case)

A 50 MB file where the entire content is one line (minified JSON, no-newlines-log-dump) breaks the `WindowedLineSource` design — `lineIndex` has one entry, `getLine(0)` returns 50 MB into a single `String`, OOM.

Mitigation: when index-building, if any single line exceeds **64 KB**, set a flag on `WindowedLineSource.isWrapByCharLimit = true` and start returning *fixed-width* virtual lines (default 256 chars or one display-width worth). The UI gets `lineSource.size = ceil(byteLength / 256)` and reads 256-char windows. Trade-off: line numbers stop matching the file's natural line numbering — but for a one-line file there is no natural numbering anyway. Show a small "wrapped" badge in the reader chrome when this mode is active.

## Phase E — implementation

Sub-step checkboxes ready for incorporation into `main.md` Phase E.

- [ ] **E.1** Stand up `format/txt/TxtRenderer.kt` implementing `DocumentRenderer`. `open()` builds the `LineSource` (eager for small files, windowed for ≥ 1 MB), `close()` releases the `RandomAccessFile`.
- [ ] **E.2** `format/txt/encoding/EncodingSniffer.kt` — BOM detection for UTF-8/16/32, then UTF-8 trial-decode on 64 KB head, then Windows-1252 fallback. Returns `DetectedEncoding(charset, confidence, source)` where source ∈ `{Bom, Utf8Trial, Cp1252Fallback, UserOverride}`.
- [ ] **E.3** `format/txt/source/LineSource.kt` interface + `InMemoryLineSource` + `WindowedLineSource` implementations. Threshold constant `EAGER_THRESHOLD_BYTES = 1_048_576`.
- [ ] **E.4** Line-index builder: one streaming pass via `BufferedInputStream` + manual byte-offset tracking (we cannot use `BufferedReader` for offsets because it buffers ahead and loses byte positions for variable-width encodings). Detects long-line wrap mode (G3) when any single line exceeds 64 KB.
- [ ] **E.5** Persist the line index in `DocumentEntity.lineIndexBlob` (Room `BLOB`). On re-open, hydrate without re-scanning. Invalidate if file mtime or size changes.
- [ ] **E.6** Compose reader `format/txt/ui/TxtReader.kt` — `LazyColumn` of monospace `Text` items keyed by line index. Typography token: `MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)`. Optional line numbers in the gutter (DataStore preference).
- [ ] **E.7** Encoding-override affordance: a small chip in the reader top-bar showing the detected encoding ("UTF-8" / "cp1252" / "UTF-16 LE"), tappable to bring up a dialog with the candidate charsets. Selection writes to `DocumentEntity.encodingOverride: String?` and rebuilds the `LineSource`.
- [ ] **E.8** Robolectric tests in `format/txt/TxtRendererTest.kt`. Fixtures: ASCII `hello.txt`, UTF-8 with BOM, UTF-8 without BOM (with emoji), UTF-16-LE with BOM, Windows-1252 with smart quotes, CRLF-only file, CR-only file, mixed-ending file, empty file, 0-byte file, 1-byte file, 50 MB synthetic log file (stress + index-cache test), single-line 5 MB JSON (G3 path).
- [ ] **E.9** Smoke test on AVD `medium_phone`: push a 1 MB Apache access log to `/sdcard/Documents/pageboy-test/access.log`, open via SAF picker, scroll to the bottom (verify `LazyColumn` windowing — RAM usage in `adb shell dumpsys meminfo com.eight87.pageboy` should not grow proportionally to file size). Screenshot the encoding-override chip. Switch to UTF-16 LE on a known-UTF-8 file, confirm the mojibake renders (proves the override actually re-routes the charset), switch back.
- [ ] **E.10** Hook `.log`, `.txt`, `.text`, `.csv`-when-no-spreadsheet-renderer-wants-it (and a generic "unknown extension + ASCII-looking head bytes" path) into `FormatDetector`. CSV routing is intentional: when we ship XLSX/ODS (phases J/L) the spreadsheet renderer takes `.csv`; until then, plain-text view is better than nothing.
