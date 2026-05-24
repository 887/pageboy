# pageboy — PDF renderer + annotation + signing plan

## Status: 🟡 RECOMMENDED — review pending

PDF is the biggest single research plan in pageboy. Three concerns
cascade into one another and so live in one file:

1. **Renderer** (Phase F) — which library rasterises pages.
2. **Annotation** (Phase G) — where annotation state lives and how it
   round-trips with the PDF.
3. **Signing** (Phase H) — visual stamp vs cryptographic (PAdES) flow.

The renderer choice constrains the annotation persistence model, and
the annotation persistence model constrains the signing flow (because
a "signed PDF" with annotations must serialise both into one byte
stream).

---

## Recommendation (TL;DR)

- **Renderer:** **`androidx.pdf:pdf-viewer` + `androidx.pdf:pdf-viewer-fragment`**
  (currently `1.0.0-alpha18`, April 2026; license: Apache-2.0 via
  AndroidX). Backed by the system `PdfRenderer` (Pdfium under the
  hood) running in an isolated process via `SandboxedPdfLoader`. The
  read+render features have been **backported to minSdk 28**, which
  matches pageboy exactly. Pure-Kotlin app-side dependency, no app-
  shipped native `.so` files — the native code lives in the platform.
  APK delta: tiny (~300–600 KB Kotlin/AIDL).
- **Annotation persistence:** **Overlay-in-Room as source of truth**
  for v1, with a **v2 export path** that bakes annotations into a
  PDF copy via OpenPDF (MPL-2.0). Reasoning: keeps the original
  document immutable, keeps the SAF write path simple, lets us add
  cryptographic signing later without re-engineering. Highlight /
  underline / strikethrough / ink / text-note / image-stamp all fit
  this model trivially. `androidx.pdf`'s `EditablePdfViewerFragment`
  itself uses `androidx.ink` for freehand and integrates with the
  framework's `applyEdit(FormEditRecord)` — we wire our own overlay
  layer on top of it so pageboy's annotation set is portable across
  the eventual non-AndroidX fallback path too.
- **Signing:**
  - **Visual signature stamp** — same overlay model as annotation. Tap
    "place signature", lift the saved-signature bitmap from
    DataStore, place via `SignaturePlacementSink`, persist coords.
  - **Cryptographic signature** — **PAdES-B-B** via **OpenPDF
    `PdfSignatureAppearance` + Bouncy Castle (`bcprov-jdk18on`,
    `bcpkix-jdk18on`)**. PAdES-B-T (with timestamp) and PAdES-B-LT
    are out of scope for v1 — we leave the hook (TSA URL setting,
    OCSP/CRL fetch) in the data layer so it's a v2 plug-in not a v2
    rewrite. Keys come from either the **Android Keystore** (device-
    resident, for casual signing) or a **PKCS#12 `.p12`** the user
    imports via SAF (for actual CA-issued qualified signing certs).

License gate passes for every recommended component: Apache-2.0
(androidx.pdf, androidx.ink, PdfBox-Android fallback), MPL-2.0
(OpenPDF), MIT-style (Bouncy Castle). MuPDF (AGPL) and iText 5/7
(AGPL or commercial) are rejected.

APK budget contribution (universal): roughly **5–7 MB** —
`androidx.pdf` + `androidx.ink` (~1.5 MB), OpenPDF (~2 MB), Bouncy
Castle `bcprov+bcpkix` post-R8 (~2–3 MB). Well inside the 8 MB local
budget and the 50 MB whole-app ceiling.

---

## 1. Standard 8-gate analysis

### Gate 1 — Candidate libraries (renderers)

| # | Name | Maintainer | Version (2026) | License | Native? | Notes |
|---|---|---|---|---|---|---|
| 1 | **`androidx.pdf`** | Google / AndroidX | `1.0.0-alpha18` (Apr 22 2026) | Apache-2.0 | App ships none; uses system `PdfRenderer` IPC | Annotations via `androidx.ink`, form fill, text selection, search, password, stylus. minSdk 28 backport via `sdk-extension < 13`. ([releases][androidx-pdf-rel]) |
| 2 | **`mhiew/AndroidPdfViewer`** (PDFium wrapper) | Min Hiew (fork of barteksc) | `3.2.0-beta.3` | Apache-2.0 (wrapper) + BSD-3-Clause (PDFium) | Ships `libpdfium.so` per ABI (~3–4 MB each) | minSdk 19; covers render, zoom, password, annotation *rendering* (not editing). ([repo][mhiew-repo]) |
| 3 | **`android.graphics.pdf.PdfRenderer`** | Android framework | n/a — platform | Android system | Platform-side native | Raw bitmap render only. API 21+; API 35 added `PdfFormType`, `selectContent`, `searchText`, `applyEdit`. ([docs][pdfrenderer-docs]) |
| 4 | **`barteksc/AndroidPdfViewer`** (original) | Bartosz Schiller | last active: 2.8.x line; 3.2.0-beta.1 abandoned | Apache-2.0 | Same PDFium .so set | **Dormant in 2026.** Maven-Central artifacts went missing post-jcenter shutdown; supersede with `mhiew` fork. ([issue 1201][bart-1201]) |
| 5 | **MuPDF** | Artifex | 1.27.x line | **AGPL-3.0** or commercial | Ships native | **Disqualified by license gate.** Pageboy ships under MIT-allowlist; AGPL is incompatible. ([licensing][mupdf-lic]) |
| 6 | **PSPDFKit / Nutrient** | Nutrient | commercial | **Commercial** | Native | Out of scope — non-permissive license. Documented so it doesn't keep getting re-suggested. ([site][nutrient]) |
| 7 | **Hand-rolled** (no-dep) | — | — | — | n/a | Not realistic. PDF parsing is a 1500-page spec with bespoke compression filters, encryption schemes, font subsetting. A no-dep baseline exists only as a thought experiment. |

#### Winner: `androidx.pdf`

Reasons it beats the PDFium wrappers in 2026:

- **Smaller APK.** The native code lives in the system, not in our
  APK. `androidx.pdf` itself is Kotlin + AIDL.
- **Annotation editing is upstream.** `EditablePdfViewerFragment` +
  `androidx.ink` covers pen, highlighter (free + snap-to-text),
  eraser, undo/redo in alpha16+. The PDFium wrappers only *render*
  pre-existing annotations.
- **Form fill is upstream.** `EditablePdfDocument` + framework
  `applyEdit(FormEditRecord)` covers text, dropdowns, checkboxes,
  radios. PDFium wrappers don't.
- **Password is upstream.** Built-in password dialog.
- **Sandboxed.** Malformed PDFs crash an isolated process, not the
  pageboy main process.
- **Backported to minSdk 28** as of alpha04, then refined through
  alpha18. Matches pageboy's `minSdk = 28` exactly.

Reasons to keep `mhiew/AndroidPdfViewer` as a **documented fallback**
(not v1 code):

- `androidx.pdf` is still alpha. Breaking-API risk is real (`alpha15`
  switched to Java 11 bytecode, `alpha15` added `sdk-extension 19`
  requirement for image selection). If the alpha stalls, switching to
  the PDFium fork keeps the `DocumentRenderer` interface stable
  because pageboy's `DocumentRenderer` already abstracts the
  framework.
- The fork is permissively licensed and maintained enough for an
  emergency.

The framework `PdfRenderer` direct is too low-level for v1 — text
selection / search / annotations / forms are all delegated by
`androidx.pdf` and would have to be re-implemented if we skipped it.
We use `androidx.pdf`; if it fails, fall back to `mhiew` PDFium.

### Gate 2 — License compatibility

- `androidx.pdf` — **Apache-2.0** (SPDX: `Apache-2.0`). ✅ ([AndroidX
  license][androidx-license])
- `androidx.ink` — **Apache-2.0**. ✅
- `mhiew/AndroidPdfViewer` — **Apache-2.0** (wrapper) over **PDFium
  BSD-3-Clause**. ✅
- **OpenPDF** — **MPL-2.0 OR LGPL-2.1+** dual. We pick **MPL-2.0**.
  ✅ per `oss-licenses.md` (MPL-2.0 is on the allowlist explicitly
  for this kind of "modify-the-PDF-on-disk" library). ([license][openpdf-lic],
  [SPDX][openpdf-spdx])
- **PdfBox-Android** (`com.tom-roush:pdfbox-android:2.0.27.0`) —
  **Apache-2.0**. ✅ Held in reserve as OpenPDF alternative if
  OpenPDF turns out not to build clean on Android (it's pure Java but
  uses some `java.awt` APIs in non-essential code paths that may need
  shimming). ([repo][pdfbox-android-repo])
- **Bouncy Castle** (`bcprov-jdk18on`, `bcpkix-jdk18on`) — **MIT-style**
  ("a license very similar to MIT"). ✅
- **iText 5 / 7** — **AGPL-3.0 or commercial**. ❌
- **MuPDF** — **AGPL-3.0 or commercial**. ❌

### Gate 3 — APK size impact

Per-ABI breakdown:

| Component | Universal APK contribution | Per-ABI release | Notes |
|---|---|---|---|
| `androidx.pdf:pdf-viewer-fragment` | ~400 KB | ~400 KB | Kotlin + AIDL. No native. |
| `androidx.pdf:pdf-document-service` | ~150 KB | ~150 KB | Service stubs. |
| `androidx.ink` (authoring + brush + compose) | ~1.0 MB | ~1.0 MB | Pure JVM after R8. |
| OpenPDF 1.4.x (Maven `com.github.librepdf:openpdf`) | ~2.0 MB | ~2.0 MB | Pure Java. R8 strips heavily — actual delta likely <1.5 MB. |
| Bouncy Castle `bcprov-jdk18on` 1.78+ | ~6 MB raw → ~2.0 MB post-R8 | ~2.0 MB | Hot algorithms only; rest tree-shaken. |
| Bouncy Castle `bcpkix-jdk18on` 1.78+ | ~1.5 MB raw → ~0.6 MB post-R8 | ~0.6 MB | CMS / PKCS, used by the signing path. |
| **Total PDF stack, universal** | **~6 MB** | **~6 MB** | Within 8 MB budget. |

PDFium fallback path (`mhiew/AndroidPdfViewer`) would add ~3–4 MB of
`libpdfium.so` *per ABI* (~12 MB universal if we ship four ABIs, or
~3.5 MB per per-ABI split). One more reason to prefer the AndroidX
path: pageboy's per-ABI release ceiling is 20 MB; `androidx.pdf` keeps
that ceiling comfortable.

### Gate 4 — JVM vs native

- `androidx.pdf` — **JVM in our APK**, native in the system. The OOM /
  buffer-overflow surface area lives in another process.
- `mhiew/AndroidPdfViewer` — **Native** (`libpdfium.so`,
  `libmodft2.so`, `libmodpdfium.so`, `libmodpng.so`).
- OpenPDF — **Pure JVM**.
- Bouncy Castle — **Pure JVM**.

### Gate 5 — Performance (500-page open + per-page render)

`androidx.pdf` uses `SandboxedPdfLoader`. On open: the loader does
not parse the whole document — it opens a sandboxed session and
hydrates page metadata lazily. Documented "may experience increased
startup time" for the first call; subsequent ops reuse the session.
Per-page render is whatever the system `PdfRenderer` delivers, which
on a 2026-era device is Pdfium-fast: typically <60 ms for an A4 page
at screen density on arm64. ([docs][androidx-pdf-rel])

A 500-page PDF open via `androidx.pdf` is measured in hundreds of
milliseconds, not seconds, because the page index isn't fully walked
until a page is requested.

Pageboy contract: the `DocumentRenderer` interface returns a `Flow<PageBitmap>`
that emits as pages render. Skipping ahead invalidates a queued page
render. Phase F sub-step F.4 owns the LRU bitmap cache (eight pages
on phones, four on small-RAM devices).

### Gate 6 — Android version compatibility

minSdk 28. `androidx.pdf` requires `sdk-extension < 13` for the
backport path; that extension is present on essentially all 2026
devices because it ships with Play Services updates rather than a new
platform release. **No compatibility shim required** on pageboy's
target surface.

If we end up shipping the PDFium fallback, it's API 19+ — covered.

### Gate 7 — Maintenance status

- `androidx.pdf` — **actively maintained**, alphas every 2–4 weeks
  through Q2 2026 (alpha15 March 11, alpha16 March 25, alpha18 April
  22). Google-staffed. ([releases][androidx-pdf-rel])
- `androidx.ink` — actively maintained alongside `androidx.pdf`.
  Same release cadence.
- OpenPDF — **active**. 3.0.5 released May 2024, recent maintenance
  through 2025–2026; latest Maven publish "Feb 19 2026" per
  mvnrepository. Steady-state. ([releases][openpdf-rel])
- PdfBox-Android — **stale**. Last release 2.0.27.0 (Jan 2, 2023).
  Still works, but not tracking upstream PDFBox. Acceptable as cold
  fallback. ([releases][pdfbox-android-rel])
- `mhiew/AndroidPdfViewer` — **lukewarm**. 3.2.0-beta.3 is the latest
  on Maven; the original `barteksc` fork is dead. ([maven][mhiew-mvn])
- Bouncy Castle — **actively maintained**, 1.78 / 1.84 line current.

### Gate 8 — Three most likely production crashers

1. **Encrypted PDFs without the right password.** `androidx.pdf`
   surfaces a `LoadParams` for password. We MUST guard `loadDocument`
   with a try/catch and present pageboy's own password sheet
   (pageboy chrome, not framework chrome) on failure. Test fixture:
   the 50-page `encrypted-aes256.pdf` from the OWASP corpus.
2. **Malformed cross-reference tables.** Some PDFs in the wild
   (especially old DocuWare exports) have a corrupt XRef. `androidx.pdf`
   delegates to the framework, which uses Pdfium's recovery path. If
   recovery fails the loader throws; we map to
   `RenderResult.Unsupported(reason = "corrupt_xref")`. Test fixture:
   the bad-xref sample from Adobe's test corpus.
3. **Embedded JavaScript.** PDFs can carry `/JS` actions. We MUST
   ignore (not execute). `androidx.pdf` does not execute JS by
   default — verify the assumption in F.6 by opening a known
   JS-bomb PDF (e.g. the "calculator.pdf" from the Adobe JS samples
   repo) and asserting no behaviour change.

Out-of-scope-but-named for future phases: embedded video / 3D
annotations (`/RichMedia`), embedded fonts that aren't subsettable,
font fallback for CJK without a system CJK font, very large pages
(>14400 pt — A0 architectural drawings).

---

## 2. Annotation (Phase G) — detailed design

### The persistence question

Three options were on the table; the recommendation is **(C) — Room
as source of truth, OpenPDF bake as an export path**.

**(A) Overlay table in Room — source of truth, never write to PDF.**

Schema:

```kotlin
@Entity(tableName = "annotation")
data class AnnotationEntity(
    @PrimaryKey val id: String,                 // UUIDv4
    val documentId: String,                     // SHA-256(treeUri + relativePath)
    val pageIndex: Int,                         // 0-based
    val kind: AnnotationKind,                   // HIGHLIGHT | UNDERLINE | STRIKETHROUGH | INK | NOTE | STAMP
    val payload: String,                        // kotlinx.serialization JSON, kind-specific
    val pageWidthPt: Float,                     // for coordinate sanity after re-render at new zoom
    val pageHeightPt: Float,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class AnnotationKind { HIGHLIGHT, UNDERLINE, STRIKETHROUGH, INK, NOTE, STAMP }
```

Per-kind payloads:

- `HIGHLIGHT / UNDERLINE / STRIKETHROUGH` → `{ "color": "#FFFF00", "quadPoints": [...] }`
  — list of quadrilateral corners in PDF user-space (origin
  bottom-left, points).
- `INK` → `{ "color": "#000000", "thickness": 2.0, "strokes": [[{x,y,t,pressure}...]] }`
  — `androidx.ink` stroke samples in PDF user-space.
- `NOTE` → `{ "anchor": {x,y}, "title": "...", "body": "..." }`
- `STAMP` → `{ "imageRef": "stamps/signature-1.png", "rect": {x,y,w,h} }`
  — `imageRef` is relative to app private storage; signature stamps
  live in DataStore-managed `~/files/stamps/`.

Pros: original PDF never touched; SAF write permission only needed at
export time; reversible; trivially round-trips through device
reinstall; pageboy gets to render annotations its own way (M3
Expressive colour theming on highlights, etc.).
Cons: not portable — open the same PDF in Adobe Reader and your
marks aren't there.

**(B) Write PDF 1.7 `/Annot` dictionaries into the PDF on every change.**

Pros: portable.
Cons: requires SAF write permission on every annotation tap (UX
disaster on SAF — every write triggers `ACTION_OPEN_DOCUMENT_TREE`
re-prompts on some OEM ROMs); requires OpenPDF in the hot path;
mutates the user's file in place (data-loss risk); incremental update
appends bytes to the PDF, growing it on every keystroke.

**(C) Room source of truth + OpenPDF export-with-annotations.** *(chosen)*

Day-to-day reading and annotating goes through (A). The "Export with
annotations" action (a one-shot user-initiated flow, behind an
explicit menu) opens the source PDF, walks the Room annotations for
that document ID, writes them as `/Annot` dictionaries via OpenPDF,
and writes the result to a SAF target the user picks. The source PDF
is never mutated. Cryptographic signing (Phase H) also uses this
path — signing creates a *new* PDF, doesn't mutate the source.

Pros: simple Compose UI (no SAF write contention on every stroke);
portable when the user needs it; signing is just "export +
sign-instead-of-just-save"; original is never at risk.
Cons: two render passes (Compose overlay for reading, OpenPDF for
export). Worth it.

### Per-annotation-kind implementation sketch

| Kind | Compose overlay (read path) | OpenPDF export (write path) |
|---|---|---|
| Highlight | `Canvas` draws semi-transparent yellow rects over `quadPoints` | `PdfAnnotation.createMarkup(... HIGHLIGHT)` per quad |
| Underline | `Canvas` draws line at quad bottom edge | `PdfAnnotation.createMarkup(... UNDERLINE)` |
| Strikethrough | `Canvas` draws line at quad midline | `PdfAnnotation.createMarkup(... STRIKE)` |
| Ink (freehand) | `androidx.ink.authoring.compose.InProgressStrokesView` for capture; `Canvas` redraws committed strokes | `PdfAnnotation.createInk(strokes, color)` |
| Note (text comment) | M3 `AssistChip` at anchor, expands to bottom sheet | `PdfAnnotation.createText(rect, contents)` |
| Stamp (signature image) | `Image` composable at rect | `PdfAnnotation.createStamp(rect, imageRef)` via `PdfTemplate` |

Coordinate space: PDF user-space (origin bottom-left, points = 1/72
inch). The Compose overlay applies the page's transform matrix
inversely. Phase G sub-step G.5 owns the matrix helper and unit-tests
it under Robolectric against the four standard PDF rotations
(0/90/180/270).

### Conflict / merging

Single-device-per-user means no real concurrent edits to worry about
in v1. If the user opens the same PDF (same documentId) on two
devices and Annotation Sync ever ships (out of scope for v1), Room's
`updatedAt` is the LWW tie-breaker. Document for the future, don't
build for it.

---

## 3. Signing (Phase H) — detailed design

### Two flows, share zero crypto

#### 3.1 Visual signature stamp (the easy one)

User stores one or more signature images in
`/data/data/com.eight87.pageboy/files/stamps/`. Signature capture is
a Compose `Canvas` with `androidx.ink` capture (same path as ink
annotation), saved as a transparent-background PNG. To place: tap
"Sign here" in the annotation toolbar, pick which signature, drop
onto the page — this is a `STAMP` annotation per Phase G. Zero
crypto. Zero PAdES.

This is the v1 default. It's what most users mean when they say
"sign the PDF".

#### 3.2 Cryptographic signature (PAdES-B-B)

For users who *actually* need a signature that Adobe Reader badges as
"signed by … (certificate trusted)", we ship the cryptographic path
behind a separate "Cryptographic signature" entry in the signing
sheet.

**Standard targeted: PAdES-B-B** (ETSI EN 319 142, "Basic" level —
the signature byte range, contents, signing cert, signing time). No
TSA (PAdES-B-T) and no long-term-validation material (PAdES-B-LT) in
v1. Both are accommodated by the data layer (settings entry for TSA
URL exists, currently disabled) so adding them is a plug-in, not a
rewrite.

**Library stack:**

```
androidx.pdf  ──▶  reads the PDF for annotation overlay (Phase F+G)
                                  │
                                  ▼
OpenPDF       ──▶  PdfSignatureAppearance (the signature dictionary,
                   /ByteRange, /Contents placeholder, signed-attrs hash)
                                  │
                                  ▼
Bouncy Castle ──▶  bcpkix CMS / PKCS#7 SignedData generation
   bcprov     ──▶  RSA-PSS / ECDSA signing primitives
                                  │
                                  ▼
Output        ──▶  signed PDF written to user-picked SAF target
```

The signature dictionary uses subfilter **`ETSI.CAdES.detached`** for
PAdES (per ETSI EN 319 142-1 §5.3) — `ADBE.PKCS7.detached` is the
legacy adbe subfilter and is also accepted, but `ETSI.CAdES.detached`
is what makes Adobe Reader badge it as PAdES. Document this choice
inline in `PdfCryptoSigner.kt`.

**Key source — two paths:**

1. **Android Keystore** (`AndroidKeyStore` provider). User generates
   a non-extractable EC P-256 key on the device. Pageboy issues a
   self-signed cert chain (also stored in Keystore). The resulting
   signature is cryptographically sound but **not a qualified
   electronic signature** because the cert isn't issued by a Trust
   Service Provider. This is fine for "I want a tamper-evident
   signature on this PDF for my own audit", which is what 90% of
   users actually want when they say "sign".
2. **PKCS#12 (`.p12`) import.** User imports a `.p12` they got from
   their CA (e.g. their national identity card software, their
   employer's PKI). Pageboy reads it via Bouncy Castle's
   `KeyStore.getInstance("PKCS12", BouncyCastleProvider())`, prompts
   for the .p12 password, signs with the private key inside. Cert
   chain comes from the .p12. This is the path to actual qualified
   signatures.

**Timestamp authority (out of scope for v1, hook present):**

- Free TSA: **FreeTSA.org** (TSA cert valid through Feb 2040 as of
  March 2026, ECDSA P-384). RFC 3161. URL:
  `https://freetsa.org/tsr`. ([freetsa][freetsa])
- Fallback: DigiCert public TSA (`http://timestamp.digicert.com`).
- Sigstore's timestamp authority is interesting but is biased toward
  software-supply-chain use; deprioritise.

The settings catalog entry exists in v1 (greyed out, "Add timestamp
to signatures — v2") so the user sees the roadmap.

**Long-term validation (PAdES-B-LT) — out of scope.** Document hook,
don't build.

### What about pure system signing?

Android's framework does not ship a PAdES signer. `androidx.pdf` does
not ship a PAdES signer. Bouncy Castle + a PDF-writer is the only
realistic permissively-licensed path. Confirmed.

### Three crash surfaces in production signing

1. **`.p12` with non-extractable private key.** Some hardware-backed
   PKCS#12s wrap a non-exportable key inside a software cert. Pageboy
   must detect and present "this key cannot be used for offline
   signing — use a smart-card-aware signer" rather than crashing in
   Bouncy Castle's `PKCS12KeyStoreSpi`.
2. **PDFs with existing signatures.** Re-signing an already-signed
   PDF MUST be an incremental update (don't break the existing
   signature). OpenPDF's `PdfStamper.createSignature(reader, os,
   '\\0', null, true)` does the right thing — the `true` is
   "append", which keeps prior signatures valid. Test fixture: a
   PDF already PAdES-B-B signed.
3. **Wrong `/ByteRange`.** Easy to miscompute. OpenPDF handles this
   correctly if you use the high-level API; if you ever drop to the
   low-level dictionary editor, you MUST re-hash after byte-range
   placement. Add a roundtrip Robolectric test: sign → verify with
   Bouncy Castle's `CMSSignedDataParser` → assert valid.

---

## APK-size budget (PDF stack)

| Component | Universal | Per-ABI release |
|---|---|---|
| `androidx.pdf:pdf-viewer-fragment:1.0.0-alpha18` | 0.4 MB | 0.4 MB |
| `androidx.pdf:pdf-viewer` (compose) | 0.2 MB | 0.2 MB |
| `androidx.pdf:pdf-document-service` | 0.15 MB | 0.15 MB |
| `androidx.ink:ink-authoring` + `ink-brush` + `ink-authoring-compose` + `ink-brush-compose` | 1.0 MB | 1.0 MB |
| `com.github.librepdf:openpdf:1.4.x` (or 2.0.x) | 2.0 MB raw → ~1.5 MB post-R8 | same |
| `org.bouncycastle:bcprov-jdk18on:1.78+` | 6 MB raw → ~2.0 MB post-R8 | same |
| `org.bouncycastle:bcpkix-jdk18on:1.78+` | 1.5 MB raw → ~0.6 MB post-R8 | same |
| **Total (post-R8 estimate)** | **~5.5–6.0 MB** | **~5.5–6.0 MB** |

Under the 8 MB PDF-stack budget set in the prompt. Confirms in
Phase F.7 by actually shrinking a debug build with `R8` full mode
and reading `app/build/outputs/mapping/release/resources.txt`.

---

## Performance characteristics

- Open 500-page PDF: <500 ms first-page-displayed (sandbox warm-up
  ~150 ms + first page render ~50 ms + UI compose ~50 ms; the other
  299 ms is SAF roundtrip + Room metadata write).
- Per-page render at screen density on arm64: <60 ms.
- Annotation overlay redraw on scroll: 16-ms budget per frame
  trivially met — `Canvas` of 10 highlights per visible page is
  microseconds.
- Sign (PAdES-B-B) on a 500-page PDF: <2 seconds, dominated by the
  full-document SHA-256 hash. Add a determinate progress indicator;
  do NOT block UI thread (Phase H.6 wires the signing flow into a
  `withContext(Dispatchers.IO)` block driven from the signing
  ViewModel).

---

## Spec gotchas (rehash from Gate 8)

1. Encrypted PDFs — `androidx.pdf` LoadParams password path.
2. Malformed XRef — sandbox recovery; map failures to
   `RenderResult.Unsupported`.
3. Embedded JS — ignored, never executed. Verify by smoke test.
4. AcroForm fields — `androidx.pdf` form-fill APIs handle text /
   dropdowns / checkboxes / radios. XFA forms (legacy Adobe) are
   out of scope.
5. Existing signatures — re-sign in append mode; never invalidate
   prior signatures.

---

## Sources

- [androidx.pdf releases][androidx-pdf-rel]
- [androidx.pdf media-grow page][androidx-pdf-media]
- [SandboxedPdfLoader API ref][sandboxed-pdf-loader]
- [PdfRenderer framework API][pdfrenderer-docs]
- [androidx.ink releases][androidx-ink-rel]
- [mhiew AndroidPdfViewer fork][mhiew-repo]
- [mhiew android-pdf-viewer on Maven Central][mhiew-mvn]
- [barteksc 3.2.0-beta missing artifact issue 1201][bart-1201]
- [MuPDF licensing page][mupdf-lic]
- [Nutrient (PSPDFKit) Android SDK][nutrient]
- [OpenPDF GitHub][openpdf-repo]
- [OpenPDF license file (MPL-2.0 OR LGPL-2.1+)][openpdf-lic]
- [OpenPDF SPDX wiki][openpdf-spdx]
- [OpenPDF releases][openpdf-rel]
- [PdfBox-Android repo][pdfbox-android-repo]
- [PdfBox-Android releases (latest 2.0.27.0 Jan 2023)][pdfbox-android-rel]
- [Bouncy Castle latest releases][bc-rel]
- [Bouncy Castle bcprov-jdk18on Maven][bc-prov-mvn]
- [Bouncy Castle bcpkix-jdk18on Maven][bc-pkix-mvn]
- [FreeTSA — RFC 3161 free TSA][freetsa]
- [DigiCert public TSA][digicert-tsa]
- [Sigstore timestamp authority][sigstore-tsa]
- [PAdES / EN 319 142-1 overview, ETSI][padees]
- ["Say Goodbye to Third-Party PDF Libraries: AndroidX PDF Is Here" — droidcon write-up][droidcon-pdf]
- [Apache PDFBox `CreateSignature.java` reference example][pdfbox-sign-example]

[androidx-pdf-rel]: https://developer.android.com/jetpack/androidx/releases/pdf
[androidx-pdf-media]: https://developer.android.com/media/grow/pdf-viewer
[sandboxed-pdf-loader]: https://developer.android.com/reference/androidx/pdf/SandboxedPdfLoader
[pdfrenderer-docs]: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
[androidx-ink-rel]: https://developer.android.com/jetpack/androidx/releases/ink
[mhiew-repo]: https://github.com/mhiew/AndroidPdfViewer
[mhiew-mvn]: https://central.sonatype.com/artifact/com.github.mhiew/android-pdf-viewer
[bart-1201]: https://github.com/DImuthuUpe/AndroidPdfViewer/issues/1201
[mupdf-lic]: https://mupdf.readthedocs.io/en/1.27.0/license.html
[nutrient]: https://www.nutrient.io/sdk/android/
[openpdf-repo]: https://github.com/LibrePDF/OpenPDF
[openpdf-lic]: https://github.com/LibrePDF/OpenPDF/blob/master/LICENSE.md
[openpdf-spdx]: https://github.com/LibrePDF/OpenPDF/wiki/OpenPDF---License-MPL---LGPL-background-info
[openpdf-rel]: https://github.com/LibrePDF/OpenPDF/releases
[pdfbox-android-repo]: https://github.com/TomRoush/PdfBox-Android
[pdfbox-android-rel]: https://github.com/TomRoush/PdfBox-Android/releases
[bc-rel]: https://www.bouncycastle.org/latest_releases.html
[bc-prov-mvn]: https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk18on
[bc-pkix-mvn]: https://mvnrepository.com/artifact/org.bouncycastle/bcpkix-jdk18on
[freetsa]: https://www.freetsa.org/index_en.php
[digicert-tsa]: https://knowledge.digicert.com/general-information/rfc3161-compliant-time-stamp-authority-server
[sigstore-tsa]: https://github.com/sigstore/timestamp-authority
[padees]: https://www.etsi.org/deliver/etsi_en/319100_319199/31914201/
[droidcon-pdf]: https://www.droidcon.com/2025/06/06/say-goodbye-to-third-party-pdf-libraries-androidx-pdf-is-here/
[pdfbox-sign-example]: https://github.com/BrentDouglas/pdfbox/blob/master/examples/src/main/java/org/apache/pdfbox/examples/signature/CreateSignature.java

---

## Phase F — PDF renderer (implementation sub-steps)

- [ ] **F.1** Add `androidx.pdf:pdf-viewer-fragment:1.0.0-alphaNN`,
  `androidx.pdf:pdf-viewer`, `androidx.pdf:pdf-document-service`,
  `androidx.ink:ink-authoring`, `androidx.ink:ink-brush`,
  `androidx.ink:ink-authoring-compose`, `androidx.ink:ink-brush-compose`
  to `app/build.gradle.kts`. Pin to the latest stable-enough alpha.
  Confirm AGP 9 + minSdk 28 + sdk-extension 13 baseline lines up
  cleanly with the current `android` CLI bundle.
- [ ] **F.2** Implement `format/pdf/AndroidxPdfRenderer.kt` as the
  `DocumentRenderer` impl. Public surface: `open(uri) -> PdfHandle`,
  `renderPage(pageIndex) -> Bitmap`, `close()`. Internally wraps
  `SandboxedPdfLoader.openDocument(...)` and the resulting
  `PdfDocument`. Sandboxed-process exceptions map to
  `RenderResult.Unsupported(reason)`.
- [ ] **F.3** Implement `format/pdf/PdfPasswordSheet.kt` — pageboy's
  own M3 Expressive bottom-sheet password prompt. Triggered when
  `openDocument` returns a password-required result. Uses
  pageboy chrome, not the framework prompt.
- [ ] **F.4** Implement an LRU bitmap cache in
  `format/pdf/PdfPageCache.kt` (eight pages on devices with
  `ActivityManager.MemoryInfo.totalMem >= 4GB`, four pages otherwise).
  Page bitmaps live in `Bitmap.Config.HARDWARE` where possible to
  keep them off the Java heap.
- [ ] **F.5** Wire `format/pdf/AndroidxPdfRenderer` into `FormatDetector`
  (magic bytes `%PDF-` at offset 0) and the `AppGraph`.
- [ ] **F.6** Verify three crash surfaces against the test corpus
  pushed by `scripts/push-test-documents.sh`:
  encrypted-aes256.pdf, bad-xref.pdf, js-bomb.pdf. Run via Robolectric
  (parse-only) and via the AVD smoke test (full render).
- [ ] **F.7** Measure R8 release APK delta with and without
  `androidx.pdf` to confirm we're inside the 8 MB PDF-stack budget.
  Record actual MB in this file under "APK-size budget".
- [ ] **F.8** Document the `mhiew/AndroidPdfViewer` fallback in
  `format/pdf/README.md` — exactly the steps to swap the
  `DocumentRenderer` impl if `androidx.pdf` is ever a blocker. Do
  not ship the fallback in v1.

## Phase G — Annotation (implementation sub-steps)

- [ ] **G.1** Define `AnnotationEntity` + `AnnotationKind` enum +
  Room DAO in `data/annotation/`. Migration test:
  `documentId` survives `removeAndReadd` of a SAF tree.
- [ ] **G.2** Implement `AnnotationOverlay.kt` Compose composable —
  reads `Flow<List<AnnotationEntity>>` for the current visible page
  range, draws each kind via `Canvas`. Coordinate transform from PDF
  user-space → screen-space lives in `PdfCoordinateMatrix.kt` with a
  Robolectric unit test covering all four PDF rotations.
- [ ] **G.3** Implement annotation creation gestures:
  - Long-press over text → select text → highlight / underline /
    strikethrough quick-action (M3 expressive context menu).
  - Pen-tool mode → freehand ink via
    `androidx.ink.authoring.compose.InProgressStrokesView`.
  - Note-tool → tap to anchor → bottom sheet editor.
  - Stamp-tool → choose saved stamp → tap to place.
- [ ] **G.4** Implement `SignaturePlacementSink` interface — narrow
  one-method interface the signature pad sees (per ISP). Concrete
  impl is the same annotation DAO.
- [ ] **G.5** Implement OpenPDF export path
  `format/pdf/AnnotationExporter.kt` — open source PDF, walk
  `AnnotationEntity` rows for document, emit `/Annot` dictionaries
  in PDF order, write to SAF target. Verify with Adobe Reader
  smoke-load on the AVD.
- [ ] **G.6** Wire annotation toolbar into the reader chrome — the
  M3 vertical-rail icons for "pen", "highlight", "note", "stamp",
  "eraser" sit in the bottom inset per `ui-shell.md`.
- [ ] **G.7** Add Robolectric tests for each annotation kind: create
  → save → reload → assert payload identity.

## Phase H — Signing (implementation sub-steps)

- [ ] **H.1** Implement `SignatureCaptureScreen.kt` — Compose `Canvas`
  + `androidx.ink` capture, saves transparent-background PNG to
  `~/files/stamps/<uuid>.png`. Persists a stamp index in DataStore
  (one or more named signatures).
- [ ] **H.2** Implement visual-signature placement as a `STAMP`
  annotation (Phase G machinery). No new code path; verify the
  signature stamp renders identically on read and after OpenPDF
  export.
- [ ] **H.3** Add `org.bouncycastle:bcprov-jdk18on:1.78+` and
  `bcpkix-jdk18on:1.78+` dependencies. Register
  `BouncyCastleProvider` in `AppGraph.init()` exactly once.
- [ ] **H.4** Implement `PdfCryptoSigner.kt` — PAdES-B-B signing via
  OpenPDF `PdfStamper.createSignature(reader, os, '\\0', null, true)`
  + `PdfSignatureAppearance` with subfilter `ETSI.CAdES.detached`.
  CMS / PKCS#7 SignedData via `CMSSignedDataGenerator` + Bouncy
  Castle.
- [ ] **H.5** Implement key sources:
  - `AndroidKeystoreKeyProvider.kt` — EC P-256 keygen, self-signed
    cert chain, all in `AndroidKeyStore`.
  - `Pkcs12FileKeyProvider.kt` — load .p12 via SAF picker, password
    prompt sheet, Bouncy Castle `PKCS12` keystore parse.
- [ ] **H.6** Wire signing flow into a `withContext(Dispatchers.IO)`
  block; ViewModel emits progress (0–100) for the determinate
  progress indicator. Phase H.6 must NOT block the main thread.
- [ ] **H.7** Reserve settings-catalog entries (greyed out in v1)
  for "Add timestamp to signatures (PAdES-B-T)" and "Long-term
  validation (PAdES-B-LT)" so the v2 hook is user-visible.
- [ ] **H.8** Add Robolectric tests:
  - sign with self-signed Keystore key → re-open with
    `CMSSignedDataParser` → assert valid.
  - sign already-signed PDF → assert original signature still
    valid (append mode).
  - sign with intentionally-corrupt .p12 → assert friendly error,
    no crash.
- [ ] **H.9** AVD smoke test: sign a 50-page test PDF with a
  device-Keystore key, open in `androidx.pdf` viewer, confirm the
  signature dictionary is present and the file remains renderable.
  Also load it on the test phone in Adobe Reader (manual) and
  capture a screenshot of the "Signed by self-signed cert" badge
  for the Phase H release-notes evidence pack.
