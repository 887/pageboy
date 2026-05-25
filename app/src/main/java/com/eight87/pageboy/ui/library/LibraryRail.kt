package com.eight87.pageboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.LibraryTab

@Composable
fun LibraryRail(
  tabs: List<LibraryTab>,
  selectedTab: LibraryTab,
  onSelectTab: (LibraryTab) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val railWidth = 52.dp

  val systemBarInsets = WindowInsets.systemBars.asPaddingValues()

  Box(
    modifier = modifier
      .fillMaxHeight()
      .requiredWidth(railWidth)
      .background(MaterialTheme.colorScheme.surface)
      .semantics { testTag = "library_rail" },
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(Modifier.size(systemBarInsets.calculateTopPadding() + 8.dp))
        tabs.forEach { tab ->
          RailTabItem(
            label = stringResource(tabLabelRes(tab)),
            tabName = tab.name,
            selected = tab == selectedTab,
            onClick = { onSelectTab(tab) },
          )
        }
      }
      IconButton(
        onClick = onOpenSettings,
        modifier = Modifier
          .padding(bottom = systemBarInsets.calculateBottomPadding() + 8.dp)
          .semantics { testTag = "rail_settings" },
      ) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = stringResource(R.string.nav_settings),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RailTabItem(
  label: String,
  tabName: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val labelColor =
    if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier = Modifier
      .size(width = 52.dp, height = 108.dp)
      .clickable(onClick = onClick)
      .semantics { testTag = "rail_tab_$tabName" },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = labelColor,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      maxLines = 1,
      modifier = Modifier
        .wrapContentSize(unbounded = true)
        .rotate(-90f),
    )
    if (selected) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .fillMaxHeight()
          .width(2.dp)
          .background(accent)
          .semantics { testTag = "rail_accent" },
      )
    }
  }
}

private fun tabLabelRes(tab: LibraryTab): Int = when (tab) {
  LibraryTab.Started -> R.string.library_tab_started
  LibraryTab.All -> R.string.library_tab_all
  LibraryTab.Recents -> R.string.library_tab_recents
  LibraryTab.Pinned -> R.string.library_tab_pinned
}
