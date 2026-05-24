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
}
