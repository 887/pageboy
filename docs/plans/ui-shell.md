# pageboy — UI shell

## Status: ✅ DONE — applied in Phase A (vertical rail, top bar, settings catalog DSL, AboutScreen, LicensesScreen all shipped)

## The rule

**The UI shell is locked. Adapt content, not shape.** Research agents in the next round must not redesign the navigation rail, the top bar, the settings catalog DSL, the About screen, or the Licenses screen. Pageboy inherits these from tonearmboy directly, the same way whisperboy and shutterboy inherit them. The visual register across the family is part of the family-resemblance — five apps that look and feel like siblings — and re-deciding it per app would burn weeks of research time on a question the user has already answered.

When in doubt: open tonearmboy, find the shape, port it across with `tonearmboy` → `pageboy` substitutions, swap the per-app content (settings entries, About body, navigation destinations), ship.

## What gets copied (with file pointers)

All paths below are inside `/home/laragana/workspace/tonearmboy/app/src/main/java/com/eight87/tonearmboy/`. Open the file in tonearmboy, port the shape to pageboy under the equivalent `com/eight87/pageboy/` path.

### Vertical navigation rail + top bar

Tonearmboy's `MainActivity` + `TonearmboyApp.kt` (the latter at the package root, alongside `AppGraph.kt`) compose a vertical navigation rail on the left, a top app bar across the top, and a content host below. Pageboy mirrors this: rail with the top-level destinations, top bar with search + overflow, content host renders whichever screen the back-stack lands on.

**Reference files in tonearmboy:**

- `MainActivity.kt` — the activity, which wires `setContent { TonearmboyTheme { TonearmboyApp(graph) } }`.
- `TonearmboyApp.kt` — the root composable that mounts the rail + bar + back-stack-driven content. **The shape of this file is the shape pageboy's `PageboyApp.kt` should take.**
- `ui/` — the screen composables wired into the back stack.

**Top-level destinations in pageboy (tentative, finalize during Phase A.3):**

- **Library** — the document grid / list, default landing.
- **Recents** — recently opened documents (deep-link landings + library taps both feed this).
- **Pinned** — user-pinned documents, hand-curated.
- **Settings** — the catalog screen.

The names finalize when the format work shows what surfaces really matter; the *count* of rail destinations should stay at four (rail crowding past five degrades the family's visual register).

### Material 3 Expressive theming

See [`m3-expressive.md`](m3-expressive.md). Apply during main.md A.2.

**Reference files in tonearmboy:**

- `ui/settings/BaseThemeUi.kt` — the M3E `MaterialExpressiveTheme(...)` wrapper + the `CategoryAccent` data class + the per-row coloured circular avatar composable.
- `ui/settings/ThemePreference.kt` — the light / dark / follow-system + dynamic-color toggle persistence.
- `data/theme/` (whisperboy mirror — tonearmboy may live differently) — the DataStore-backed `ThemeSettings` facet.

Pageboy keeps the same six-ish category accents tonearmboy ships. **Auto-derive `accent` from row `id` at the `SettingsRow` layer** (gotcha #3 from `m3-expressive.md`) so hand-rolled screens (About, Licenses, any one-off setting page) inherit colour without per-call-site work.

### Settings screen architecture — catalog DSL

Tonearmboy's settings screen is **not** a hand-rolled `LazyColumn` of `SettingsRow(...)` calls; it's a **catalog DSL** that declares sections + entries declaratively, plus a renderer that walks the catalog and lays out the rows. This shape exists because the catalog also feeds the **search** screen (`SettingsSearchScreen.kt`) — every setting in the app is searchable by title or subtitle.

**Reference files in tonearmboy** (under `ui/settings/catalog/`):

- `SettingsCardDsl.kt` — the DSL for declaring `settingsCard { row(...) row(...) }` blocks.
- `SettingsCatalog.kt` — the top-level catalog assembling every section.
- `SettingsPagesRender.kt` — the renderer that walks the catalog and lays out the M3 surface-tier grouped cards.
- `SettingsSearchScreen.kt` — the search surface over the same catalog.
- `catalog/sections/*Entries.kt` — one file per section (Appearance, Library, Playback, etc.). Each file exposes a single function returning a list of entries.

Pageboy ports the same five files (`SettingsCardDsl.kt`, `SettingsCatalog.kt`, `SettingsPagesRender.kt`, `SettingsSearchScreen.kt`, plus `catalog/sections/*Entries.kt`) with pageboy-specific entries. The section breakdown for pageboy (tentative):

- **Appearance** — theme, font (size, family, line-height), reading mode (paged / continuous / two-page-landscape).
- **Library** — folder roots, scan filters (which formats to skip), default sort, default view (grid / list).
- **Reader** — per-format defaults (PDF page-fit mode, EPUB CSS injection on/off, Markdown rendering pin, code-block theme).
- **Annotations** — default highlight colour, default ink colour, signature management (sub-screen).
- **Signing** — signature stamps catalog (sub-screen), digital signature certificate management (sub-screen).
- **About** — version, build hash, MIT license, GitHub link, open-source licenses sub-page.

Section accents pick from the same `CategoryAccent` palette tonearmboy hand-picks; assign during Phase A.4.

### About screen

Tonearmboy's `ui/settings/AboutScreen.kt`. Pageboy's `ui/settings/AboutScreen.kt` is the same shape with pageboy-specific copy:

- App icon + name + version + build hash.
- Source link to `https://github.com/887/pageboy`.
- MIT license link.
- **Sibling credit** — clean-room rewrite, MIT, no fork, names the open-source design-space references (Markor, Librera, MuPDF viewer, Collabora Office) the way tonearmboy / whisperboy credit Auxio / Voice respectively.
- Open-source licenses link → `LicensesScreen`.

### Licenses screen

See [`oss-licenses.md`](oss-licenses.md) for the full plan. The composable shape (`LazyColumn` of `SettingsCard`s, tap → `AlertDialog` with monospaced license text) ports verbatim from tonearmboy's `ui/settings/LicensesScreen.kt`.

### Easter egg

Tonearmboy ships a triple-tap easter egg on the launcher icon (see `ui/settings/EasterEggController.kt`). Pageboy ships the same surface — the user will supply an icon and an easter-egg image at a later date. **Leave a placeholder** at `ui/settings/EasterEggController.kt` + a "TBD: easter egg image" slot in the assets directory. The triple-tap detector logic and the dialog-open machinery should be functional at A.5; only the asset is missing.

## What the next-round research agents do NOT touch

- The rail composition (count of destinations, vertical orientation, position on the left).
- The top app bar layout (icon density, overflow shape, the height of the resting top bar).
- The settings catalog DSL shape (declarative `settingsCard { row(...) }` syntax, per-section file split, search integration).
- The Material 3 Expressive theme entry point (the `PageboyTheme` composable, the `MaterialExpressiveTheme` wrapper).
- The About screen layout (logo position, link order, sibling-credit section placement).
- The Licenses screen layout (`LazyColumn` of cards, tap → dialog).

**What they DO touch:** the *content* — which settings live in the Appearance section, which renderer hooks the Reader section exposes, which signature affordances the Signing section needs. Those are format-research outputs, not UI-shell decisions.

## Why this is locked

User's verbatim instruction at seed time (2026-05-24): *"look at tonearmboy the colorful material 3e and also the open source licenses etc should all be in the same style without me having to reprompt you 50x."*

Translation: re-deciding the shell per app is exactly what the user is telling agents not to do. The shell is the *product brand* across the five apps; format research is the *product content* per app. Don't confuse the two.
