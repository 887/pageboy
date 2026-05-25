# pageboy — UI parity sweep vs tonearmboy

## Status: ✅ DONE

Four parallel sweep agents compared tonearmboy's shipped UI against pageboy's shipped UI. The user tested on a real phone and the gaps are severe. This plan enumerates every difference, grouped by priority.

## The root problem

**Pageboy's nav pattern is architecturally wrong.** The Phase A agent used standard M3 `NavigationRail` + a horizontal `TabRow` for the library tabs. Tonearmboy uses a **completely custom** vertical rail where the tabs themselves ARE the rail items — rotated -90° text labels with a 2dp accent stripe, no horizontal TabRow at all. Pageboy's M3 `NavigationRail` + horizontal `TabRow` is a design departure, not a copy.

## What the user sees on their phone (from the screenshot)

1. Horizontal text labels in the rail ("Library" / "Recents" / "Pinned" / "Settings") — not vertical rotated text
2. "Recents" tab text wraps to TWO lines in the TabRow
3. No visible surface tinting from the seed color
4. No Settings gear icon in the top bar (only Search + Sort)
5. Plain/monochrome appearance — nothing looks like tonearmboy's tinted chrome
6. The nav rail items look like a plain text menu, not the sleek vertical tab system tonearmboy ships

## Differences enumerated

### Priority 1 — Critical visual breaks (fix immediately)

| # | Component | tonearmboy | pageboy | Fix |
| --- | --- | --- | --- | --- |
| P1.1 | **Tab text wrapping** | N/A (no horizontal TabRow) | Tab text wraps to 2 lines on phone ("Recents" → 2 lines) | Add `maxLines = 1, overflow = TextOverflow.Ellipsis` to every Tab `Text()` in LibraryScreen.kt line 219 |
| P1.2 | **Nav rail icon state** | Filled icon when selected, outlined when not (M3 convention) | Recents uses `Icons.Outlined.History` always; other items mix filled/outlined inconsistently | Pass both filled + outlined icons to RailItem; select per `selected` state |
| P1.3 | **Nav rail theme tinting** | Rail items tint via `colorScheme.primary` / `onSurface` / `onSurfaceVariant` | `NavigationRailItem` uses default M3 colors (not explicitly wired → subtle tinting only) | Add explicit `colors = NavigationRailItemDefaults.colors(selectedIconColor = ..., indicatorColor = ...)` |
| P1.4 | **Tab indicator tinting** | N/A (no TabRow) | TabRow uses default M3 indicator (not wired to seed color) | Add `containerColor` + `contentColor` to TabRow from `MaterialTheme.colorScheme` |
| P1.5 | **Settings in top bar** | 5 action icons: Search + Sort + View Mode + Filter + Settings | 2 action icons: Search + Sort only | Add Settings gear icon to LibraryScreen TopAppBar actions |
| P1.6 | **Default seed color** | Dynamic color ON by default (Android 12+ wallpaper); static brand palette below API 31 | ThemeSettings default seed = 0L (no tint); dynamic color toggle exists but unclear if default-on | Ensure dynamic color defaults ON for API 31+; ensure seed color = 0 still uses a visible brand palette rather than pure black |

### Priority 2 — Architectural mismatch (tonearmboy's rail pattern)

| # | Component | tonearmboy | pageboy | Fix |
| --- | --- | --- | --- | --- |
| P2.1 | **Rail implementation** | Custom `LibraryRail` — `Box(requiredWidth=52.dp)` with `Column` of tab items + settings gear at bottom | Standard M3 `NavigationRail` Composable with horizontal text labels | Replace `NavigationRail` with a custom `LibraryRail` matching tonearmboy's: 52dp wide, vertical Column, per-tab items, settings gear pinned at bottom |
| P2.2 | **Tab items in rail** | Each item = `Box(52×108dp)` with `Text(rotate(-90f), labelLarge, maxLines=1, wrapContentSize(unbounded=true))` + 2dp `primary` accent stripe on right edge when selected; bold when selected, normal when not; text color `onSurface` (selected) / `onSurfaceVariant` (unselected) | `NavigationRailItem` with default M3 styling (horizontal text, pill indicator, M3 icon) | Rewrite each rail item to match tonearmboy's `RailTabItem` pattern exactly: 52×108dp box, -90° text, accent stripe, bold/normal, theme colors |
| P2.3 | **Horizontal TabRow** | DOES NOT EXIST — the rail IS the tab selector | `TabRow` with 4 horizontal tabs (Started / All / Recents / Pinned) above the library content | Remove the horizontal TabRow entirely; the rail items (Started / All / Recents / Pinned) become the tab selector, like tonearmboy's Songs / Albums / Artists / etc. in the rail |
| P2.4 | **Settings in rail** | `IconButton` with `Icons.Filled.Settings` pinned at bottom of rail, padding 8dp, tint `onSurfaceVariant` | Settings is a full rail destination (4th NavigationRailItem) with icon + label | Move Settings out of the rail destinations; add it as a gear icon pinned at the bottom of the custom rail (matching tonearmboy), clicking navigates to SettingsRootDest |
| P2.5 | **Rail background** | `MaterialTheme.colorScheme.surface` | Default M3 NavigationRail container color | Set rail background to `MaterialTheme.colorScheme.surface` explicitly |
| P2.6 | **Rail scrolling** | Tabs scroll vertically via `rememberScrollState()` if they exceed rail height; settings gear stays pinned | N/A (M3 NavigationRail doesn't scroll) | Implement scrollable rail tab section with settings gear pinned at bottom (weight distribution: scrollable tabs `weight(1f)` + fixed gear) |

### Priority 3 — Library screen feature parity

| # | Component | tonearmboy | pageboy | Fix |
| --- | --- | --- | --- | --- |
| P3.1 | **Top bar actions** | Search + Sort + **View Mode toggle** + **Filter (with badge)** + Settings = 5 icons | Search + Sort = 2 icons | Add View Mode toggle + Filter + Settings icons to the top bar. View Mode cycles List→Tile→TwoColumn (shows next-mode icon). Filter opens ModalBottomSheet. |
| P3.2 | **View modes** | List / Tile (adaptive 160dp min grid) / TwoColumn (fixed 2-column grid) — per-tab persistence via DataStore | List only | Implement Tile + TwoColumn view modes; per-tab DataStore persistence; toggle icon in top bar |
| P3.3 | **Filter UI** | ModalBottomSheet with name filter + year range + date-added range + Reset/Apply buttons | Inline filter chips below TabRow (format + collection chips) | Replace inline filter chips with a Filter ModalBottomSheet matching tonearmboy's pattern; filter icon in top bar with badge when active |
| P3.4 | **Sort UI** | ModalBottomSheet with RadioButton per sort key + Ascending/Descending segmented button + Cancel/OK | Sort dropdown in top bar overflow | Replace sort dropdown with a Sort ModalBottomSheet matching tonearmboy's pattern |
| P3.5 | **Section headers** | Sticky headers per sort-group key; `RoundedCornerShape(12.dp)`, `surfaceContainerHigh` background, `labelLarge`, `onSurface` color, padding 16dp×8dp | No section headers | Add sticky section headers matching tonearmboy's `SectionHeader` pattern |
| P3.6 | **Tile grid cards** | `Column`: 4dp padding, cover art with `aspectRatio(1f)` + `RoundedCornerShape(12.dp)` + `surfaceVariant` background, title below (titleSmall, maxLines=1), subtitle (bodySmall, maxLines=1), overflow icon top-right | No tile view | Implement tile cards matching tonearmboy's `TileCell` pattern |
| P3.7 | **List row styling** | Track: 16dp h / 10dp v padding, CoverArt(48dp, 4dp corner), Spacer(12dp), title (titleSmall, maxLines=1) + subtitle (bodySmall, maxLines=1), overflow MoreVert, HorizontalDivider | DocumentCard: different styling (verify exact differences) | Match tonearmboy's list-row dimensions + typography + divider pattern |
| P3.8 | **Multi-select** | SecondaryContainer background on selected rows, MultiSelectBar at top (close + count + add-to-playlist + delete + overflow), 3dp border on selected tiles | No multi-select | Implement multi-select matching tonearmboy's pattern |
| P3.9 | **Empty states** | `Box(fillMaxSize, padding=32.dp)`, `bodyMedium`, centered | `LibraryEmptyState` — functional but possibly less polished | Verify styling matches; adjust if needed |
| P3.10 | **Scan progress** | `Surface(tonalElevation=2.dp)`, `AnimatedVisibility(fadeIn+expandVertically)`, Row with count + percentage + LinearProgressIndicator + current title | `LibraryScanProgressBanner` — verify styling vs tonearmboy | Match tonearmboy's scan-progress-bar pattern (tonal elevation + animated visibility) |

### Priority 4 — Theme refinement

| # | Component | tonearmboy | pageboy | Fix |
| --- | --- | --- | --- | --- |
| P4.1 | **BaseTheme sealed** | 4 variants: `DefaultAndroid` (dynamic) / `DefaultColors` (static brand) / `PureBlack` (AMOLED) / `Custom(seedRgb)` | ThemeSettings exists but scope unclear; close-out agent added AppearanceEntries | Verify BaseTheme has all 4 variants; implement `PureBlack` (surface=Color.Black, background=Color.Black) |
| P4.2 | **Custom seed derivation** | `deriveCustomScheme(seedRgb, darkTheme)` — HSL rotation: secondary=hue+30°, tertiary=hue+60°; feeds into `lightColorScheme()` / `darkColorScheme()` with computed primary/secondary/tertiary + luminance-derived `onPrimary` | Unknown — need to verify what the close-out agent implemented | Ensure `deriveCustomScheme` matches tonearmboy's HSL rotation pattern exactly |
| P4.3 | **Surface blending** | `blendSurface(base, tint, 0.4f)` applied to ALL 9 surface tokens: surface, surfaceVariant, background, surfaceContainerLowest through surfaceContainerHighest, secondaryContainer | Unknown — verify if close-out agent implemented | Implement surface blending across all 9 tokens when seed/dynamic color is active |
| P4.4 | **Color picker** | Hand-rolled HSV picker: preview swatch + hex readout + saturation/value square (drag/tap) + hue slider (0-360) + Cancel/Reset/OK buttons | ColorPickerDialog.kt exists — verify quality | Compare against tonearmboy's ColorPickerDialog.kt; ensure HSV square + hue slider + preview pattern matches |
| P4.5 | **Look & Feel entries** | Theme picker (System/Light/Dark) + Base theme picker (DefaultAndroid/DefaultColors/PureBlack/Custom) + Album art tint toggle + Album art background toggle + Custom chrome tint picker | AppearanceEntries.kt exists — verify entries | Match tonearmboy's LookAndFeelEntries structure (adapted for pageboy — no album art fields; replace with document-theme seed picker) |
| P4.6 | **Default dynamic color** | `BaseTheme.DefaultAndroid` is the default → dynamic color ON for API 31+, falls back to brand palette below | Verify what the close-out agent set as default | Ensure `BaseTheme.DefaultAndroid` (or equivalent) is the default, so the app tints from wallpaper on first install without the user touching settings |

## Implementation order

**Round 1 (Quick wins — fix the worst visual breaks):**
- P1.1 tab text wrapping (1 line fix)
- P1.2 icon state consistency (8 lines)
- P1.3 rail item theme tinting (add `colors` param)
- P1.5 Settings gear in top bar (add action icon)
- P1.6 default dynamic color (verify + fix default)

**Round 2 (Architectural rework — the big one):**
- P2.1-P2.6: replace M3 NavigationRail with custom LibraryRail matching tonearmboy's exact pattern

**Round 3 (Feature surface parity):**
- [x] **P3.1** Top bar actions: added View Mode + Filter (with badge) icons to TopAppBar alongside existing Search + Sort + Settings
- [x] **P3.2** View modes: ViewMode enum (List/Tile/TwoColumn) with DataStore persistence, cycle button in TopAppBar, icon reflects current mode, LibraryDocumentList dispatches on viewMode
- [x] **P3.3** Filter UI: created LibraryFilterSheet.kt (ModalBottomSheet with format + collection checkboxes, Reset/Apply); replaced inline LibraryFilterChipRow with filter icon + badge in TopAppBar
- [x] **P3.4** Sort UI: created LibrarySortSheet.kt (ModalBottomSheet with RadioButton per sort key, Cancel/OK); replaced inline DropdownMenu sort
- [x] **P3.5** Section headers: created SectionHeader.kt (rounded surface chip, surfaceContainerHigh, labelLarge); wired into LibraryDocumentList with sectionKey() grouping by Format or first letter
- [x] **P3.6** Tile grid cards: DocumentTile composable with aspectRatio(1f) cover area, format icon, overflow menu, title/subtitle; used by LazyVerticalGrid in Tile + TwoColumn modes
- [x] **P3.7** List row styling: rewrote DocumentCard to match tonearmboy Row pattern (16dp h / 10dp v padding, 48dp icon with 4dp corners, 12dp spacer, titleSmall maxLines=1, bodySmall maxLines=1, MoreVert, HorizontalDivider between rows)
- [x] **P3.8** Multi-select: long-press activates, MultiSelectBar replaces TopAppBar (close + count + pin + delete), secondaryContainer bg on selected rows, 3dp primary border on selected tiles, back gesture closes, deleteDocuments added to DocumentSource
- [x] **P3.9** Empty state: fixed to Box(fillMaxSize, padding=32.dp) with plain bodyMedium text, centered (removed Card wrapper)
- [x] **P3.10** Scan progress banner: rewrote to Surface(tonalElevation=2.dp) with AnimatedVisibility(fadeIn+expandVertically), Row with count + LinearProgressIndicator

**Round 4 (Theme polish):**
- [x] **P4.1** Add `BaseThemeChoice` enum (DefaultAndroid/DefaultColors/PureBlack/Custom) to ThemeSettings.kt + `baseTheme` EnumSetting
- [x] **P4.2** Wire `BaseThemeChoice` through Theme.kt `resolveColorScheme` -- four-way dispatch matching tonearmboy's `resolveBaseScheme`
- [x] **P4.3** Surface blending -- verified: `blendSurface` exists; `deriveCustomScheme` generates full surface container ladder for Custom; pageboy has no album art so no external tint blending needed
- [x] **P4.4** Color picker quality -- verified: HSV square + hue slider + preview swatch + hex readout + Cancel/Reset/OK all present and matching tonearmboy's pattern
- [x] **P4.5** Appearance entries updated: replaced Dynamic Color toggle with Base Theme picker; Seed Color row only active when baseTheme == Custom; base theme picker dialog added to SettingsScreen
- [x] **P4.6** Default theme: `BaseThemeChoice.DefaultAndroid` is the default -- dynamic color ON on first install for API 31+, brand palette below

## Files that need changes

### Round 1 (quick wins):
- `ui/library/LibraryScreen.kt` — TabRow text + top bar actions
- `ui/PageboyApp.kt` — NavigationRailItem colors + icon state

### Round 2 (rail rework):
- `ui/PageboyApp.kt` — replace NavigationRail with custom LibraryRail
- `ui/library/LibraryRail.kt` — NEW FILE matching tonearmboy's
- `ui/library/LibraryScreen.kt` — remove TabRow; receive tab selection from rail
- `ui/Navigation.kt` — rework routes (Settings is no longer a rail destination)

### Round 3 (feature parity):
- `ui/library/LibraryScreen.kt` — view modes + filter/sort bottom sheets
- `ui/library/LibraryFilterSheet.kt` — NEW FILE (ModalBottomSheet)
- `ui/library/LibrarySortSheet.kt` — NEW FILE (ModalBottomSheet)
- `ui/library/LibraryTileGrid.kt` — NEW FILE (grid rendering)
- `ui/library/tabs/SectionHeader.kt` — NEW FILE (sticky headers)
- `ui/library/MultiSelectBar.kt` — NEW FILE
- `data/settings/ViewModeSettings.kt` — per-tab persistence

### Round 4 (theme):
- `ui/theme/Theme.kt` — verify + enhance
- `data/settings/ThemeSettings.kt` — verify BaseTheme variants
- `ui/settings/ColorPickerDialog.kt` — verify quality

## Cross-references

- tonearmboy's `LibraryRail.kt` — the definitive rail pattern
- tonearmboy's `LibraryScreen.kt` — scaffold + top bar with 5 actions
- tonearmboy's `Theme.kt` (276 lines) — full theme resolution + surface blending
- tonearmboy's `ColorPickerDialog.kt` — HSV color picker
- tonearmboy's `LookAndFeelEntries.kt` — settings catalog entries
- tonearmboy's `SortSheet.kt` + `LibraryFilterSheet.kt` — bottom sheet patterns
- tonearmboy's `LibraryTileGrid.kt` — tile card grid
- tonearmboy's `SectionHeader.kt` — sticky section headers
- tonearmboy's `MultiSelectBar.kt` — multi-select chrome

## Note on scope

This plan covers **visual + structural parity with tonearmboy**. Features unique to pageboy (format-specific renderers, PDF signing, PDF annotation, open-with, document-type filter chips) are NOT in scope for this plan — they're already shipped and working. This plan is about the shell chrome, not the content.
