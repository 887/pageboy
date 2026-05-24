# pageboy — format research

## Status: 🟡 SEED — this is the prompt for the next round of research agents. Each agent produces one `docs/plans/format-<name>.md` with their findings + recommendation, then the relevant main.md phase ticks open.

## How this round works

For each format pageboy supports, fan out **one research agent per format**. Each agent works in a worktree, owns one `docs/plans/format-<name>.md`, and reports back with a library recommendation + dependency-tree analysis + a sub-step checklist for the corresponding main.md phase. The output is a *plan*, not code — implementation lands in a subsequent phase once the plan is reviewed.

Formats to research, one plan each:

- `format-markdown.md` → unlocks main.md Phase D.
- `format-txt.md` → unlocks main.md Phase E.
- `format-pdf.md` → unlocks main.md Phase F (view) + Phase G (annotate) + Phase H (sign). **This is the biggest single research plan in pageboy — it gets a long file.** The annotation persistence question (overlay table vs PDF 1.7 annotation dictionaries vs both) and the digital-signature question (PKCS#7 / CAdES / PAdES, which crypto library, which Android key-store affordances in 2026) are both load-bearing and warrant dedicated sections.
- `format-docx.md` → unlocks main.md Phase I.
- `format-xlsx.md` → unlocks main.md Phase J.
- `format-odt.md` → unlocks main.md Phase K. Should also opine on whether ODT + DOCX share an intermediate model.
- `format-ods.md` → unlocks main.md Phase L. Should opine on shared plumbing with XLSX.
- `format-epub.md` → unlocks main.md Phase M.

Eight plans total. Markdown, TXT, ODT, ODS, EPUB are each likely a small plan (a single library, light dependencies, a few edge cases). DOCX + XLSX + PDF are likely larger.

## The questions each plan must answer

For every format, the research agent answers — **with citations**:

### 1. Candidate libraries

Identify the top **two to four** open-source Android-compatible libraries for this format. For each candidate, note: name, maintainer, current version (as of the research date), last release date, public GitHub repo URL. Briefly describe what the library does and does not cover. **Include at least one no-dependency option** (hand-rolled Compose / `android.graphics` / standard library only) if such a thing is even theoretically achievable for this format, even if the recommendation is to use a real library — knowing the no-dep baseline is the price of being able to defend the library choice.

### 2. License compatibility with MIT

For each candidate, state the SPDX id. **MIT, Apache-2.0, BSD-2-Clause, and BSD-3-Clause are the allowlist.** Anything else is disqualifying for v1 (see `oss-licenses.md`). Pay special attention to:

- **iText** — historically AGPL with a paid commercial license; AGPL is disqualifying. Investigate forks (OpenPDF, iText 5 free branch) and document their licenses exactly.
- **Apache POI** — Apache-2.0, but pulls in heavy XML stack; check what survives R8 shrinking on Android.
- **MuPDF** — AGPL for the source; commercial license offered separately. If recommending MuPDF, the recommendation must be a commercial-license carve-out or a hard "no" with a fallback.
- **PDFium** — BSD-3-Clause (Chromium's PDF renderer). Likely the recommendation for the renderer side, but the Android bindings landscape (`AndroidPdfViewer` (Apache-2.0 wrapper), `Pdfium-Android` (Apache-2.0 wrapper)) needs concrete pinning.
- **Bouncy Castle** — MIT-style permissive — fine, but APK-size impact is meaningful.

### 3. APK-size impact

For each candidate, estimate the APK delta with R8 / ProGuard enabled. The expected ranges:

- Markdown / TXT — small (< 200 KB each).
- EPUB — moderate (1–3 MB).
- DOCX / ODT / XLSX / ODS — moderate to large depending on whether POI-on-Android comes into play.
- PDF — large. Native renderers ship per-ABI; estimate per ABI (armeabi-v7a, arm64-v8a, x86_64). Document whether the APK ships split-per-ABI or universal.

Pageboy's APK-size goal: **< 30 MB universal-debug, < 20 MB per-ABI release.** Hard ceiling: 50 MB universal (Obtainium-friendly; some users on metered connections balk past that).

### 4. JVM-vs-native

Is the library pure JVM (parses on the Dalvik/ART side), or does it ship native code? Native renderers (Pdfium, MuPDF) are typically faster and lower-RAM but ship per-ABI binaries. Pure-JVM libraries are easier to bundle and test under Robolectric but can be slow on large documents.

### 5. Performance characteristics

For PDF + DOCX + EPUB in particular, what's the latency for opening a 500-page document? What's the per-page render latency? Does the library expose a streaming / paged API, or must it parse the whole document up front?

### 6. Android-version compatibility

Pageboy's `minSdk` is **28** (Android 9). Confirm the candidate supports it. Some libraries (`android.graphics.pdf.PdfRenderer` for the system PDF renderer) have changed surface meaningfully across API levels — note any compatibility shims required.

### 7. Maintenance status

How active is the upstream? Last commit, last release, open-issue backlog, recent security advisories. **A library that hasn't seen a commit in two years is a red flag**, especially for parsers that need to track format spec updates and CVE responses.

### 8. Specific format-spec gotchas

Per format, name the three most likely places a wrong-shaped document will crash the renderer in production. Examples (not exhaustive):

- **Markdown:** GFM extensions (tables, task lists, footnotes, alerts) — which library covers which? Code-block language detection for syntax highlighting. Math (KaTeX / MathJax) — explicitly out of scope unless the research surfaces a free shipping option.
- **PDF:** encrypted documents (password prompt UX), malformed objects, AcroForm fields, embedded JavaScript (must be ignored, not executed).
- **DOCX:** drawing-XML embedded vector graphics, embedded OLE objects (must be displayed as a placeholder, not crashed on), `mc:AlternateContent` MCE blocks.
- **XLSX:** shared strings table, frozen panes, merged cells, named ranges, formula-cached-value-vs-formula precedence.
- **EPUB 2 vs 3:** spine reading-order vs `<nav>` ToC, the `epub:type` attribute set, fixed-layout EPUBs (out of scope unless trivial), font obfuscation.

## What each plan looks like

The output `docs/plans/format-<name>.md` follows the family plan-file convention:

```
# pageboy — <format> renderer plan

## Status: 🟡 RECOMMENDED — review pending

## Recommendation

<library X version Y, license SPDX, APK delta Z>

## Alternatives considered

<library A — rejected because …>
<library B — rejected because …>
<no-dep hand-roll — rejected because … (or accepted if the recommendation IS no-dep)>

## APK-size budget

<…>

## Performance characteristics

<…>

## Spec gotchas

<…>

## Phase <X> — implementation

- [ ] **X.1** <…>
- [ ] **X.2** <…>
…
```

The "Phase X — implementation" section is the missing sub-step content for the corresponding stub phase in `main.md`. Once the research plan is reviewed and accepted, the main.md phase header gets a "depends on `format-<name>.md`" note replaced with "sub-steps owned by `format-<name>.md`", and implementation can start.

## Reading list before fanning out

Each research agent should at least skim:

- The `README.md` of every candidate library to understand the public API surface.
- The Android-skills MCP — `android skills list --long | grep -i <format>` may surface official Google patterns (e.g. `android.graphics.pdf` patterns).
- Whichever open-source Android reader handles this format in production — Markor (Markdown), Librera (EPUB + PDF), MuPDF viewer (PDF). Read how they wire the renderer in. Do not vendor their code; pageboy is clean-room.
- The relevant entry in this repo's `CLAUDE.md` "Per-format renderer modules" section so the `DocumentRenderer` interface contract is clear before the agent designs around it.

## What this seed does NOT decide

It does not decide the libraries. It does not decide the APK-size budget per format (just the universal ceiling). It does not decide whether ODT / ODS share parsers with their OOXML counterparts. It does not decide whether the EPUB renderer goes through a WebView or a Compose-native flow. **All of those are explicit research outputs.** The seed only locks the structure of the research and the gate criteria.
