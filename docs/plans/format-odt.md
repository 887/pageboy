# pageboy — ODT renderer plan

## Status: 🟡 RECOMMENDED — review pending

## TL;DR

Ship a **hand-rolled minimal ODT parser** as v1. ODT files are ZIP archives whose only
load-bearing parts (`content.xml`, `styles.xml`, `META-INF/manifest.xml`) are XML, and the
read-only subset pageboy needs is ~400–600 LOC of `org.xmlpull` SAX walking on top of
`java.util.zip`. The reference Java library — Apache ODF Toolkit (`odfdom-java`) — is alive
in 2026 (0.13.0 shipped 2026-01-23) but drags in Xerces + Xalan + Apache Jena + RDF-A +
BouncyCastle as compile dependencies, weighing ~17 MB of JARs before R8 and ships with
known Android dex-conflict history around Xerces. Pulling odfdom for a read-only viewer
violates the APK budget by an order of magnitude and would force us to fight Xerces vs.
the platform's `org.xmlpull.v1` parser.

## Recommendation

**Hand-rolled parser** in `format/odt/` over the platform `XmlPullParser` +
`java.util.zip.ZipInputStream`.

- **APK delta:** **~10 KB** (Kotlin source only; no new third-party dependency).
- **License:** N/A (own code, repo MIT).
- **JDK / minSdk:** API 28+ trivially — XmlPullParser is on Android since API 1, ZIP I/O
  since forever.
- **Coverage targets (v1):** paragraphs, headings, character runs (`text:span` with
  bold/italic/underline/strike pulled from `styles.xml`), lists (`text:list` with bullet
  vs. number distinction), tables (`table:table`), inline images (`draw:frame` →
  `draw:image` resolved against `META-INF/manifest.xml`), hyperlinks (`text:a`).
- **Not v1:** formula objects, embedded charts/OLE (placeholder), tracked-changes markup
  (display final), page styles (we reflow for screen), conditional text, frames /
  textboxes that aren't simple image carriers (placeholder rectangle with the frame's
  caption underneath, see "Spec gotchas").
- **Render target:** the parser emits a `RichTextDocument` (see "Shared with
  DOCX/XLSX?") that the existing Compose `RichTextDocumentView` renders. No
  Compose-specific work is duplicated with DOCX.

## Alternatives considered

### Apache ODF Toolkit (`org.odftoolkit:odfdom-java:0.13.0`) — REJECTED

- **License:** [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) — ✅ allow-listed.
- **Maintenance (2026):** alive. 0.13.0 released 2026-01-23. Repository now lives at
  [`tdf/odftoolkit`](https://github.com/tdf/odftoolkit) under the Document Foundation
  after Apache Incubator retirement (2018-11-27). 146 stars, modest but steady commit
  cadence in 2025–2026.
- **JDK requirement:** **JDK 11+** for 0.13.0. ART on minSdk 28 desugars Java 8 by
  default and Java 11 partially; some odfdom call sites use `String.repeat`,
  `Files.readString` etc. which need desugaring or replacement. Probably fine via
  AGP's core library desugaring, but it's friction we'd own forever.
- **Dependency tree (per `odfdom/pom.xml` on master):**
  - `xerces:xercesImpl` (~1.4 MB) — full JAXP SAX/DOM parser. **Conflicts with
    Android's bundled `org.apache.harmony.xml`/`org.xmlpull` setup**; historical issue
    [ODFTOOLKIT-459](https://issues.apache.org/jira/browse/ODFTOOLKIT-459) tracked
    exactly this dex-build failure. Pinnable via `packagingOptions { exclude
    "META-INF/services/javax.xml.parsers.*" }` but the JAR still ships.
  - `xalan:serializer` (~283 KB).
  - `org.apache.jena:jena-core` (~1.7 MB) — RDF processing for ODF metadata. Pulls
    further transitive Jena / SLF4J / commons-csv. **Massive overkill** for a viewer.
  - `net.rootdev:java-rdfa` — RDF-A semantic metadata.
  - `commons-validator`, `commons-lang3`, `commons-compress` (~1.1 MB), `org.json:json`.
  - `org.bouncycastle:bcprov-jdk18on:1.83` (~8.5 MB) — for ODF encrypted-document
    support, which is not in v1 scope. Already a hard sell APK-wise on its own.
- **Total fat-JAR before R8:** ~17 MB. After aggressive R8 + unused-rules pruning the
  realistic floor is still **8–12 MB** — Jena and BouncyCastle have reflective entry
  points that R8 won't aggressively shrink without large keep-rule investment.
- **API shape:** DOM-style (`OdfTextDocument.loadDocument(...).getContentRoot()`),
  walks `org.w3c.dom.Node`. Comfortable for write-heavy server use; clumsy for
  a viewer that wants to stream + lazily render windowed pages.
- **Verdict:** rejected on APK-size gate. Re-evaluate if pageboy adds ODT *editing* or
  encrypted-ODF support — at that point the dep cost is justified and we'd port over.

### JOpenDocument — REJECTED

- **Upstream:** the original ILM-Informatique project at
  [jopendocument.org](http://www.jopendocument.org) is **dormant** (no release since
  early 2010s). Active fork lineage moved to
  [`andiwand/JOpenDocument`](https://github.com/andiwand/JOpenDocument) → discontinued
  with a successor project `OpenDocument.java`.
- **License:** **GPL-with-classpath-exception / LGPLv3** depending on fork. ❌ not on
  the [oss-licenses.md](oss-licenses.md) allow-list (MIT / Apache-2.0 / BSD-2 /
  BSD-3 / MPL-2.0). Disqualified by the license gate regardless of technical fit.

### AODL — OUT OF SCOPE

.NET only. Listed only to close the loop.

### `OpenDocument.java` (andiwand successor) — REJECTED

- **License:** GPLv3 per repo. ❌ license gate.
- Even ignoring the license, the project is single-maintainer and emphasises
  format-conversion (ODT → HTML) — not a pageboy-shaped fit.

### Hand-roll over `XmlPullParser` — RECOMMENDED (this is the pick)

- **The pitch:** the ODF 1.2 spec
  ([OASIS Part 1](https://docs.oasis-open.org/office/v1.2/os/OpenDocument-v1.2-os-part1.html))
  is large in total but the read-only subset pageboy actually renders is small. A
  prior reference exists in the wild — the LibreOffice Online "lite text" renderers
  and the Calligra office viewers each take this approach. Markor's ODT support
  (Java) is in the same shape and runs comfortably inside an Android editor app.
- **Cost:** roughly a week of focused work for v1 coverage + a fixture-based
  Robolectric test suite. Lower long-term than maintaining a Jena+Xerces+BouncyCastle
  classpath on Android.
- **What we lose:** odfdom's roundtrip fidelity (we'd be terrible at *writing* ODT).
  Pageboy is a *viewer* — we never write ODT, so this isn't a loss.

## APK-size budget

| Approach | Universal APK delta | Per-ABI delta | Notes |
|---|---|---|---|
| Hand-rolled parser (recommended) | **~10 KB** | ~10 KB | pure Kotlin |
| odfdom-java 0.13.0 (R8'd) | **8–12 MB** | same | + ongoing keep-rules maintenance |
| odfdom-java 0.13.0 (no R8) | ~17 MB | same | not realistic for release |

Pageboy's per-format moderate budget is 2–4 MB; the hand-rolled path is well inside it.
odfdom blows the budget by ~3x and would push the universal APK past the 30 MB working
target on its own.

## Performance characteristics

- ODT documents are typically **small** (under a few MB even for 200-page manuscripts);
  most of the time is spent inflating the ZIP entry, not parsing the XML.
- Streaming SAX (`XmlPullParser.START_TAG`/`END_TAG`) means we can emit a
  `Flow<DocumentBlock>` and start showing the first screenful while the rest parses in
  the background — exact pattern markdown will use, so the Compose plumbing already
  exists by Phase D.
- Expected open-to-first-paint latency on a 100-page ODT: **< 200 ms** on the
  reference device. Whole-document parse: **< 1 s** for typical, **< 3 s** for
  pathological 500-page ODTs with heavy formatting.
- No background page-cache warmup needed — unlike PDF, ODT reflows for screen.

## JVM-vs-native

Pure JVM. No NDK component. Same `XmlPullParser` instance the EPUB renderer will use
(EPUB is XHTML in a ZIP; reuses the same `ZipInputStream` plumbing). One inflater per
open document; reset between entry reads.

## minSdk 28 compatibility

- `java.util.zip.ZipInputStream` — since API 1.
- `org.xmlpull.v1.XmlPullParser` — since API 1.
- `kotlinx.coroutines.flow` — works on 28 (already a project dep for whisperboy
  patterns).

No desugaring required for the hand-roll. odfdom would have required core library
desugaring for `java.nio.file.*` paths.

## Maintenance status

- **Hand-roll:** maintained inside this repo. Spec target is ODF 1.2 (ISO 26300-1:2015,
  stable for a decade). New ODF features land slowly and additively — a 2026 hand-roll
  against the v1.2 subset will still parse 2030 ODT files (any newer markup we don't
  recognise is ignored by SAX walking).
- **odfdom:** active under TDF — would survive if we ever needed it.
- **JOpenDocument and forks:** dormant or single-maintainer, license-disqualified anyway.

## Spec gotchas

The three most likely production crash sites, plus the rest of the prescribed list:

1. **Manifest mismatch.** `META-INF/manifest.xml` lists entries that the ZIP doesn't
   contain, or vice versa. Pageboy must tolerate either direction — read content.xml
   first, resolve image references against `manifest.xml` only if needed for MIME, and
   if a referenced image entry is missing render a `[missing image: <path>]`
   placeholder rather than throwing.
2. **Embedded objects (formulas, charts, OLE)** — `<draw:object>` /
   `<draw:object-ole>`. Pageboy renders a 1-line placeholder
   "[embedded object: <type>]" with the surrounding paragraph's style. **Never crash
   on unknown `draw:*` children.** Use a default-skip walker.
3. **Malformed `office:value` on text** — pages produced by spreadsheet → ODT conversion
   pipelines occasionally embed `office:value-type="float"` on a `text:p`; parser must
   ignore unexpected attributes silently.

Other items from the prompt:

- **Tracked changes (`<text:tracked-changes>`):** v1 displays the final-without-markup
  version. The element appears in the `office:body` prefix; we skip its subtree and
  consume the inline `<text:change>`/`<text:change-start>` markers without
  rendering the change bubbles. Document the decision in the renderer KDoc.
- **Page styles:** ignored for screen reading. Pageboy reflows. Document in
  KDoc that this is a deliberate viewer-not-printer choice.
- **Lists with custom bullet / number styles:** parse `<text:list-style>` from
  `styles.xml`; cache per-style `BulletFormat` (bullet char, number format, indent
  level). Fallback to "•" if unknown.
- **Tables:** parse `<table:table>` → `<table:table-row>` → `<table:table-cell>`,
  render in a Compose `Table` composable (the same one DOCX will use). Honour
  `table:number-columns-spanned` / `table:number-rows-spanned`.
- **Embedded images:** read the ZIP entry directly into a `BitmapPainter`; cap pixel
  dimension at 2048px longest side to protect heap. Multi-image documents share an
  LRU bitmap cache keyed on (documentId, zipEntryName).
- **Frames and text boxes:** render frames whose only child is `<draw:image>` (the
  common case — image with caption); for other frame contents render a labelled
  placeholder rectangle. Documented as a v1 limitation.

## Shared with DOCX/XLSX?

**Yes for DOCX, with caveats.** I propose a shared intermediate model:

```kotlin
// in format/common/
sealed interface DocumentBlock {
    data class Paragraph(val runs: List<Run>, val style: ParagraphStyle) : DocumentBlock
    data class Heading(val level: Int, val runs: List<Run>) : DocumentBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<DocumentBlock>>) : DocumentBlock
    data class Table(val rows: List<TableRow>) : DocumentBlock
    data class Image(val source: ImageRef, val caption: String?) : DocumentBlock
    data class Placeholder(val label: String) : DocumentBlock  // OLE / chart / formula
}

data class Run(val text: String, val style: CharStyle, val link: String? = null)
data class CharStyle(
    val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val strike: Boolean = false,
    val color: Long? = null, val fontSizeScale: Float? = null,
)
data class ParagraphStyle(val align: Align = Align.Start, val indentEm: Float = 0f)
class RichTextDocument(val blocks: Flow<DocumentBlock>, val meta: DocumentMeta)
```

**Why this works for ODT + DOCX:** both formats decompose into "paragraphs of styled
runs with occasional table/image/list blocks". The character-run model (`text:span` /
`w:r`) is the same shape. ODF uses style references (`text:style-name="T1"`); DOCX uses
inline `<w:rPr>`. Both flatten into a `CharStyle` cleanly.

**Where it gets leaky** (the honest part):
- **List numbering schemes** differ (ODF uses style chains, DOCX uses
  `numbering.xml`). Resolution happens *before* we hit the shared model — each parser
  produces a flat `BulletFormat` per item. Fine.
- **Page-level concepts** (sections, headers/footers, page breaks) — both formats
  have them, both viewers ignore them for reflow. Symmetrical decision.
- **Comments / tracked changes** — both formats support them, both v1 viewers drop
  them. Re-evaluate together if/when pageboy ever shows comments.
- **Math** (MathML in ODT, OMML in DOCX) — both render as `Placeholder("formula")`
  in v1. The intermediate model doesn't need a math node yet.

**Where it does NOT share with XLSX/ODS:** spreadsheets are a different abstraction
entirely. `SpreadsheetModel` (see `format-ods.md`) is grid-shaped, not block-stream
shaped; no point unifying. The shared layer is `RichTextDocument` only; spreadsheets
get their own sibling type.

**Recommended structure:**

```
format/
├── common/
│   ├── RichTextDocument.kt
│   ├── DocumentBlock.kt
│   ├── SpreadsheetModel.kt        // separate, see format-ods.md
│   └── ZipDocumentSource.kt       // shared ZIP+manifest plumbing
├── odt/
│   └── OdtParser.kt               // XmlPullParser → DocumentBlock flow
├── docx/
│   └── DocxParser.kt              // XmlPullParser → DocumentBlock flow
└── …
```

The Compose renderer `RichTextDocumentView(doc: RichTextDocument)` is written once,
fed by both parsers. **Strong recommendation** — the OOXML agent should land the same
opinion from their side; if both agents converge here, Phase I + Phase K can share UI
without redundant Compose code.

## Phase K — implementation

- [ ] **K.1** Add `format/common/RichTextDocument.kt`, `DocumentBlock.kt`,
      `ZipDocumentSource.kt` skeletons (coordinate with the OOXML agent — whichever
      phase lands first defines these; the other just consumes).
- [ ] **K.2** Add `format/odt/OdtRenderer.kt` implementing `DocumentRenderer` with the
      `DocumentFormat.ODT` enum case. Add the case to `FormatDetector` (extension
      `.odt`, magic-byte sniff: `PK\x03\x04` + `mimetype` first entry equals
      `application/vnd.oasis.opendocument.text`).
- [ ] **K.3** Implement `OdtParser`:
      - Open ZIP via `ZipDocumentSource`.
      - Parse `styles.xml` first → `Map<String, CharStyle>` and
        `Map<String, ParagraphStyle>` and `Map<String, BulletFormat>`.
      - Stream-parse `content.xml` with `XmlPullParser`, emitting
        `Flow<DocumentBlock>`.
      - Handle paragraphs, headings, spans (resolve `text:style-name`), lists,
        tables, images, hyperlinks.
      - Unknown elements: default-skip the subtree.
- [ ] **K.4** Implement `OdtImageLoader` resolving `draw:image xlink:href` against
      the ZIP. Cap at 2048px longest side. LRU cache.
- [ ] **K.5** `RichTextDocumentView` composable (shared with DOCX) — render blocks
      with paging / scroll, honour reader preferences (font, font size, dark theme).
- [ ] **K.6** Robolectric tests: 12 fixtures (`empty.odt`, `lorem.odt`,
      `lists.odt`, `nested-lists.odt`, `table.odt`, `image.odt`, `missing-image.odt`,
      `tracked-changes.odt`, `formula-embedded.odt`, `chart-embedded.odt`,
      `frame-textbox.odt`, `large-500page.odt`). Generate fixtures via LibreOffice
      headless in a `scripts/build-odt-fixtures.sh` (committed shell script,
      regeneration is reproducible).
- [ ] **K.7** Robolectric perf test: open `large-500page.odt`, assert first-block
      latency < 200 ms, full parse < 3 s.
- [ ] **K.8** On-device verify via the AVD loop (`adb install -r` + screenshot) on a
      real ODT (CLAUDE.md mandates UI changes are verified on the running AVD, not
      Robolectric alone). Screenshot the rendered table + list + image.
- [ ] **K.9** Tick checkboxes + add `shipped in change <jj-id>` to this phase
      header and to main.md Phase K when complete.

## Sources

- ODF Toolkit upstream — [tdf/odftoolkit on GitHub](https://github.com/tdf/odftoolkit)
- ODF Toolkit 0.13.0 release notes (2026-01-23) —
  [odftoolkit.org/ReleaseNotes.html](https://odftoolkit.org/ReleaseNotes.html)
- Maven Central — `org.odftoolkit:odfdom-java:0.13.0`
  ([central.sonatype.com](https://central.sonatype.com/artifact/org.odftoolkit/odfdom-java))
- ODF 1.2 OASIS spec —
  [OpenDocument-v1.2-os-part1.html](https://docs.oasis-open.org/office/v1.2/os/OpenDocument-v1.2-os-part1.html)
- Apache JIRA ODFTOOLKIT-459 (historical Xerces-on-Android dex issue) —
  [issues.apache.org/jira/browse/ODFTOOLKIT-459](https://issues.apache.org/jira/browse/ODFTOOLKIT-459)
- SPDX Apache-2.0 — [spdx.org/licenses/Apache-2.0.html](https://spdx.org/licenses/Apache-2.0.html)
- JOpenDocument upstream / discontinuation note —
  [github.com/andiwand/JOpenDocument](https://github.com/andiwand/JOpenDocument)
- "How ODT files are structured" reference —
  [opensource.com/article/22/8/odt-files](https://opensource.com/article/22/8/odt-files)
