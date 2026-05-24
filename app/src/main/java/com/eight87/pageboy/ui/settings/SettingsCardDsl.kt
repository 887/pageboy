package com.eight87.pageboy.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.theme.CategoryAccent
import com.eight87.pageboy.theme.accentFor
import kotlinx.coroutines.delay

/**
 * Dimensions used by every settings card / row. Keeping these as
 * top-level constants makes the page-level layout consistent and the
 * "sitting in the middle" inset (16 dp horizontal page padding) easy
 * to read at the call site.
 */
object SettingsDimens {
  val PagePadding = 16.dp
  val CardCornerRadius = 16.dp
  val RowVerticalPadding = 14.dp
  val RowHorizontalPadding = 16.dp
  val IconSize = 24.dp
  val IconLabelGap = 16.dp
  val GroupTitleTopPadding = 20.dp
  val GroupTitleBottomPadding = 8.dp
  val CardSpacing = 16.dp
}

/**
 * The id of a settings row that should briefly highlight on entry, set
 * by a future search overlay before it navigates. Sub-pages observe
 * this and compare against each row's id; on match the row flashes its
 * background for ~300 ms and the value is cleared.
 *
 * Nullable (no row to highlight) is the default.
 */
val LocalHighlightedSettingId = compositionLocalOf<MutableState<String?>> {
  mutableStateOf(null)
}

/**
 * Material 3 card that hosts a group of related rows. Renders with
 * `RoundedCornerShape(16.dp)` corners and a tonal `surfaceContainerHigh`
 * background so it visually separates from the page background — the
 * "sitting in the middle" pattern from Android system Settings.
 *
 * Per m3-expressive.md gotcha #2, `surfaceContainerHigh` (not
 * `surfaceContainer`) is the AMOLED-correct default — the latter is
 * barely a step above `surface` on dark mode.
 *
 * @param title Optional small header rendered above the card. When set,
 *   it takes the same horizontal inset as the card itself so the labels
 *   line up with each row's icon column.
 */
@Composable
fun SettingsCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier = modifier) {
    if (title != null) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
          start = SettingsDimens.RowHorizontalPadding,
          top = SettingsDimens.GroupTitleTopPadding,
          bottom = SettingsDimens.GroupTitleBottomPadding,
        ),
      )
    }
    Card(
      shape = RoundedCornerShape(SettingsDimens.CardCornerRadius),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "settings_card" },
    ) {
      Column { content() }
    }
  }
}

/**
 * One row inside a [SettingsCard]. Always has a leading icon (the
 * Android Settings convention is "no orphan rows without icons") plus
 * a label and optional subtitle. The trailing slot hosts the
 * affordance — `Switch`, navigation chevron, picker indicator.
 *
 * If [id] matches [LocalHighlightedSettingId], the row briefly flashes
 * its background to draw the user's eye when a search overlay (future
 * surface) navigated here.
 *
 * Per m3-expressive.md gotcha #3 — when caller passes `accent = null`
 * AND a non-null [id], we auto-derive the accent via `accentFor(id)`
 * so every settings row across the app gets the coloured-circle avatar
 * without each call site having to pass the parameter explicitly. Pass
 * `accent = null` AND a null `id` to opt out (e.g. a row with no
 * stable id).
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  id: String? = null,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  accent: CategoryAccent? = null,
) {
  val resolvedAccent = accent ?: id?.let { accentFor(it) }
  val highlightState = LocalHighlightedSettingId.current
  val highlighted = id != null && highlightState.value == id
  val target =
    if (highlighted) MaterialTheme.colorScheme.primaryContainer
    else Color.Transparent
  val animatedBg by animateColorAsState(
    targetValue = target,
    animationSpec = tween(durationMillis = 300),
    label = "settings_row_highlight",
  )

  if (highlighted) {
    LaunchedEffect(id) {
      delay(600)
      if (highlightState.value == id) highlightState.value = null
    }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(animatedBg)
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
      .padding(
        horizontal = SettingsDimens.RowHorizontalPadding,
        vertical = SettingsDimens.RowVerticalPadding,
      )
      .heightIn(min = 48.dp)
      .semantics { testTag = "settings_row_${id ?: label}" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (resolvedAccent != null) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(resolvedAccent.container),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = resolvedAccent.onContainer,
          modifier = Modifier.size(SettingsDimens.IconSize),
        )
      }
    } else {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(SettingsDimens.IconSize),
      )
    }
    Spacer(Modifier.size(SettingsDimens.IconLabelGap))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (trailing != null) {
      Spacer(Modifier.size(8.dp))
      Box { trailing() }
    }
  }
}

/**
 * Subtle divider used between rows inside a [SettingsCard]. Indented to
 * align with the row text (past the icon column) so the icons act as a
 * vertical guideline.
 */
@Composable
fun SettingsRowDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(
      start = SettingsDimens.RowHorizontalPadding +
        SettingsDimens.IconSize +
        SettingsDimens.IconLabelGap,
    ),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}
