/*
 * Copyright (C) 2026 The uwuAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemProperties
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.search.UniversalSearchActivity
import com.android.launcher3.R
import com.android.launcher3.SessionCommitReceiver
import com.android.launcher3.util.SettingsCache
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.framework.compose.localNavController
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category

private const val MAIN_ROUTE = "main"
private const val GRID_ROUTE = "grid"
private const val DOCK_ROUTE = "dock"
private const val SEARCH_ROUTE = "search"
private const val DRAWER_ROUTE = "drawer"
private const val FOLDER_ROUTE = "folder"
private const val ICON_PACK_ROUTE = "icon_pack"
private const val AT_A_GLANCE_ROUTE = "at_a_glance"
private const val HIDDEN_APPS_ROUTE = "hidden_apps"
private const val UNIVERSAL_SEARCH_SETTINGS_ROUTE = "universal_search_settings"

@Composable
fun LauncherSettingsScreen(startRoute: String? = null) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIconPack by remember {
        mutableStateOf(LauncherPrefs.get(context).get(LauncherPrefs.ICON_PACK_PACKAGE))
    }
    val launcherPrefs = remember { LauncherPrefs.get(context) }
    var developerOptionsEnabled by remember {
        mutableStateOf(launcherPrefs.get(LauncherPrefs.DEVELOPER_OPTIONS_ENABLED))
    }
    var versionInfoTapCount by rememberSaveable { mutableIntStateOf(0) }

    androidx.compose.runtime.CompositionLocalProvider(navController.localNavController()) {
        NavHost(
            navController = navController,
            startDestination = startRoute ?: MAIN_ROUTE,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300),
                    initialOffset = { it / 5 },
                ) + fadeIn(tween(300, delayMillis = 75))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300),
                    targetOffset = { it / 5 },
                ) + fadeOut(tween(75))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300),
                    initialOffset = { it / 5 },
                ) + fadeIn(tween(300, delayMillis = 75))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300),
                    targetOffset = { it / 5 },
                ) + fadeOut(tween(75))
            },
        ) {
            composable(MAIN_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.derived_app_name),
                    isFirstLayerPageWhenEmbedded = true,
                ) { padding ->
                    MainSettingsContent(
                        contentPadding = padding,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        selectedIconPack = selectedIconPack,
                        developerOptionsEnabled = developerOptionsEnabled,
                        onVersionInfoClick = {
                            if (!developerOptionsEnabled) {
                                versionInfoTapCount++
                                if (versionInfoTapCount >= 7) {
                                    developerOptionsEnabled = true
                                    launcherPrefs.put(
                                        LauncherPrefs.DEVELOPER_OPTIONS_ENABLED.to(true)
                                    )
                                }
                            }
                        },
                        onOpenRoute = { navController.navigate(it) },
                        onOpenGrid = { navController.navigate(GRID_ROUTE) },
                        onOpenDock = { navController.navigate(DOCK_ROUTE) },
                        onOpenSearch = { navController.navigate(SEARCH_ROUTE) },
                        onOpenDrawer = { navController.navigate(DRAWER_ROUTE) },
                         onOpenFolder = { navController.navigate(FOLDER_ROUTE) },
                         onOpenIconPack = { navController.navigate(ICON_PACK_ROUTE) },
                        onOpenHiddenApps = { navController.navigate(HIDDEN_APPS_ROUTE) },
                        onOpenUniversalSearchSettings = {
                            navController.navigate(UNIVERSAL_SEARCH_SETTINGS_ROUTE)
                        },
                    )
                }
            }
            composable(GRID_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.home_screen_grid),
                    actions = {
                        ResetAction {
                            resetGridSettings(context)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    GridSizeSettingsContent(contentPadding = padding)
                }
            }
            composable(DOCK_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.dock_settings),
                    actions = {
                        ResetAction {
                            resetDockSettings(context)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    DockSettingsContent(padding)
                }
            }
            composable(SEARCH_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.search_bar_settings),
                    actions = {
                        ResetAction {
                            resetSearchSettings(context)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    SearchSettingsContent(padding)
                }
            }
            composable(DRAWER_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.drawer_settings),
                    actions = {
                        ResetAction {
                            resetLayoutSettings(context, LayoutSettingsKind.Drawer)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    LayoutSettingsContent(LayoutSettingsKind.Drawer, padding)
                }
            }
            composable(FOLDER_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.folder_settings),
                    actions = {
                        ResetAction {
                            resetLayoutSettings(context, LayoutSettingsKind.Folder)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    LayoutSettingsContent(LayoutSettingsKind.Folder, padding)
                }
            }
            composable(ICON_PACK_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.icon_settings_title),
                    actions = {
                        ResetAction {
                            resetIconSizeSettings(context)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    IconSettingsContent(
                        contentPadding = padding,
                        selectedPackage = selectedIconPack,
                        onSelectedPackageChange = { selectedIconPack = it },
                    )
                }
            }
            composable(UNIVERSAL_SEARCH_SETTINGS_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.universal_search_title),
                ) { padding ->
                    UniversalSearchSettingsContent(contentPadding = padding)
                }
            }
            composable(HIDDEN_APPS_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.hidden_apps_title),
                ) { padding ->
                    HiddenAppsSettingsContent(contentPadding = padding)
                }
            }
            composable(AT_A_GLANCE_ROUTE) {
                SettingsScaffold(
                    title = context.getString(R.string.at_a_glance_title),
                    actions = {
                        ResetAction {
                            resetAtAGlanceSettings(context)
                            recreateSettings(context)
                        }
                    },
                ) { padding ->
                    AtAGlanceSettingsContent(padding)
                }
            }
        }
    }
}

@Composable
private fun ResetAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.RestartAlt,
            contentDescription = stringResource(R.string.action_reset),
        )
    }
}

private enum class SettingsIconTone {
    Primary,
    Secondary,
    Tertiary,
}

private fun settingsPreferenceIcon(
    imageVector: ImageVector,
    tone: SettingsIconTone,
): @Composable () -> Unit = {
    SettingsHomepageIcon(tone) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconForegroundColor(tone),
        )
    }
}

@Composable
private fun SettingsHomepageIcon(tone: SettingsIconTone, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = iconContainerColor(tone),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun iconContainerColor(tone: SettingsIconTone) = when (tone) {
    SettingsIconTone.Primary -> MaterialTheme.colorScheme.primaryContainer
    SettingsIconTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    SettingsIconTone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun iconForegroundColor(tone: SettingsIconTone) = when (tone) {
    SettingsIconTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    SettingsIconTone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    SettingsIconTone.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
}

private fun dockPreferenceIcon(): @Composable () -> Unit = {
    val starColor = MaterialTheme.colorScheme.onSecondaryContainer
    SettingsHomepageIcon(SettingsIconTone.Secondary) {
        Canvas(Modifier.size(20.dp)) {
            val star = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.08f)
                lineTo(size.width * 0.63f, size.height * 0.38f)
                lineTo(size.width * 0.96f, size.height * 0.41f)
                lineTo(size.width * 0.71f, size.height * 0.63f)
                lineTo(size.width * 0.78f, size.height * 0.94f)
                lineTo(size.width * 0.5f, size.height * 0.77f)
                lineTo(size.width * 0.22f, size.height * 0.94f)
                lineTo(size.width * 0.29f, size.height * 0.63f)
                lineTo(size.width * 0.04f, size.height * 0.41f)
                lineTo(size.width * 0.37f, size.height * 0.38f)
                close()
            }
            drawPath(
                path = star,
                color = starColor,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun SettingsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        }
    }
}

private data class SettingsSearchResult(
    val title: Int,
    val route: String,
    val keywords: String,
)

private val SETTINGS_SEARCH_RESULTS = listOf(
    SettingsSearchResult(R.string.home_screen_grid, GRID_ROUTE, "grid rows columns"),
    SettingsSearchResult(R.string.dock_settings, DOCK_ROUTE, "dock icons pages labels background"),
    SettingsSearchResult(R.string.search_bar_settings, SEARCH_ROUTE, "search provider qsb"),
    SettingsSearchResult(R.string.drawer_settings, DRAWER_ROUTE, "drawer applications"),
    SettingsSearchResult(R.string.folder_settings, FOLDER_ROUTE, "folder"),
    SettingsSearchResult(R.string.dock_rows, DOCK_ROUTE, "dock rows"),
    SettingsSearchResult(R.string.dock_pages, DOCK_ROUTE, "dock pages"),
    SettingsSearchResult(R.string.dock_background, DOCK_ROUTE, "dock background"),
    SettingsSearchResult(R.string.search_provider, SEARCH_ROUTE, "search provider"),
    SettingsSearchResult(R.string.show_dock_search, SEARCH_ROUTE, "search bar"),
    SettingsSearchResult(R.string.force_website_search, SEARCH_ROUTE, "website search"),
    SettingsSearchResult(R.string.match_drawer_search, SEARCH_ROUTE, "drawer search"),
    SettingsSearchResult(
        R.string.smartspacer_title,
        AT_A_GLANCE_ROUTE,
        "smartspace smartspacer download",
    ),
    SettingsSearchResult(
        R.string.at_a_glance_title,
        AT_A_GLANCE_ROUTE,
        "at a glance smartspace status",
    ),
    SettingsSearchResult(R.string.icon_settings_title, ICON_PACK_ROUTE, "icons icon pack theme"),
    SettingsSearchResult(R.string.home_icon_size, ICON_PACK_ROUTE, "home screen icon size"),
    SettingsSearchResult(R.string.drawer_icon_size, ICON_PACK_ROUTE, "drawer icon size"),
    SettingsSearchResult(R.string.hidden_apps_title, HIDDEN_APPS_ROUTE, "hide apps drawer hidden"),
)

private fun settingsRouteIcon(route: String): @Composable () -> Unit = when (route) {
    GRID_ROUTE -> settingsPreferenceIcon(Icons.Outlined.GridView, SettingsIconTone.Primary)
    DOCK_ROUTE -> dockPreferenceIcon()
    SEARCH_ROUTE -> settingsPreferenceIcon(Icons.Outlined.Search, SettingsIconTone.Tertiary)
    DRAWER_ROUTE -> settingsPreferenceIcon(Icons.Outlined.Apps, SettingsIconTone.Primary)
    FOLDER_ROUTE -> settingsPreferenceIcon(Icons.Outlined.FolderOpen, SettingsIconTone.Secondary)
    ICON_PACK_ROUTE -> settingsPreferenceIcon(Icons.Outlined.Palette, SettingsIconTone.Tertiary)
    AT_A_GLANCE_ROUTE -> settingsPreferenceIcon(Icons.Outlined.AutoAwesome, SettingsIconTone.Primary)
    HIDDEN_APPS_ROUTE -> settingsPreferenceIcon(Icons.Outlined.Clear, SettingsIconTone.Secondary)
    UNIVERSAL_SEARCH_SETTINGS_ROUTE ->
        settingsPreferenceIcon(Icons.Outlined.Search, SettingsIconTone.Primary)
    else -> settingsPreferenceIcon(Icons.Outlined.DeveloperMode, SettingsIconTone.Secondary)
}

@Composable
private fun SettingsSearchResults(query: String, onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val normalizedQuery = query.trim().lowercase()
    val results = SETTINGS_SEARCH_RESULTS.filter { result ->
        val title = context.getString(result.title)
        "$title ${result.keywords}".lowercase().contains(normalizedQuery)
    }

    Category(title = stringResource(R.string.settings_search_results)) {
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_search_no_results),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            results.forEach { result ->
                Preference(
                    model = object : PreferenceModel {
                        override val title = context.getString(result.title)
                        override val icon = settingsRouteIcon(result.route)
                        override val onClick = { onOpenRoute(result.route) }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainSettingsContent(
    contentPadding: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedIconPack: String,
    developerOptionsEnabled: Boolean,
    onVersionInfoClick: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onOpenGrid: () -> Unit,
    onOpenDock: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenIconPack: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenUniversalSearchSettings: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSearchBar(searchQuery, onSearchQueryChange)

        if (searchQuery.isNotBlank()) {
            SettingsSearchResults(searchQuery, onOpenRoute)
        } else {
            OneUiCategory(title = context.getString(R.string.settings_general_section)) {
                NotificationDotsPreference(context)
                if (BuildConfig.IS_STUDIO_BUILD || developerOptionsEnabled) {
                    OneUiPreference(
                        model = object : PreferenceModel {
                            override val title = context.getString(R.string.developer_options_title)
                            override val icon = settingsPreferenceIcon(
                                Icons.Outlined.DeveloperMode,
                                SettingsIconTone.Secondary,
                            )
                            override val onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                )
                            }
                        }
                    )
                }
                NavigationBarHintPreference(context)
            }
            OneUiCategory(title = context.getString(R.string.settings_home_screen_section)) {
                AddIconsToHomePreference(context)
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.at_a_glance_title)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.AutoAwesome,
                            SettingsIconTone.Primary,
                        )
                        override val onClick = { onOpenRoute(AT_A_GLANCE_ROUTE) }
                    }
                )
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.home_screen_grid)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.GridView,
                            SettingsIconTone.Primary,
                        )
                        override val onClick = onOpenGrid
                    }
                )
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.folder_settings)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.FolderOpen,
                            SettingsIconTone.Secondary,
                        )
                        override val onClick = onOpenFolder
                    }
                )
            }
            OneUiCategory(title = context.getString(R.string.settings_icons_section)) {
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.icon_settings_title)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.Palette,
                            SettingsIconTone.Tertiary,
                        )
                        override val onClick = onOpenIconPack
                    }
                )
            }
            OneUiCategory(title = context.getString(R.string.hidden_apps_section)) {
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.hidden_apps_title)
                        override val summary = {
                            context.getString(R.string.hidden_apps_summary)
                        }
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.Clear,
                            SettingsIconTone.Secondary,
                        )
                        override val onClick = onOpenHiddenApps
                    }
                )
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title =
                            context.getString(R.string.universal_search_settings_row_title)
                        override val summary = {
                            context.getString(R.string.universal_search_settings_row_summary)
                        }
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.Search,
                            SettingsIconTone.Primary,
                        )
                        override val onClick = onOpenUniversalSearchSettings
                    }
                )
                IosAppDrawerPreference(context)
                StackedRecentsPreference(context)
                FolderStylePreference(context)
                FolderSizePreference(context)
            }
            OneUiCategory(title = context.getString(R.string.settings_dock_section)) {
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.dock_settings)
                        override val icon = dockPreferenceIcon()
                        override val onClick = onOpenDock
                    }
                )
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.search_bar_settings)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.Search,
                            SettingsIconTone.Tertiary,
                        )
                        override val onClick = onOpenSearch
                    }
                )
            }
            OneUiCategory(title = context.getString(R.string.drawer_settings)) {
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.drawer_layout_settings)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.Apps,
                            SettingsIconTone.Primary,
                        )
                        override val onClick = onOpenDrawer
                    }
                )
            }
            Category {
                val release = SystemProperties.get("ro.uwu.release", "")
                OneUiPreference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.version_info)
                        override val icon = settingsPreferenceIcon(
                            Icons.Outlined.Info,
                            SettingsIconTone.Tertiary,
                        )
                        override val summary = {
                            if (release.isBlank()) context.getString(R.string.launcher_release_name)
                            else context.getString(R.string.launcher_release_summary, release)
                        }
                        override val onClick = onVersionInfoClick
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationDotsPreference(context: Context) {
    val settingsCache = remember { SettingsCache.INSTANCE.get(context) }
    val enabled = remember { mutableStateOf(settingsCache.getValue(SettingsCache.NOTIFICATION_BADGING_URI)) }

    OneUiPreference(
        model =
            object : PreferenceModel {
                override val title = context.getString(R.string.notification_dots_title)
                override val icon = settingsPreferenceIcon(
                    Icons.Outlined.Notifications,
                    SettingsIconTone.Primary,
                )
                override val summary = {
                    context.getString(
                        if (enabled.value) R.string.notification_dots_desc_on
                        else R.string.notification_dots_desc_off
                    )
                }
                override val onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_SETTINGS))
                }
            }
    )
}

@Composable
private fun FolderSizePreference(context: Context) {
    var size by remember(context) {
        mutableStateOf(LauncherPrefs.get(context).get(LauncherPrefs.FOLDER_SIZE))
    }
    val sizeNames = listOf(
        context.getString(R.string.folder_size_small),
        context.getString(R.string.folder_size_medium),
        context.getString(R.string.folder_size_large),
    )

    // Each tap moves to the next size. The change rebuilds the layout, so it goes through
    // applyLauncherSetting, which refreshes the device profile.
    OneUiPreference(
        model = object : PreferenceModel {
            override val title = context.getString(R.string.folder_size_title)
            override val summary = { sizeNames[size.coerceIn(0, sizeNames.lastIndex)] }
            override val icon = settingsPreferenceIcon(
                Icons.Outlined.Search,
                SettingsIconTone.Tertiary,
            )
            override val onClick = {
                val next = (size + 1) % sizeNames.size
                applyLauncherSetting(context, LauncherPrefs.FOLDER_SIZE.to(next))
                size = next
            }
        }
    )
}

@Composable
private fun FolderStylePreference(context: Context) {
    var style by remember(context) {
        mutableStateOf(LauncherPrefs.get(context).get(LauncherPrefs.FOLDER_STYLE))
    }
    val styleNames = listOf(
        context.getString(R.string.folder_style_default),
        context.getString(R.string.folder_style_coloros),
        context.getString(R.string.folder_style_oxygenos),
    )

    // Each tap moves to the next style, so the choice needs no extra screen.
    OneUiPreference(
        model = object : PreferenceModel {
            override val title = context.getString(R.string.folder_style_title)
            override val summary = { styleNames[style.coerceIn(0, styleNames.lastIndex)] }
            override val icon = settingsPreferenceIcon(
                Icons.Outlined.Search,
                SettingsIconTone.Tertiary,
            )
            override val onClick = {
                val next = (style + 1) % styleNames.size
                LauncherPrefs.get(context).put(LauncherPrefs.FOLDER_STYLE, next)
                style = next
            }
        }
    )
}

@Composable
private fun StackedRecentsPreference(context: Context) {
    var enabled by remember(context) {
        mutableStateOf(LauncherPrefs.get(context).get(LauncherPrefs.STACKED_RECENTS))
    }

    OneUiSwitchPreference(
        model = object : SwitchPreferenceModel {
            override val title = context.getString(R.string.stacked_recents_title)
            override val summary = { context.getString(R.string.stacked_recents_summary) }
            override val icon = settingsPreferenceIcon(
                Icons.Outlined.Search,
                SettingsIconTone.Tertiary,
            )
            override val checked = { enabled }
            override val onCheckedChange = { newChecked: Boolean ->
                LauncherPrefs.get(context).put(LauncherPrefs.STACKED_RECENTS, newChecked)
                enabled = newChecked
            }
        }
    )
}

@Composable
private fun IosAppDrawerPreference(context: Context) {
    var enabled by remember(context) {
        mutableStateOf(LauncherPrefs.get(context).get(LauncherPrefs.IOS_APP_DRAWER))
    }

    OneUiSwitchPreference(
        model = object : SwitchPreferenceModel {
            override val title = context.getString(R.string.ios_app_drawer_title)
            override val summary = { context.getString(R.string.ios_app_drawer_summary) }
            override val icon = settingsPreferenceIcon(
                Icons.Outlined.Search,
                SettingsIconTone.Tertiary,
            )
            override val checked = { enabled }
            override val onCheckedChange = { newChecked: Boolean ->
                LauncherPrefs.get(context).put(LauncherPrefs.IOS_APP_DRAWER, newChecked)
                enabled = newChecked
            }
        }
    )
}

@Composable
private fun NavigationBarHintPreference(context: Context) {
    var enabled by remember(context) {
        mutableStateOf(
            LauncherPrefs.get(context).get(LauncherPrefs.SHOW_GESTURE_NAVIGATION_HINT)
        )
    }

    OneUiSwitchPreference(
        model = object : SwitchPreferenceModel {
            override val title = context.getString(R.string.navigation_bar_hint_title)
            override val icon = settingsPreferenceIcon(
                Icons.Outlined.Gesture,
                SettingsIconTone.Secondary,
            )
            override val checked = { enabled }
            override val onCheckedChange = { newChecked: Boolean ->
                LauncherPrefs.get(context).put(
                    LauncherPrefs.SHOW_GESTURE_NAVIGATION_HINT,
                    newChecked,
                )
                enabled = newChecked
            }
        }
    )
}

@Composable
private fun AddIconsToHomePreference(context: Context) {
    val sharedPrefs =
        remember {
            context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
    var enabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, true))
    }

    OneUiSwitchPreference(
        model =
            object : SwitchPreferenceModel {
                override val title = context.getString(R.string.auto_add_shortcuts_label)
                override val icon = settingsPreferenceIcon(
                    Icons.Outlined.Add,
                    SettingsIconTone.Secondary,
                )
                override val summary = { context.getString(R.string.auto_add_shortcuts_description) }
                override val checked = { enabled }
                override val onCheckedChange = { newChecked: Boolean ->
                    enabled = newChecked
                    sharedPrefs
                        .edit()
                        .putBoolean(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, newChecked)
                        .apply()
                }
            }
    )
}

/** Universal Search options. Each switch is read by the search screen on open. */
@Composable
private fun UniversalSearchSettingsContent(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs.get(context) }
    var appsOn by remember {
        mutableStateOf(prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_APPS_ENABLED))
    }
    var contactsOn by remember {
        mutableStateOf(prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_CONTACTS_ENABLED))
    }
    var settingsOn by remember {
        mutableStateOf(prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_SETTINGS_ENABLED))
    }
    var historyOn by remember {
        mutableStateOf(prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_HISTORY_ENABLED))
    }
    var homeButtonOn by remember {
        mutableStateOf(prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_HOME_BUTTON))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Category(title = context.getString(R.string.universal_search_categories_section)) {
            SwitchPreference(
                model = object : SwitchPreferenceModel {
                    override val title = context.getString(R.string.universal_search_category_apps)
                    override val checked = { appsOn }
                    override val onCheckedChange = { on: Boolean ->
                        appsOn = on
                        prefs.put(LauncherPrefs.UNIVERSAL_SEARCH_APPS_ENABLED.to(on))
                    }
                }
            )
            SwitchPreference(
                model = object : SwitchPreferenceModel {
                    override val title =
                        context.getString(R.string.universal_search_category_contacts)
                    override val checked = { contactsOn }
                    override val onCheckedChange = { on: Boolean ->
                        contactsOn = on
                        prefs.put(LauncherPrefs.UNIVERSAL_SEARCH_CONTACTS_ENABLED.to(on))
                    }
                }
            )
            SwitchPreference(
                model = object : SwitchPreferenceModel {
                    override val title =
                        context.getString(R.string.universal_search_category_settings)
                    override val checked = { settingsOn }
                    override val onCheckedChange = { on: Boolean ->
                        settingsOn = on
                        prefs.put(LauncherPrefs.UNIVERSAL_SEARCH_SETTINGS_ENABLED.to(on))
                    }
                }
            )
        }
        Category(title = context.getString(R.string.universal_search_home_section)) {
            SwitchPreference(
                model = object : SwitchPreferenceModel {
                    override val title =
                        context.getString(R.string.universal_search_home_button_title)
                    override val summary = {
                        context.getString(R.string.universal_search_home_button_summary)
                    }
                    override val checked = { homeButtonOn }
                    override val onCheckedChange = { on: Boolean ->
                        homeButtonOn = on
                        prefs.put(LauncherPrefs.UNIVERSAL_SEARCH_HOME_BUTTON.to(on))
                    }
                }
            )
        }
        Category(title = context.getString(R.string.universal_search_privacy_section)) {
            SwitchPreference(
                model = object : SwitchPreferenceModel {
                    override val title = context.getString(R.string.universal_search_history_title)
                    override val summary = {
                        context.getString(R.string.universal_search_history_summary)
                    }
                    override val checked = { historyOn }
                    override val onCheckedChange = { on: Boolean ->
                        historyOn = on
                        prefs.put(LauncherPrefs.UNIVERSAL_SEARCH_HISTORY_ENABLED.to(on))
                    }
                }
            )
        }
        Category(title = context.getString(R.string.universal_search_title)) {
            Preference(
                model = object : PreferenceModel {
                    override val title = context.getString(R.string.universal_search_open_title)
                    override val summary = {
                        context.getString(R.string.universal_search_open_summary)
                    }
                    override val onClick = {
                        context.startActivity(
                            Intent(context, UniversalSearchActivity::class.java)
                        )
                    }
                }
            )
        }
    }
}

private data class LaunchableApp(val key: String, val label: String)

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcherIntent, 0)
        .map { resolveInfo ->
            val component = ComponentName(
                resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name,
            )
            LaunchableApp(component.flattenToString(), resolveInfo.loadLabel(pm).toString())
        }
        .distinctBy { it.key }
        .sortedBy { it.label.lowercase() }
}

private fun readHiddenAppKeys(prefs: LauncherPrefs): Set<String> =
    prefs.get(LauncherPrefs.HIDDEN_APPS)
        .split(",")
        .filter { it.isNotEmpty() }
        .toSet()

@Composable
private fun HiddenAppsSettingsContent(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs.get(context) }
    val apps = remember { loadLaunchableApps(context) }
    var hiddenKeys by remember { mutableStateOf(readHiddenAppKeys(prefs)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Category(title = context.getString(R.string.hidden_apps_section)) {
            if (apps.isEmpty()) {
                Preference(
                    model = object : PreferenceModel {
                        override val title = context.getString(R.string.hidden_apps_empty)
                    }
                )
            }
            apps.forEach { app ->
                SwitchPreference(
                    model = object : SwitchPreferenceModel {
                        override val title = app.label
                        override val checked = { app.key in hiddenKeys }
                        override val onCheckedChange = { hide: Boolean ->
                            val updated = if (hide) hiddenKeys + app.key else hiddenKeys - app.key
                            hiddenKeys = updated
                            prefs.put(LauncherPrefs.HIDDEN_APPS.to(updated.joinToString(",")))
                        }
                    }
                )
            }
        }
    }
}
