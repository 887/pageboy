# pageboy — Markdown renderer plan

## Status: 🟡 RECOMMENDED — review pending

## Recommendation

**`org.commonmark:commonmark` 0.28.0** (BSD-2-Clause) for parsing, with a **hand-rolled Compose `AnnotatedString` renderer** that walks the CommonMark AST and emits Material 3 Expressive typography.

- Parser: `org.commonmark:commonmark:0.28.0` (released 2026-03-31 — verified against the [GitHub releases](https://github.com/commonmark/commonmark-java/releases) and [main-branch commit log](https://github.com/commonmark/commonmark-java/commits/main/)). SPDX: **BSD-2-Clause** (per the [LICENSE.txt](https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt) — full text of the Simplified BSD license, no explicit SPDX header but text is unambiguous).
- GFM extensions (each shipped as a separate artifact, BSD-2-Clause, same project): `commonmark-ext-gfm-tables`, `commonmark-ext-gfm-strikethrough`, `commonmark-ext-task-list-items`, `commonmark-ext-autolink`, `commonmark-ext-footnotes`, `commonmark-ext-yaml-front-matter`, `commonmark-ext-image-attributes`. The 0.28 line also adds an alerts extension (`> [!NOTE]`-style GitHub callouts) in core — useful for note-style Markdown.
- Renderer: own Kotlin code under `format/markdown/render/`, traversing `org.commonmark.node.Node` and emitting `AnnotatedString` + `LazyColumn` blocks for top-level constructs. We control typography, spacing, link styling, code-block presentation. Estimated 600–900 LOC plus theming hooks.
- Code-block syntax highlighting: **deferred to v1.1**. v1 ships code blocks as monospace plain text inside a `Surface`. See "Spec gotchas" for the v1.1 path.
- Math (KaTeX / MathJax): **explicitly out of scope**. v1 renders `$…$` and `$$…$$` literally inside code-style spans. No free Compose-native math renderer surfaced in research.

Rationale: this is the load-bearing decision and is driven by the LSP / SRP commitments in `CLAUDE.md`. Markwon is the most "drop-in" choice but is moribund (last commit master 2021-03, last release 4.6.2 2023-02 — see [Markwon commits](https://github.com/noties/Markwon/commits/master/)), and even when alive it renders to `Spannable` for `TextView`, which fights Compose. Compose Richtext and mikepenz's multiplatform-markdown-renderer are the modern Compose-native choices, but each adds either a wrapped dependency we don't need (Richtext re-implements its own parser surface) or a heavy KMP stack (mikepenz pulls JetBrains Markdown + Highlights + Coil + Extended Spans — fine for a KMP app, overkill for a single-target Android viewer). Owning the renderer is ~700 LOC of arithmetic and gives us total typography control for the M3 Expressive look — pageboy's reader chrome is the surface the user shows other people, so locking the rendering pipeline ourselves is worth it.

## Alternatives considered

### Markwon (`io.noties:markwon-core` + extensions) — **rejected**

- SPDX: Apache-2.0 ([repo LICENSE](https://github.com/noties/Markwon/blob/master/LICENSE)).
- Last release: **v4.6.2, 2023-02-08** ([releases](https://github.com/noties/Markwon/releases)). Last commit to `master`: **2021-03-15** ([commit log](https://github.com/noties/Markwon/commits/master/)). Open issues: 86.
- **Disqualified by the "no commit in two years" red flag in `format-research.md` §7.** Five years without a master-branch commit is well past that threshold.
- Architecturally it would also fight us: Markwon renders into `android.text.Spannable` for `TextView`, so a Compose integration goes through `AndroidView`, defeating M3 Expressive theming. There is a `markwon-compose` artifact but it's also stale.
- It does ship the cleanest answer to code-block highlighting (Prism4j integration, see below), so if we want highlighting in v1.1 we re-evaluate the Markwon-or-its-fork question then.

### Compose Richtext (`com.halilibo.compose-richtext:richtext-commonmark`) — **rejected for v1, candidate for v1.1**

- SPDX: **Apache-2.0** ([central artifact](https://central.sonatype.com/artifact/com.halilibo.compose-richtext/richtext-commonmark)).
- Latest published version: **1.0.0-alpha04** (2025-12-18); most recent commit **2026-05-20** restoring `minSdk` after an AGP 9 upgrade ([commit log](https://github.com/halilozercan/compose-richtext/commits/main/)). Actively maintained.
- README still describes the project as "very experimental" and "roadmap is unclear" ([repo](https://github.com/halilozercan/compose-richtext)). For a v1 reader chrome we want a stable parser.
- Wraps commonmark-java internally already, so picking this is "let someone else's renderer make typography choices" — and we want those choices made ourselves for M3 Expressive consistency with whisperboy/tonearmboy.
- **Reconsider for v1.1** if our hand-rolled renderer accumulates bugs around edge-case Markdown (deeply nested lists, image-with-attributes, raw-HTML passthrough).

### mikepenz/multiplatform-markdown-renderer — **rejected**

- SPDX: **Apache-2.0** ([repo](https://github.com/mikepenz/multiplatform-markdown-renderer)). Latest **v0.41.0 (2026-05-17)**. Healthy.
- Dependencies: **JetBrains Markdown** (Apache-2.0, [repo](https://github.com/JetBrains/markdown)) for parsing + Compose Multiplatform + Extended Spans + Highlights (for code) + optional Coil2/3 (for images). The transitive license story is clean (all Apache-2.0).
- **Rejected because the dependency tree is sized for a KMP app, not a single-target Android reader.** Pulling Compose Multiplatform's surface into an Android-only build to render Markdown is a poor ratio. APK delta estimated 1.5–2.5 MB minified — over our < 1 MB budget.
- It would be the right call if pageboy ever ported to desktop/iOS. It is not the right call for v1 Android.

### flexmark-java — **rejected**

- SPDX: **BSD-2-Clause** ([LICENSE](https://github.com/vsch/flexmark-java/blob/master/LICENSE.txt)). License is fine.
- Last release **0.64.8, 2023-05-23**. No master-branch activity since. Hits the two-year-no-commit red flag.
- Heavier than commonmark-java (broader pegdown/kramdown emulation we don't need). APK delta would be ~1–1.5 MB on top of base.
- The one place flexmark wins (PDF/DOCX conversion modules) we don't need — pageboy is a viewer, not a converter.

### Hand-rolled CommonMark parser — **rejected**

- LOC estimate: a *correct* CommonMark parser is 3–5k LOC (the spec has 600+ test cases including delimiter-stretching edge cases, link-reference precedence, and HTML-block boundary conditions). flexmark-java cites being written specifically because hand-rolled regex parsers fail spec compliance.
- For an Android **viewer** that has to round-trip user content faithfully (especially Obsidian Vault content with idiomatic Markdown), we do not get to ship a parser with known spec failures.
- The hand-roll only makes sense for the *renderer* side, where we control the typography contract and have a `Node` AST to walk.

## License gate summary

| Candidate | SPDX | On allowlist? |
|---|---|---|
| commonmark-java 0.28.x | BSD-2-Clause | ✅ |
| commonmark extensions (gfm-tables, strikethrough, task-list, autolink, footnotes, yaml-front-matter, image-attributes) | BSD-2-Clause | ✅ |
| Markwon 4.6.2 | Apache-2.0 | ✅ (but disqualified on maintenance) |
| Compose Richtext 1.0.0-alpha04 | Apache-2.0 | ✅ |
| mikepenz multiplatform-markdown-renderer 0.41.0 | Apache-2.0 | ✅ |
| JetBrains/markdown (transitive via mikepenz) | Apache-2.0 | ✅ |
| flexmark-java 0.64.8 | BSD-2-Clause | ✅ (but disqualified on maintenance) |
| Prism4j (highlighting, v1.1) | Apache-2.0 | ✅ (but upstream unmaintained since 2019; fork at tehcneko/Prism4j) |
| SnipMeDev/Highlights | Apache-2.0 | ✅ |
| SnipMeDev/KodeView | Apache-2.0 | ✅ |

All allowlist-compatible; no MPL / EPL / GPL / AGPL hits.

## APK-size budget

Target: **< 1 MB Markdown total APK delta** (per `format-research.md`'s "small" guidance, with explicit headroom for one optional highlighting lib in v1.1).

Estimate with R8 + ProGuard rules enabled (R8 will tree-shake unused parser nodes):

| Component | Unminified JAR | Minified APK contribution (estimate) |
|---|---|---|
| commonmark-core 0.28.0 | ~110 KB | ~70 KB |
| ext-gfm-tables | ~25 KB | ~15 KB |
| ext-gfm-strikethrough | ~10 KB | ~6 KB |
| ext-task-list-items | ~15 KB | ~10 KB |
| ext-autolink | ~20 KB | ~12 KB |
| ext-footnotes | ~30 KB | ~18 KB |
| ext-yaml-front-matter | ~10 KB | ~6 KB |
| ext-image-attributes | ~10 KB | ~6 KB |
| nadakov/snakeyaml-engine (transitive of yaml-front-matter) | ~250 KB | ~150 KB (or 0 if we drop yaml-fm and parse frontmatter ourselves) |
| Own renderer code | n/a | ~30 KB DEX |
| **Total (v1)** | | **~175 KB** (without snakeyaml) — **~325 KB** (with snakeyaml) |

Decision: **skip `commonmark-ext-yaml-front-matter`** in v1 — write a 40-LOC `--- … ---` sniffer that hands the front-matter block to `kotlinx.serialization` if we ever need to consume it. Saves the snakeyaml transitive. Total Markdown APK delta lands ~175 KB minified.

If we add Prism4j later for code-block highlighting (v1.1), add ~250 KB for the parser core + ~30 KB per bundled language grammar. Cap at 6 languages (kotlin, java, javascript, python, bash, json) → +250 + 180 = **+430 KB**, putting the renderer total at ~600 KB — still inside the 1 MB budget.

Per-ABI breakdown: pure JVM, no per-ABI cost.

## JVM-vs-native

**Pure JVM.** All candidates run on ART; no `.so` files; no per-ABI multiplier. This is the entire reason Markdown is a "small plan" — there's no native renderer story to negotiate.

## Performance characteristics

commonmark-java's project README cites **10–20× faster than pegdown** and is the reference performance benchmark for JVM Markdown parsers ([repo README](https://github.com/commonmark/commonmark-java)). For pageboy's expected document sizes (Obsidian-vault notes, README files, blog drafts — typically < 50 KB of source) parsing is sub-millisecond and the relevant cost is the Compose render pass.

Rendering strategy:
- Parse once on document open (on `Dispatchers.Default`), cache the `Node` AST on the `DocumentRenderer` instance.
- Convert top-level block nodes (`Heading`, `Paragraph`, `ListBlock`, `BlockQuote`, `FencedCodeBlock`, `ThematicBreak`, `TableBlock`) to a `List<MarkdownBlock>` sealed-type list.
- Render via `LazyColumn` keyed by node index, so the recomposition cost scales with viewport, not document length.
- For inline runs (`Emphasis`, `StrongEmphasis`, `Code`, `Link`, `Image`), fold into an `AnnotatedString` per paragraph — this is the path Compose is optimized for.

Expected open-to-first-paint for a 50 KB Markdown file: **< 50 ms** on the AVD's emulated mid-range hardware.

No streaming/paged API needed — Markdown documents are not large enough to require windowing at the parse level. The render-side `LazyColumn` is the windowing primitive.

## Android-version compatibility

commonmark-java's `commonmark-android-test` module pins **`minSdk` 19** ([android-test README](https://github.com/commonmark/commonmark-java/blob/main/commonmark-android-test/README.md)). pageboy's `minSdk` is 28 — well above the floor. No shim needed.

Pure Java 8 byte-code; no `java.time`, `java.util.stream`, or `java.lang.reflect` calls that would force core library desugaring beyond what AGP enables by default. Safe.

## Maintenance status

**commonmark-java:** robust. Latest release 0.28.0 on 2026-03-31; main-branch commits within the last month at research time (last commit 2026-05-04 — see [commits](https://github.com/commonmark/commonmark-java/commits/main/)). Maintainer: `robinst`. The project is the de-facto JVM CommonMark implementation and is referenced by spec.commonmark.org.

**Markwon:** dead. Last master commit 2021-03-15. Last release 2023-02. 86 open issues. Do not rely on for v1.

**Compose Richtext:** alive. Most recent commit 2026-05-20. Self-described as experimental.

**mikepenz multiplatform-markdown-renderer:** very alive. Latest 0.41.0 on 2026-05-17. Active maintainer.

**flexmark-java:** stalled. Last release May 2023.

**Prism4j (upstream noties/Prism4j):** unmaintained since 2019. The `tehcneko/Prism4j` fork has continued activity and is what we'd take if we add highlighting in v1.1. Both Apache-2.0.

## Spec gotchas

The three places a wrong-shaped Markdown document is most likely to break the v1 renderer in production:

### G1. GFM extensions matrix

The biggest production hazard is silently ignoring well-known GFM constructs. The matrix:

| Construct | Required extension | Bundled in v1? |
|---|---|---|
| Tables | `commonmark-ext-gfm-tables` | ✅ |
| Strikethrough | `commonmark-ext-gfm-strikethrough` | ✅ |
| Task lists (`- [ ]` / `- [x]`) | `commonmark-ext-task-list-items` | ✅ |
| Autolinks (bare URLs) | `commonmark-ext-autolink` | ✅ |
| Footnotes (`[^1]`) | `commonmark-ext-footnotes` | ✅ |
| Alerts (`> [!NOTE]`) | core (0.28+) | ✅ — free |
| Image dimensions / attrs (`{width=200}`) | `commonmark-ext-image-attributes` | ✅ |
| YAML front-matter | DIY 40-LOC sniffer | ✅ (no extension dep) |
| Wikilinks (`[[Note]]`) | none upstream — Obsidian dialect | ❌ v1 renders as literal `[[…]]`; v1.2 if user demand |
| Mermaid / PlantUML code blocks | none — would require JS engine | ❌ render as `` ``` `` code |
| KaTeX / MathJax (`$…$`) | none free for Android | ❌ render as literal `$…$` in code style |

The Obsidian-vault use case (`format-research.md` cites Obsidian as a target document collection) means Wikilinks will be hit. We accept the literal fallback in v1 and consider a 30-LOC pre-parse pass in v1.1 that rewrites `[[Note]]` → `[Note](note://Note)` before handing to commonmark.

### G2. Code-block syntax highlighting

Out of scope in v1 — code blocks render as monospace inside a `Surface(color = surfaceContainer)`. The v1.1 path is:

- **Option A (preferred):** add `tehcneko/Prism4j` (Apache-2.0 fork of `noties/Prism4j`) for tokenization, write our own Compose theme adapter that maps Prism token types to Material 3 Expressive on-surface colors. ~250 KB + ~30 KB per bundled language.
- **Option B:** `SnipMeDev/Highlights` (Apache-2.0, KMP, [repo](https://github.com/SnipMeDev/Highlights)). Compose-friendly out of the box but heavier; pulls KMP runtime overhead.
- **Option C:** ship without highlighting and call it a feature ("plain code, distraction-free").

We commit to Option A for v1.1 only if user demand surfaces; the v1 shipping default is Option C.

### G3. Raw HTML blocks

CommonMark permits raw HTML (`<div>…</div>`, `<details>…</details>`) inside Markdown. commonmark-java parses these into `HtmlBlock` / `HtmlInline` nodes. **We do not render arbitrary HTML in v1 — that road leads to WebView and security review.** The renderer treats unknown HTML as either:
- For known-safe inline tags (`<br>`, `<sub>`, `<sup>`, `<kbd>`, `<mark>`): map to `AnnotatedString` `SpanStyle`.
- For `<details>…</details>`: render as a collapsible Material `ListItem` with the inner content recursively re-parsed.
- Everything else: render the raw HTML source as monospace within a `Surface(color = errorContainer)` so the user knows something was skipped.

This is the SOLID-correct degradation path — `RenderResult` always renders *something*, never throws.

## Phase D — implementation

Sub-step checkboxes ready for incorporation into `main.md` Phase D.

- [ ] **D.1** Add `commonmark-core` 0.28.0 and the six bundled extensions (gfm-tables, gfm-strikethrough, task-list-items, autolink, footnotes, image-attributes) to `app/build.gradle.kts`. Exclude the `yaml-front-matter` extension. Add a Licensee allow rule for BSD-2-Clause.
- [ ] **D.2** Stand up `format/markdown/MarkdownRenderer.kt` implementing `DocumentRenderer`. `open()` reads the SAF `InputStream` into a `String`, parses to `org.commonmark.node.Node`, holds the AST on the instance. `close()` clears the reference.
- [ ] **D.3** Sealed-type tree under `format/markdown/model/MarkdownBlock.kt`: `Heading`, `Paragraph(text: AnnotatedString)`, `BulletList`, `OrderedList`, `TaskListItem`, `BlockQuote`, `FencedCodeBlock(lang, content)`, `Alert(kind, content)`, `ThematicBreak`, `Table(headers, rows)`, `Image(url, alt, dims?)`, `RawHtmlBlock(content)`, `HtmlFallback(content)`.
- [ ] **D.4** AST-to-sealed-tree visitor in `format/markdown/parse/AstToBlocks.kt`. Walks top-level nodes once on parse. Inline nodes fold into per-paragraph `AnnotatedString` via `format/markdown/parse/InlineFolder.kt`.
- [ ] **D.5** Compose renderer in `format/markdown/ui/MarkdownReader.kt` — `LazyColumn` keyed by block index, one `@Composable` per `MarkdownBlock` subtype. M3 Expressive typography (`MaterialTheme.typography.{headlineLarge..bodySmall}`).
- [ ] **D.6** 40-LOC YAML front-matter sniffer in `format/markdown/parse/FrontMatter.kt` — strips a leading `^---\n…\n---\n` block before handing the body to commonmark-java. Returns the raw front-matter as `String?` for downstream consumption (not used in v1 UI; available for v1.1 Obsidian metadata reading).
- [ ] **D.7** Raw-HTML degradation: implement the G3 strategy. Known-safe inline tags (`<br>`, `<sub>`, `<sup>`, `<kbd>`, `<mark>`) map to `SpanStyle`. `<details>` maps to a Material expand/collapse. Other HTML renders in an error-container `Surface` with the raw source visible.
- [ ] **D.8** Robolectric tests in `format/markdown/MarkdownRendererTest.kt`. Spec-compliance set: 6 hand-picked CommonMark spec cases (heading, list, code-fence, link, image, blockquote-with-list). GFM set: tables, strikethrough, task-list, autolink, footnote, alert, image-with-attrs. Edge cases: empty file, BOM-prefixed file, CRLF line endings, unicode characters in headings, 5 MB Markdown stress file (parse < 200 ms).
- [ ] **D.9** Smoke test on AVD `medium_phone`: open `README.md` from `/sdcard/Documents/pageboy-test/`, screenshot the reader, verify heading hierarchy, list rendering, code-block monospace, and link tap-handling. Compare against the M3 Expressive design tokens in `m3-expressive.md`.
- [ ] **D.10** Document the v1.1 syntax-highlighting decision tree in `format/markdown/render/code/CodeBlockRenderer.kt` (just a header comment explaining the Prism4j-vs-Highlights tradeoff so future-us doesn't re-research). v1 implementation: monospace inside `surfaceContainer`.
