# pageboy — seed prompt

## Status: ✅ DONE (this plan is a verbatim preservation of the user's original brief; no execution required)

## Family context (for fresh agents)

Pageboy is the fifth member of a family of Android apps named `<object-from-the-medium>boy`. The siblings, all under `/home/laragana/workspace/`:

- **[tonearmboy](https://github.com/887/tonearmboy)** — music player. The eldest sibling; the one whose UI shell and build pipeline the rest copy.
- **[whisperboy](https://github.com/887/whisperboy)** — audiobook player. Pageboy's closest sibling — same intellectual content (books, documents), different sensory modality (read vs listen).
- **[shutterboy](https://github.com/887/shutterboy)** — photo gallery.
- **[strictlykeptboy](https://github.com/887/strictlykeptboy)** — git-backed calendar and todos. Naming peer (the triple-loaded "strictly kept" → "strictly kept boy" → "the boy who is strictly kept").
- **pageboy** — this app. Documents. The textual sibling of whisperboy.

The five apps share a toolchain (Kotlin + Compose + Material 3 Expressive + the `android` CLI + Robolectric + mobile-mcp + Obtainium release path), a set of architectural decisions (SAF-only storage, single-module, hand-rolled `AppGraph`, narrow data interfaces), and a visual register (vertical navigation rail + top bar + Material 3 Expressive surface tiers + circular coloured row-icon avatars in settings). They do **not** share code — see each app's `docs/plans/sharing-analysis.md` for why.

A new agent walking into this repo cold should read, in order: this file (the original brief), then `README.md` (what the app is for users), then `CLAUDE.md` (what the app is for agents), then `docs/plans/main.md` (the phased plan), then whichever per-format plan is relevant to the task at hand.

## Original brief

The brief below is the user's verbatim wording from **2026-05-24**, preserved as the source of truth for what pageboy is meant to be. When a future decision drifts from it, that's the decision that needs justifying — not the brief.

> E-reader / document viewer — MARKDOWN+ DOCX/XLSX + ODT + txt + PDF ( also marking things in pdf/signing) + EPUB support would complement the audiobook player really nicely (same content, different format)

## What this brief commits us to

Unpacking the brief into the locked decisions the rest of the planning derives from:

- **Format coverage as a v1 gate, not a roadmap.** Every format the brief names — Markdown, DOCX, XLSX, ODT, TXT, PDF, EPUB — is in scope for v1. ODS is in scope as a sibling of ODT/XLSX because excluding the only OpenDocument spreadsheet from an app that supports the OpenDocument Text format would be inconsistent. No other formats land in v1 (no `.rtf`, no `.djvu`, no `.azw`, no `.fb2`, no `.cbz`); those can become Phase P+ if a real user case shows up.
- **PDF signing is part of the brief, not a stretch goal.** "Also marking things in pdf/signing" is one of the seven explicit format requirements. The research plan splits this into annotation (Phase G) and signing (Phase H, with both freehand-stamp and digital-signature surfaces — both worth investigating).
- **"Complement the audiobook player" defines the sibling relationship.** Same intellectual content (books, manuscripts, documents), different modality. Codebases stay separate; the shared bit is the stack and the visual register. See `sharing-analysis.md`.
- **No editing surface mentioned, so no editing surface ships in v1.** Pageboy reads, annotates, signs. It does not author. (Edit a Markdown file in any editor that produces Markdown; open it in pageboy to read.)

## What this brief intentionally leaves open

Library selection per format. Whether the PDF renderer is Pdfium, MuPDF, `android.graphics.pdf.PdfRenderer`, `PdfBox-Android`, or a hybrid. Whether DOCX rendering goes through Apache POI on Android, a hand-rolled OOXML parser, or a small library like `xdocreport`. Whether EPUB content renders in a Compose-native flow or an embedded WebView. Whether digital signatures use Bouncy Castle / iText-licensable-fork / a Conscrypt-based hand-roll.

These are the explicit questions for the next-round research agents. See [`format-research.md`](format-research.md) for the prompt that fans them out.
