# pageboy — open-source licenses plan

## Status: 🟡 PRESCRIBED — apply during main.md Phase A.5; inventory snapshot deferred

## Why

The app ships under MIT (`LICENSE` in repo root). Every dependency that ships in the APK will almost certainly be one of `Apache-2.0` / `MIT` / `BSD-2-Clause` / `BSD-3-Clause` — the same SPDX set tonearmboy and whisperboy converged on — but **the format-research phase has not picked its libraries yet**, so the exact inventory is unknowable today. Plan documents this gap and locks the approach so the inventory step is a single command, not a re-architecture, once the dep list firms up.

Apache 2.0 §4 requires that downstream binary distributions preserve copyright + NOTICE entries from upstream artifacts. The Android-conventional way to satisfy this is an "Open-source licenses" sub-page: a list of every shipping dep with name, version, license SPDX, and license body.

## Approach (locked — copied from tonearmboy)

- **Build-time inventory, zero runtime deps.** Use the [`app.cash.licensee`](https://github.com/cashapp/licensee) Gradle plugin. It walks the resolved `releaseRuntimeClasspath` at configuration time and writes a JSON inventory; nothing is added to the APK at runtime. Licensee is Apache 2.0 itself, build-time only, and is the pattern Cash App / Square use.
- **Compose-rendered sub-screen.** A new `LicensesScreen.kt` reads the generated `artifacts.json` from `assets/licenses/` and renders a `LazyColumn` of M3 Expressive cards. Tapping a row reveals the license body. License bodies ship as raw text assets — finite set, three or four at most.
- **Robolectric-driven catalog test.** Parses the generated JSON, asserts non-empty, asserts every entry has a known SPDX and a backing license-text asset, asserts a known sample of shipping deps is present. Catches accidental dep removal and unknown-license additions.
- **Report-only allowlist in v1.** Licensee can fail the build on disallowed licenses. Declare the allowlist (`Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`) but do not enforce in v1. A second pass can flip enforcement once the inventory has been reviewed once.

## Inventory snapshot

**TBD — next-round research agents will run `./gradlew :app:licenseeAndroidDebug` once the per-format library choices land in `format-research.md`.** The inventory will most likely include the same `androidx.*` base set tonearmboy and whisperboy ship plus whichever format-rendering libraries the research phase picks (`com.itextpdf:itext7-core`? `org.commonmark:commonmark`? `org.apache.poi:poi-ooxml`? `nl.siegmann.epublib:epublib-core`? `com.github.barteksc:android-pdf-viewer`? — these are *examples* of candidates, not commitments; the research agents decide).

Format-research outputs **must** include the license SPDX of each candidate library — and **MIT compatibility is a hard gate**. Any GPL / AGPL / LGPL dep is disqualifying for pageboy (the MIT license at the repo root would have to change to a copyleft license to ship one, which would change the user's distribution surface in a way the user has not authorized). The research plan asks for this explicitly; see [`format-research.md`](format-research.md) "License compatibility" question per format.

## Phase A — Licensee plugin + generated inventory (sub-step of main.md A.5)

- [ ] **A.1** Add Licensee (latest stable) to `gradle/libs.versions.toml` (versions + plugins block).
- [ ] **A.2** Apply `alias(libs.plugins.licensee)` in `app/build.gradle.kts`.
- [ ] **A.3** Configure `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause") }`. Test-scope deps (JUnit's EPL-1.0) never enter the resolved release classpath Licensee inspects, so no `allowDependency` exemption is needed at A.3.
- [ ] **A.4** `androidComponents.onVariants` wires a per-variant `Copy` task that depends on `licenseeAndroid<Variant>` and feeds `merge<Variant>Assets`. Copies `build/reports/licensee/android<Variant>/artifacts.json` into `src/main/assets/licenses/`.
- [ ] **A.5** Source `Apache-2.0.txt`, `MIT.txt`, `BSD-3-Clause.txt`, `BSD-2-Clause.txt` from `https://spdx.org/licenses/<id>.txt` (canonical SPDX). Add any additional SPDX text the format-research phase requires.
- [ ] **A.6** `:app:assembleDebug` clean. `assets/licenses/artifacts.json` parses with valid SPDX coordinates. Spot-check the format-rendering deps that landed.

## Phase B — `LicensesScreen` Compose UI (sub-step of main.md A.5)

- [ ] **B.1** Add `app/src/main/java/com/eight87/pageboy/ui/settings/LicensesScreen.kt`. Reuses `SettingsCard` / `SettingsRow` / `SettingsDimens` chrome from the catalog DSL.
- [ ] **B.2** Read happens directly in `loadLicensesFromAssets(context)` cached via `remember(context)` — single one-shot read. No ViewModel (the data is immutable per build).
- [ ] **B.3** `LicenseEntry { groupId, artifactId, version, spdxId, licenseText: String? }` — `licenseText` resolved by `assets/licenses/<spdx>.txt` lookup at construction; unknown SPDX → `licenseText = null` + row renders an "unknown SPDX" string.
- [ ] **B.4** `LazyColumn` of single-row `SettingsCard`s. Card title: `<artifactId> <version>`. Row label: `<groupId>`. Row subtitle: SPDX id. Tap → `AlertDialog` with monospaced license body, scrollable, "Close" button. Verified on AVD.
- [ ] **B.5** Navigation route `SettingsLicensesRoute` added, registered in `PageboyApp.kt`. `AboutScreen` gains an `onLicenses` parameter and a new `SettingsRow` "Open-source licenses".
- [ ] **B.6** Strings: `settings_about_licenses_label`, `settings_about_licenses_subtitle`, `licenses_screen_title`, `licenses_row_supporting`, `licenses_unknown_spdx`, `licenses_empty`, `licenses_dialog_close`. All in `values/strings_settings.xml`.
- [ ] **B.7** AVD smoke: Settings root → About → tap "Open-source licenses" → screen lists every dep → tap any row → dialog shows full license text in monospaced, scrollable.

## Phase C — Tests + audit discipline (sub-step of main.md A.5 + ongoing)

- [ ] **C.1** `LicensesCatalogTest` (Robolectric, JVM-only): parses `assets/licenses/artifacts.json`; asserts non-empty; asserts every entry has a SPDX from the allowlist and a backing license-text asset; asserts the catalog contains the known shipping samples.
- [ ] **C.2** `LicensesScreenTest` (Compose UI test under Robolectric): renders the screen, taps the first row, asserts the dialog body shows the SPDX text.
- [ ] **C.3** Ongoing audit: when adding a new `implementation` dep, run `./gradlew :app:licenseeAndroidDebug` and confirm the SPDX is in the allowlist. If not, add it via `licensee { allow("…") }` (preferred) or document the exemption with `allowDependency(group, artifact, version) { because("…") }`. Adding a new SPDX also requires shipping its canonical text at `app/src/main/assets/licenses/<spdx>.txt`; `LicensesCatalogTest` will fail loud otherwise.
