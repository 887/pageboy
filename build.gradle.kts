// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  // oss-licenses A.2 — Licensee plugin available on the classpath for :app.
  alias(libs.plugins.licensee) apply false
  // Phase B — KSP available on the :app classpath so Room can generate DAOs.
  alias(libs.plugins.ksp) apply false
}
