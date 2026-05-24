import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  // oss-licenses A.2 — Licensee plugin. Generates per-variant `artifacts.json`
  // under `app/build/reports/licensee/android<Variant>/`. The Copy task wired
  // below stages that JSON into `src/main/assets/licenses/` so LicensesScreen
  // can read it via AssetManager at runtime.
  alias(libs.plugins.licensee)
  // Phase B — KSP runs Room's annotation processor at build time. Apache-2.0.
  alias(libs.plugins.ksp)
}

// Mirror of whisperboy's About-screen build-metadata capture. `git rev-parse`
// runs at configuration time so the result is baked into BuildConfig; the date
// is captured at the same time. Both fall back to sentinels when run from a
// tarball without git history.
val gitShortSha: String = runCatching {
  val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
    .redirectErrorStream(true)
    .start()
  proc.waitFor()
  proc.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
}.getOrDefault("unknown")

val buildDateUtc: String = DateTimeFormatter.ISO_LOCAL_DATE
  .format(LocalDate.now(ZoneOffset.UTC))

android {
    namespace = "com.eight87.pageboy"
    compileSdk = 36
    compileSdkExtension = 19  // required by androidx.pdf-viewer-fragment 1.0.0-alpha18
    base {
        archivesName.set("pageboy")
    }
    defaultConfig {
        applicationId = "com.eight87.pageboy"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDateUtc\"")
        // Phase B — Room schema export. Committed under `app/schemas/` so
        // future migrations can diff the generated JSON. KSP picks this up
        // via the room.schemaLocation argument below.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Phase M — Readium Kotlin Toolkit 3.2.0 requires core library
        // desugaring (uses java.time + java.nio APIs that minSdk 28 does
        // not natively cover). The desugar runtime ships in the APK as
        // `com.android.tools:desugar_jdk_libs` declared in `dependencies`
        // via `coreLibraryDesugaring(...)`.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Phase I/J — Apache POI + XmlBeans + aalto bring in a handful of
        // META-INF artifacts (LICENSE / NOTICE / DEPENDENCIES / versions /
        // services duplicates across the OOXML schema jars) that Gradle's
        // mergeJavaResource step otherwise treats as conflicts. The license
        // texts themselves are surfaced via LicensesScreen + the Licensee
        // inventory (oss-licenses.md) — excluding them from packaging is
        // standard practice for POI on Android.
        excludes += listOf(
          "META-INF/LICENSE",
          "META-INF/LICENSE.txt",
          "META-INF/NOTICE",
          "META-INF/NOTICE.txt",
          "META-INF/DEPENDENCIES",
          "META-INF/INDEX.LIST",
          "META-INF/*.kotlin_module",
          "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
          "META-INF/services/javax.xml.stream.*",
          "META-INF/maven/**",
        )
        // POI ships duplicate `mozilla/public-suffix-list.txt` and similar
        // text resources across its OOXML jar split; pick the first one.
        pickFirsts += listOf(
          "mozilla/public-suffix-list.txt",
          "draftv3/schema",
          "draftv4/schema",
        )
      }
    }

    // Robolectric — JVM-only Android-shadow runner for unit tests.
    //   * `isIncludeAndroidResources = true` packages merged resources +
    //     `AndroidManifest.xml` + the `assets/` tree onto the JVM test
    //     classpath, which is what makes `LocalContext.current.assets.open(...)`
    //     resolve under Robolectric (the LicensesScreen's `assets/licenses/`
    //     inventory and future format-renderer asset reads land via this).
    //   * `usePreciseLog` keeps Robolectric's "ran shadow X at line Y" trace
    //     on so test failures are diagnosable without re-running.
    // Test classes pick the SDK level with `@Config(sdk = [34])`.
    testOptions {
      unitTests.isIncludeAndroidResources = true
      unitTests.all {
        it.systemProperty("robolectric.usePreciseLog", "true")
      }
    }
}

// oss-licenses A.3 — declare allowed SPDX ids for shipped deps. v1 ships
// reporting-only (no failOnDisallowed); the catalog test in app/src/test
// is what fails loud if an unrecognised SPDX sneaks in. EPL-1.0 (junit) is
// test-scope only, so it never enters the resolved release classpath that
// Licensee inspects. The family allowlist (MIT/Apache-2.0/BSD-2/BSD-3/MPL-2.0)
// matches tonearmboy / whisperboy plus MPL-2.0 reserved for any per-format
// renderer libraries the next-round research may pick.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("MPL-2.0")
    // slf4j-api 2.0.17 (pulled transitively by POI) lists its MIT
    // license via opensource.org's new URL path (/license/mit instead
    // of /licenses/MIT); whitelist that URL so Licensee maps it to MIT.
    allowUrl("https://opensource.org/license/mit")
    // Phase M — Readium 3.2.0 (BSD-3-Clause) ships its license URL as
    // a GitHub blob link rather than the SPDX canonical URL. jsoup
    // 1.22.2 (pulled transitively by Readium-shared for HTML walking)
    // lists its MIT license via the jsoup.org/license URL. Both
    // resolve to family-allowlist licences when followed; whitelist
    // the URLs so Licensee maps them correctly.
    allowUrl("https://github.com/readium/kotlin-toolkit/blob/main/LICENSE")
    allowUrl("https://jsoup.org/license")
    // Phase H — Bouncy Castle ships its "adaptation of the MIT X11
    // License" via the bouncycastle.org/licence.html URL; Licensee
    // can't auto-map that to an SPDX id, so each artifact gets an
    // explicit allowDependency entry. Text is functionally MIT (see
    // libs.versions.toml comment); we treat it as such for the
    // bundled LicensesScreen surface but cannot claim the SPDX
    // mapping in the build-time inventory.
    // bcprov resolves to 1.81.1 transitively via bcutil's [1.81,1.82)
    // range; bcpkix + bcutil stay on 1.81.
    allowDependency("org.bouncycastle", "bcprov-jdk18on", "1.81.1") {
        because("Bouncy Castle MIT X11-style license (bouncycastle.org/licence.html). " +
                "Functionally MIT; Licensee cannot auto-map the project URL.")
    }
    allowDependency("org.bouncycastle", "bcpkix-jdk18on", "1.81") {
        because("Bouncy Castle MIT X11-style license (bouncycastle.org/licence.html). " +
                "Functionally MIT; Licensee cannot auto-map the project URL.")
    }
    allowDependency("org.bouncycastle", "bcutil-jdk18on", "1.81.1") {
        because("Bouncy Castle MIT X11-style license (bouncycastle.org/licence.html). " +
                "Functionally MIT; Licensee cannot auto-map the project URL.")
    }
    // Phase H — OpenPDF 2.0.4 dual-licenses MPL-2.0 OR LGPL-2.1+;
    // we pick MPL-2.0 (already on the family allowlist), but the
    // POM may declare both — explicit allowDependency keeps the
    // inventory deterministic if Licensee maps to the LGPL entry.
    allowDependency("com.github.librepdf", "openpdf", "2.0.4") {
        because("OpenPDF dual-licensed MPL-2.0 OR LGPL-2.1+; pageboy ships under MPL-2.0.")
    }
}

// oss-licenses A.4 — copy the per-variant Licensee `artifacts.json` into
// `src/main/assets/licenses/` so LicensesScreen can read it via
// AssetManager at runtime. Wired as a dependency of the variant's
// mergeAssets task so the asset is always self-consistent with the
// build's resolved classpath.
val licenseeReportDir = layout.buildDirectory.dir("reports/licensee")
val licensesAssetDir = layout.projectDirectory.dir("src/main/assets/licenses")

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyTask = tasks.register<Copy>("copyLicensesAssetFor$variantName") {
            dependsOn("licenseeAndroid$variantName")
            from(licenseeReportDir.map { it.dir("android$variantName") }) {
                include("artifacts.json")
            }
            into(licensesAssetDir)
        }
        afterEvaluate {
            tasks.named("merge${variantName}Assets").configure {
                dependsOn(copyTask)
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // material-icons-extended — non-core glyphs used by the nav rail, About,
  // settings rows. Not transitive in material3 1.4.0+ (m3-expressive
  // gotcha #2).
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Robolectric — JVM-side Android shadows + AndroidX test runner shims. Lets
  // any `Context`/`AssetManager`/`Resources`-dependent test (and the Compose
  // `createComposeRule()` harness) run under `:app:testDebugUnitTest` without
  // an emulator. Compose UI tests reuse the already-versioned
  // `androidx.compose.ui:ui-test-junit4` from the BOM plus `ui-test-manifest`
  // for the host harness.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core.ktx)
  testImplementation(libs.androidx.test.ext.junit.ktx)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.compose.ui.test.manifest)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // kotlinx-serialization JSON — used by LicensesScreen to parse the
  // Licensee-generated `artifacts.json` asset at runtime.
  implementation(libs.kotlinx.serialization.json)

  // Phase B — Room (document library cache) + KSP-generated DAOs.
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.androidx.room.testing)

  // Phase B — DataStore Preferences for library UI prefs + per-root metadata.
  implementation(libs.androidx.datastore.preferences)

  // Phase B — DocumentFile wrapper around SAF tree URIs; backing API for
  // the scanner's tree walker.
  implementation(libs.androidx.documentfile)

  // Phase D — commonmark-java parser + six GFM extensions for the Markdown
  // renderer. All BSD-2-Clause; on the family Licensee allowlist. Renderer
  // is hand-rolled Compose on top of the AST (see format/markdown/). The
  // yaml-front-matter extension is excluded — a 40-LOC sniffer strips the
  // `--- … ---` header before the body reaches commonmark, which keeps
  // snakeyaml-engine out of the APK (~150 KB saved).
  implementation(libs.commonmark.core)
  implementation(libs.commonmark.ext.gfm.tables)
  implementation(libs.commonmark.ext.gfm.strikethrough)
  implementation(libs.commonmark.ext.task.list.items)
  implementation(libs.commonmark.ext.autolink)
  implementation(libs.commonmark.ext.footnotes)
  implementation(libs.commonmark.ext.image.attributes)

  // Phase F — androidx.pdf view-side fragment + Compose interop. The
  // pdf-viewer-fragment artifact transitively pulls pdf-viewer +
  // pdf-document-service; we list every artifact so Licensee inventories
  // them explicitly + version drift is surfaced in the manifest diff.
  // Apache-2.0; minSdk 28 backport via sdk-extension < 13. No native
  // code in our APK (Pdfium runs inside the system PdfRenderer service).
  implementation(libs.androidx.pdf.viewer.fragment)
  implementation(libs.androidx.pdf.viewer)
  implementation(libs.androidx.pdf.document.service)
  implementation(libs.androidx.fragment.compose)

  // Phase F — Coil 3 (compose binding). Closes Phase D's image-rendering
  // deferral so markdown inline + standalone images render the actual
  // bitmap (was placeholder card). Apache-2.0; ~1 MB universal APK
  // delta after R8. Disk cache disabled by default in v1 (see
  // AppGraph.imageLoader) so reader sessions don't leak across users.
  implementation(libs.coil.compose)

  // Phase I/J — Apache POI for the OOXML pair (DOCX + XLSX). Apache-2.0.
  //
  // The StAX implementation Android does NOT ship (`javax.xml.stream.*`
  // factories) is provided by `com.fasterxml:aalto-xml`. POI 5.x respects
  // three `org.apache.poi.javax.xml.stream.*` system properties when
  // looking up factories, so we wire those in PageboyApplication.onCreate()
  // before any POI class loads.
  //
  // xlsx-streamer (the actively-maintained pjfanning fork of monitorjbl's
  // excel-streaming-reader) is layered on top of POI for windowed-row
  // reading on large workbooks. We exclude its `org.apache.poi` transitive
  // so it picks up our pinned POI 5.5.1 rather than its own (likely older)
  // pin.
  //
  // Several POI transitives are excluded — `log4j-api` (Android ships
  // SLF4J via androidx; POI logs are noisy and we don't pipe them
  // anywhere), `xml-apis` (Android's stripped runtime owns the
  // `org.w3c.dom.*` + `org.xml.sax.*` namespaces; pulling another copy
  // causes dex collisions).
  implementation(libs.poi.ooxml) {
    exclude(group = "org.apache.logging.log4j")
    exclude(group = "xml-apis")
  }
  implementation(libs.poi.scratchpad) {
    exclude(group = "org.apache.logging.log4j")
    exclude(group = "xml-apis")
  }
  implementation(libs.aalto.xml)
  implementation(libs.xlsx.streamer) {
    exclude(group = "org.apache.logging.log4j")
    exclude(group = "xml-apis")
  }

  // Phase M — Readium Kotlin Toolkit (EPUB renderer). BSD-3-Clause; on
  // the Licensee allowlist (Phase A configured the family allowlist
  // including BSD-3-Clause).
  //
  // androidx.media3-* transitives are excluded — Readium's
  // readium-navigator pulls media3-session / media3-common-ktx /
  // media3-exoplayer because the navigator umbrella also covers
  // audiobooks. Pageboy never reaches that pathway (we route only EPUB
  // assets to the EpubNavigatorFragment), so excluding the transitives
  // shaves ~600–900 KB of unminified bytes the long-term R8 pass would
  // otherwise have to chase per docs/plans/format-epub.md APK-budget.
  //
  // androidx.legacy:legacy-support-core-ui is left in — Readium's
  // EpubNavigatorFragment depends on `androidx.viewpager` through it for
  // the spine pager. R8 trims most.
  implementation(libs.readium.shared)
  implementation(libs.readium.streamer)
  implementation(libs.readium.navigator) {
    exclude(group = "androidx.media3")
  }

  // Phase M — desugar_jdk_libs (java.time / java.nio backport for
  // minSdk 28; required by Readium). Apache-2.0. Ships its own runtime
  // dex inside the APK; R8 tree-shakes unreferenced parts.
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  // Phase H — PDF signing.
  //   * OpenPDF 2.0.4 (MPL-2.0): the PDF writer/stamper for the
  //     visual-stamp burn-in path (Phase H.4) and the PAdES
  //     signature appearance + /ByteRange computation (Phase H.5).
  //     Pure-Java; ~2 MB APK, ~1.5 MB post-R8.
  //   * Bouncy Castle bcprov-jdk18on 1.81 (MIT X11-style): JCA
  //     security provider — EC P-256 key gen, RSA-PSS / ECDSA
  //     signing primitives, PKCS#12 keystore parser.
  //   * Bouncy Castle bcpkix-jdk18on 1.81 (MIT X11-style):
  //     CMS / PKCS#7 SignedData generator + X.509 v3 cert builder
  //     for the self-signed Keystore path.
  // Total Phase H delta budget: ~2.5–3 MB post-R8.
  implementation(libs.openpdf.core)
  implementation(libs.bouncycastle.prov)
  implementation(libs.bouncycastle.pkix)
}
