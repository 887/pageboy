# pageboy — MOBI renderer plan

## Status: ✅ DONE — Phase Q shipped 2026-05-24. Added after the
## initial 8-format brief as a natural fit alongside EPUB for ebook
## coverage. Hand-rolled parser + Android WebView host, zero new deps,
## DRM-protected files emit friendly error state.

## Scope

**In scope:**
- **MOBI 6 (PalmDOC)** — the original Mobipocket format. Palm Database
  Format container + PalmDOC-compressed body of HTML-like markup.
- **MOBI/KF8** — Amazon's HTML5 + CSS3 successor (2011). Often a single
  file with both MOBI 6 + KF8 sections so older Kindles fall back to
  MOBI 6 while newer Kindles read KF8.
- **AZW** — essentially MOBI 6 with Amazon's identifier branding.
- **AZW3** — essentially KF8 with Amazon's identifier branding.

**Out of scope (v1):**
- **KFX** — Amazon's newer container (2015+). Complex, reverse-engineered,
  fragile. Defer to v1.x if user demand surfaces.
- **DRM** — Amazon's MobiDRM scheme is proprietary. DRM-free MOBI/KF8/AZW3
  files (the bulk of what users typically have from pre-2023 downloads)
  work; encrypted Amazon-purchased files do not. Document loudly in the
  reader error state when DRM detected.
- **Cloud sync of last-read position** — Amazon's Whispersync is proprietary
  + requires their account. Out of scope; pageboy's own per-document
  scroll persistence works fine.

## Recommendation

**Renderer:** **Hand-rolled MOBI parser + WebView-based rendering.**
- Parser extracts HTML from the MOBI container (PalmDB + Mobipocket
  headers + PalmDOC decompression + section concatenation).
- Body composable hosts an Android `WebView` (Compose `AndroidView` wrap)
  rendering the extracted HTML.
- v1.1: route through Readium's navigator once Phase M's WebView host
  is generalizable for non-EPUB content. v1 stays standalone to ship
  in parallel with Phase M.

**Why hand-rolled parser:** the only Java/Kotlin MOBI parser libraries
on Maven Central in 2026 are either GPL-licensed (calibre-derived) or
abandoned (last commit 2019). The MOBI spec is documented publicly (see
mobileread wiki); a basic MOBI 6 + KF8 reader is ~1,500-2,500 LOC of
pure Kotlin — within v1 budget. No third-party parser dep.

**Why WebView (not Compose-native):** MOBI/KF8 content is HTML5 + CSS.
A Compose-native renderer would need to re-implement a CSS engine + box
model — wildly out of scope. WebView is the same trade-off Phase M
documented for EPUB; same trade-off applies here.

## Library candidates evaluated

| Candidate | SPDX | Verdict |
| --- | --- | --- |
| `org.jhy.mobi-java` (apparent) | unverified (last activity 2019) | REJECT — dormant, license unverified |
| `io.github.gmt2001:mobi` | unverified | REJECT pending verification — likely dormant |
| **mobi-utils Java forks** | various, mostly GPL | REJECT — GPL infects single APK |
| `com.amazon:kindle-format-tools` | proprietary (Amazon internal) | REJECT — not public |
| **Hand-rolled from spec** | MIT (ours) | **ADOPT** — ~1,500-2,500 LOC, fully under our control |
| **MOBI-to-EPUB conversion + Readium** | depends on converter | DEFER v1.x — converter libs are GPL/proprietary; lossy |

## License compatibility

Hand-rolled = trivially MIT. No third-party dep means no Licensee gate
concerns. Bouncy Castle (already in Phase H for PDF signing) is NOT
needed for MOBI v1 — DRM-free files have no crypto layer.

## APK-size budget

- Parser: ~50-80 KB compiled Kotlin + tests.
- WebView host: already in the APK (shipped with Android system). Zero
  additional APK weight.
- Total: well under any meaningful budget. **MOBI is APK-cheap.**

## Magic bytes for classifier

MOBI files are PalmDB containers. The format detection in `DocumentClassifier`:
- Read first 78 bytes (PalmDB header + creator code).
- Offset 60-64: **type code** — `BOOK` for older MOBI, `TEXt` for PalmDOC.
- Offset 64-68: **creator code** — `MOBI` for Mobipocket / Amazon MOBI/KF8.
- Combined: `(type == "BOOK" || type == "TEXt") && creator == "MOBI"` → MOBI/KF8/AZW/AZW3.

Extension fallback: `.mobi` / `.azw` / `.azw3` / `.prc` (older PalmDOC ebooks).

## MIME types (for intent filter / open-with)

Phase A.6 manifest filters add:
- `application/x-mobipocket-ebook` (canonical for MOBI)
- `application/vnd.amazon.ebook` (canonical for AZW / KF8 / AZW3)
- Extension catch-all via pathPattern for `.mobi`, `.azw`, `.azw3`,
  `.prc` (covers misbehaving senders that emit `application/octet-stream`).

Per Phase N's open-with handling, the same catch-all infrastructure
(application/octet-stream + pathPattern) automatically covers MOBI
files emitted via email / file manager / browser download.

## Spec gotchas

- **DRM detection.** The Mobipocket EXTH header has a record type 401
  (`drm_server_id`) that indicates DRM presence. If detected, render
  an error state explaining "this MOBI file is DRM-protected; pageboy
  does not support DRM. Try a DRM-free version." — don't crash, don't
  try to read the encrypted body.
- **KF8 vs MOBI 6 dispatch.** A combo file has two PalmDB sections; the
  PalmDOC header at offset 0x18 of the first section points at the
  start of the KF8 section if present (FDST/FCIS markers). Parser
  detects KF8 + prefers it; falls back to MOBI 6 if no KF8 section.
- **PalmDOC compression.** Records may be uncompressed (mode 1),
  PalmDOC LZ77-style (mode 2), or HUFF/CDIC (mode 17480 — rare,
  defer to v1.x). v1 ships modes 1 + 2; mode 17480 falls back to "MOBI
  format variant not supported" error state.
- **Images.** MOBI images are stored as JPEG/GIF/PNG inline records.
  Parser extracts them; WebView references via `pageboy://mobi/<id>`
  scheme with a custom WebViewClient that resolves to the in-memory
  byte streams.
- **Cover image.** EXTH record type 201 points at the cover-image record
  index. Used for the library card thumbnail.
- **Metadata.** EXTH header has author (100), publisher (101), description
  (103), ISBN (104), subject (105). Extract for the Settings ->
  Library card display.
- **CR/LF normalization.** PalmDOC content may have mixed line endings;
  WebView handles whatever HTML we give it.

## Phase Q — implementation sub-steps — _Shipped: Q.1–Q.9 in commit `873c56b`_

(Phase Q = MOBI — letters A through P already in use; Q is the next
unused letter after Phase O release-pipeline lands. Plan files
ordered alphabetically.)

- [x] **Q.1** Add `Mobi` to `DocumentFormat` enum. Add format-label
      string resource. Add the `format_label_mobi` localized label.
- [x] **Q.2** Extend `DocumentClassifier` with the MOBI magic-byte sniff
      + extension fallback (`.mobi`, `.azw`, `.azw3`, `.prc`).
- [x] **Q.3** Extend the manifest intent filters with MOBI MIME types +
      pathPattern catch-alls.
- [x] **Q.4** `format/mobi/` package:
  - `MobiRenderer.kt` — `DocumentRenderer` impl. ~80 LOC.
  - `MobiHandle.kt` — `data class MobiHandle(val html: String, val
    metadata: MobiMetadata, val images: Map<String, ByteArray>) :
    DocumentHandle`.
  - `MobiParser.kt` — orchestrates the parse pipeline. ~150 LOC.
  - `internal/PalmDbReader.kt` — reads the PalmDB container (header +
    record offsets). ~200 LOC.
  - `internal/MobipocketHeaderReader.kt` — reads the Mobipocket-specific
    header (type/creator codes, EXTH metadata, KF8 dispatch). ~250 LOC.
  - `internal/PalmDocDecompressor.kt` — PalmDOC LZ77 decompression
    (mode 2). ~150 LOC.
  - `internal/Kf8Reader.kt` — KF8-specific markup walk (HTML5 + CSS3).
    ~200 LOC.
  - `internal/MobiImageExtractor.kt` — extracts inline image records.
    ~100 LOC.
  - `MobiBody.kt` — Compose `AndroidView<WebView>` host. Wires the
    `RendererContext.scrollSink` via scroll-position observation +
    `RendererContext.findSink` via `WebView.findAllAsync` + the find
    listener. ~200 LOC.
  - `MobiWebViewClient.kt` — custom WebViewClient that resolves
    `pageboy://mobi/<id>` URLs to the in-memory image bytes. ~80 LOC.
  - `MobiTitleExtractor.kt` — EXTH metadata title; fallback to filename.
- [x] **Q.5** Register `MobiRenderer` in `AppGraph.formatRegistry` —
      `DocumentFormat.Mobi to MobiRenderer()`.
- [x] **Q.6** Per R.X.2 — `MobiParseError` is a sealed type covering
      DrmDetected / UnsupportedCompression / MalformedContainer /
      EmptyContent. The reader chrome's error state branches via
      sealed dispatch, not a `when (errorMessage.contains(...))` switch.
- [x] **Q.7** Tests:
  - `PalmDbReaderTest` — feeds known PalmDB byte prefixes; asserts
    header parsing + record offset extraction.
  - `MobipocketHeaderReaderTest` — feeds known Mobipocket headers;
    asserts type/creator code detection + EXTH metadata + KF8
    dispatch.
  - `PalmDocDecompressorTest` — feeds known PalmDOC compressed
    streams; asserts byte-exact decompression.
  - `MobiParserTest` — end-to-end parse on small fixture MOBI files
    (under `app/src/test/resources/fixtures/`).
  - `MobiRendererTest` — DocumentRenderer contract.
  - `MobiBodySmokeTest` — Compose. Renders MobiBody with a small
    fixture; asserts WebView loads + scroll-position records.
  - `MobiClassifierTest` — extends the existing `DocumentClassifierTest`
    with MOBI byte-prefix cases.
- [x] **Q.8** Build green; pre-merge 8-item checklist all PASS.
- [x] **Q.9** Update `docs/plans/main.md` — add Phase Q sub-steps; tick;
      "Shipped: Q.1-Q.9 in commit `<hash>`".
      Update `docs/plans/format-research.md` — add Q to the format-by-format
      reading order. Update `docs/plans/refactor-solid.md` —
      R.X.9 deeper.

## SOLID compliance (per refactor-solid.md)

- **R.X.1** narrow interfaces: `MobiParseResult` is a sealed result type;
  consumers take what they need.
- **R.X.2** sealed: `MobiParseError`, `MobiCompressionMode`, `MobiVariant`
  (Mobi6 / Kf8 / Combo) — all sealed.
- **R.X.3** composition root: `AppGraph` wires the renderer.
- **R.X.4** file size: every file in `format/mobi/internal/` under 300
  LOC; orchestrator `MobiParser` under 200; body under 250.
- **R.X.5** `NotImplementedError`: only allowed in the HUFF/CDIC
  decompressor stub, with `// closed by v1.x — HUFF/CDIC decompression`
  comment.
- **R.X.6** wrong-direction: `format/mobi/` does not import `ui/` or
  `data/library/`.
- **R.X.7** Compose ISP: `MobiBody` takes `MobiHandle` + `RendererContext`,
  not god-state.
- **R.X.8** test discipline: every file has at least one test (per Q.7).
- **R.X.9** `MobiRenderer` is just another `DocumentRenderer` impl;
  dispatch via `FormatRegistry`; no `when (format)` anywhere outside
  the registry.

## What this is NOT

- **Not a Kindle-account-integrated reader.** Pageboy reads DRM-free
  MOBI files. No Amazon login, no Whispersync, no library sync.
- **Not a MOBI editor.** Read-only.
- **Not a converter.** Pageboy reads MOBI; pageboy does not write MOBI.
  Annotation / signing flows for MOBI files: the reader chrome's
  AnnotationCommands works on the overlay model (per Phase G); the
  overlay is per-document-id and works for any format. Export-with-
  annotations for MOBI is a v1.x consideration (would need a MOBI
  writer or a MOBI -> EPUB converter for the export path).
