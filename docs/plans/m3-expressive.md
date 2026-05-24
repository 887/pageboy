# pageboy — Material 3 Expressive (M3E) starter

## Status: 🟡 PRESCRIBED — apply during main.md Phase A.2

## Why this plan exists

Tonearmboy paid for five M3E gotchas the hard way. Whisperboy inherited the fixes. Pageboy inherits them too — written down once so the next-round agent who lands Phase A.2 doesn't relearn them on the AVD over a weekend.

Source of truth for the full migration story: `/home/laragana/workspace/tonearmboy/docs/plans/m3-expressive.md`. The five gotchas below are the load-bearing subset every pageboy author needs in muscle memory.

## Inherited from tonearmboy — five gotchas, transcribed

### 1. `material3:1.4.0` keeps `MaterialExpressiveTheme` / `expressive*ColorScheme` `internal`

The Compose BOM `2026.03.01` resolves to `material3:1.4.0` but you can't actually call the expressive APIs from there — the Kotlin metadata marks them `internal`, even though the JVM bytecode is public. **Override the BOM** in `gradle/libs.versions.toml` with `composeMaterial3 = "1.5.0-alpha18"`, the alpha that promoted the APIs to public. `expressiveDarkColorScheme()` does NOT exist in 1.5.0-alpha18 — only the `light` factory ships. Dark mode stays on `darkColorScheme(...)` and inherits the surface-tier ladder. Drop the override once 1.5.0 stable lands.

### 2. `surfaceContainer` is too quiet on AMOLED-leaning dark palettes

Initial pass used `containerColor = surfaceContainer` and it was barely a step above `surface`. The ship uses `surfaceContainerHigh`. Light mode with `expressiveLightColorScheme()` reads better at `surfaceContainer`, so revisit if/when light mode gets a polish pass — but for the dark mode that pageboy will spend most of its time in (e-readers tend to land on dark backgrounds for long-form reading), default to `surfaceContainerHigh` for cards / settings rows / catalog cards from day one.

### 3. Auto-derive accent from `id` at the `SettingsRow` layer, not per call site

Initially tonearmboy passed `accent = accentFor(entry.id)` from every `SettingsRowBinding.Render` impl + from `SettingsScreen.kt`. That left direct `SettingsRow(...)` callers like `AboutScreen` / `LicensesScreen` (which don't go through bindings) monochrome. **The fix is a one-liner:** `SettingsRow` falls back to `accentFor(id)` internally when caller passes `accent = null` and a non-null `id`. Every direct caller now gets the avatar without changes.

Pageboy: write `SettingsRow` with the fallback baked in from the start. Same for any future hand-rolled settings surface (Reader sub-pages, Annotations sub-pages, Signing sub-pages) — they all pick up colour for free.

### 4. Android 12+ splash icon is hard circle-clipped, period

The layer-list `android:windowBackground` workaround does NOT work — the system splash paints `windowSplashScreenBackground` over the whole window during launch, covering anything you set on `windowBackground`. **The working approach:** ship a dedicated splash mipmap (`ic_launcher_splash.png`) where the design is shrunk so it inscribes inside the system circle. **70.7 % (1/√2) is too tight** — corners still graze the mask. **60 %** gave proper headroom on the user's device. Set `windowSplashScreenIconBackgroundColor = launcher_background` so the bigger 240-dp icon area kicks in.

Pageboy: when the user supplies the launcher icon (deferred — placeholder will sit at `app/src/main/res/mipmap-*/ic_launcher*.png`), pre-shrink the splash variant to 60 % before exporting.

### 5. Album-art tint blending skips the `surfaceContainer*` ladder

Tonearmboy's `Theme.kt` blends `surface` / `surfaceVariant` / `background` with the dominant cover-art swatch but NOT the `surfaceContainer*` ladder. That's why the library list / detail cards look uniformly tinted with the album palette while the page surface drifts.

**Pageboy applicability:** less direct (pageboy doesn't have cover art the way tonearmboy does — documents have first-page thumbnails, not Palette-rich album covers). If pageboy ends up extracting a tint from a PDF's first page or an EPUB's cover image (a possible Phase F.6-equivalent), apply the tint across the full `surfaceContainer*` ladder from the start; do not skip rungs.

## The four M3E patterns to land at Phase A.2

Drawn directly from tonearmboy's shipped Phases A–C; each one is a small composable or a small theming tweak, not a re-architecture.

1. **Surface tier ladder** — `surfaceContainerLowest` < `surfaceContainerLow` < `surfaceContainer` < `surfaceContainerHigh` < `surfaceContainerHighest`. Page background = `surface`. Card / catalog row = `surfaceContainerHigh` on dark, `surfaceContainer` on light.
2. **`MaterialExpressiveTheme` wrapper** — the theme entry composable wraps `expressiveLightColorScheme()` / `darkColorScheme()` in `MaterialExpressiveTheme(...)` rather than the bare `MaterialTheme(...)`.
3. **Coloured circular row-icon avatars** — settings rows render their leading icon inside a 40dp filled circle painted with a per-category accent. `CategoryAccent` is a small data class in the theme holding ~6 hand-picked accent pairs (light + dark). Pageboy section breakdown is in [`ui-shell.md`](ui-shell.md); pick accents during A.4.
4. **Filled glyph icons** at the settings-row layer — `androidx.compose.material:material-icons-extended` (no longer transitive in `material3:1.4.0+`, add explicitly), `Icons.Filled.*` (not `Icons.Outlined.*`) for the settings rows. Don't touch icon usage inside the reader chrome / annotation toolbar — those have their own logic.

## What's deferred

- The cover-tint / first-page-tint experiment (gotcha 5 applicability) — defer to a post-Phase-M decision; pageboy doesn't have a single canonical "cover" surface yet, and the right answer depends on which renderers expose first-page bitmaps cheaply.
- A "dynamic color" toggle (Material You wallpaper-driven palette). Whisperboy ships it in K.5; pageboy will too, but during the settings phase, not during A.2.
- Per-app custom base color picker (whisperboy's K.5 follow-up). Same deferral — settings-phase work, not bootstrap work.

## What pageboy is NOT redoing

The tonearmboy migration drove a long deliberation about typography defaults, font weights, and motion specs. Pageboy adopts whatever defaults tonearmboy + whisperboy currently use. If the user's reading typography needs ever diverge from the rest of the family's display typography, that's a future plan.
