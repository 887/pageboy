package com.eight87.pageboy.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import com.eight87.pageboy.BuildConfig
import com.eight87.pageboy.R
import kotlinx.coroutines.launch

/**
 * About sub-page. Renders inside the same M3 Expressive grouped
 * cards (`SettingsCard` / `SettingsRow`) used elsewhere so the chrome
 * lines up with every other settings surface.
 *
 * Layout:
 *   - "Build" card: app name, version + SHA (build-version row taps
 *     drive [EasterEggController]; three taps within five seconds
 *     reveal a fullscreen ferret), build date.
 *   - "Source" card: GitHub repo link, MIT license note, link to the
 *     Open-source licenses sub-page.
 *
 * Per `docs/plans/ui-shell.md`, a sibling-credit card lands in a later
 * pass once the format-research phase names the open-source design-space
 * references (Markor, Librera, MuPDF viewer, Collabora Office).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  onBack: () -> Unit,
  onLicenses: () -> Unit,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val easterEgg = remember { EasterEggController() }
  var ferretVisible by remember { mutableStateOf(false) }

  val easterEggFirst = stringResource(R.string.settings_about_easter_egg_first)
  val easterEggSecond = stringResource(R.string.settings_about_easter_egg_second)

  val versionName = stringResource(
    R.string.settings_about_version_subtitle,
    BuildConfig.VERSION_NAME,
    BuildConfig.GIT_SHA,
  )
  val buildDate = stringResource(
    R.string.settings_about_build_date_subtitle,
    BuildConfig.BUILD_DATE,
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_about_title)) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { testTag = "about_back" },
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.settings_cd_back),
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .semantics { testTag = "about_screen" },
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      // ---- Build card ----
      SettingsCard(
        title = stringResource(R.string.settings_about_card_build),
        modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
      ) {
        SettingsRow(
          id = "about.app_name",
          icon = Icons.Outlined.Info,
          label = stringResource(R.string.settings_about_application_label),
          subtitle = stringResource(R.string.settings_about_application_subtitle),
          onClick = null,
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.version",
          icon = Icons.Outlined.Numbers,
          label = stringResource(R.string.settings_about_version_label),
          subtitle = versionName,
          onClick = {
            when (easterEgg.tap(System.currentTimeMillis())) {
              EasterEggController.Outcome.FirstPromptSnackbar -> scope.launch {
                snackbarHostState.showSnackbar(easterEggFirst)
              }
              EasterEggController.Outcome.SecondPromptSnackbar -> scope.launch {
                snackbarHostState.showSnackbar(easterEggSecond)
              }
              EasterEggController.Outcome.Reveal -> {
                ferretVisible = true
              }
            }
          },
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.build_date",
          icon = Icons.Outlined.Schedule,
          label = stringResource(R.string.settings_about_build_date_label),
          subtitle = buildDate,
          onClick = null,
        )
      }

      // ---- Source card ----
      SettingsCard(
        title = stringResource(R.string.settings_about_card_source),
        modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
      ) {
        SettingsRow(
          id = "about.github",
          icon = Icons.Outlined.Launch,
          label = stringResource(R.string.settings_about_github_label),
          subtitle = stringResource(R.string.settings_about_github_subtitle),
          onClick = { openExternalBrowser(context, GITHUB_URL) },
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.licenses",
          icon = Icons.Outlined.Article,
          label = stringResource(R.string.settings_about_licenses_label),
          subtitle = stringResource(R.string.settings_about_licenses_subtitle),
          onClick = onLicenses,
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.license",
          icon = Icons.Outlined.Article,
          label = stringResource(R.string.settings_about_license_label),
          subtitle = stringResource(R.string.settings_about_license_subtitle),
          onClick = { openExternalBrowser(context, LICENSE_URL) },
        )
      }
    }
  }

  if (ferretVisible) {
    EasterEggFerretDialog(onDismiss = { ferretVisible = false })
  }
}

@Composable
private fun EasterEggFerretDialog(onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .clickable(onClick = onDismiss)
        .semantics { testTag = "easter_egg_ferret_scrim" },
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = painterResource(id = R.drawable.easter_egg_ferret),
        contentDescription = stringResource(R.string.settings_about_easter_egg_ferret_cd),
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .fillMaxSize()
          .semantics { testTag = "easter_egg_ferret_image" },
      )
    }
  }
}

private const val GITHUB_URL = "https://github.com/887/pageboy"
private const val LICENSE_URL = "https://github.com/887/pageboy/blob/main/LICENSE"

/**
 * Open a URL in the user's default external browser, NOT in any
 * embedded WebView / Chrome Custom Tab. The CATEGORY_BROWSABLE +
 * Browser.EXTRA_APPLICATION_ID combination forces the system to route
 * through the configured browser-launcher only (mirrors tonearmboy's
 * pattern).
 */
private fun openExternalBrowser(context: android.content.Context, url: String) {
  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
    addCategory(Intent.CATEGORY_BROWSABLE)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    putExtra("com.android.browser.application_id", context.packageName)
  }
  runCatching { context.startActivity(intent) }
}
