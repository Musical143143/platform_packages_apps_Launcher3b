/*
 * Copyright (C) 2026 The uwuAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.core.graphics.PathParser
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.iconpack.IconPackRepository
import com.android.launcher3.shapes.ShapesProvider
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.RadioPreferences
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.ui.Category

@Composable
fun IconSettingsContent(
    contentPadding: PaddingValues,
    selectedPackage: String,
    onSelectedPackageChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = LauncherPrefs.get(context)
    val themeManager = remember { ThemeManager.INSTANCE.get(context) }
    val shapes = remember { ShapesProvider.iconShapes.toList() }
    val packs = remember { IconPackRepository.getAvailablePacks(context) }
    val idp = remember { InvariantDeviceProfile.INSTANCE.get(context) }
    val initialHomeIconSizeFactor = prefs.get(LauncherPrefs.HOME_ICON_SIZE_FACTOR)
    val initialDrawerIconSizeFactor = prefs.get(LauncherPrefs.DRAWER_ICON_SIZE_FACTOR)
    var homeIconSizeFactor by remember(initialHomeIconSizeFactor) {
        mutableFloatStateOf(initialHomeIconSizeFactor)
    }
    var drawerIconSizeFactor by remember(initialDrawerIconSizeFactor) {
        mutableFloatStateOf(initialDrawerIconSizeFactor)
    }
    val initialShapeKey = remember { prefs.get(ThemeManager.PREF_ICON_SHAPE) }
    var appliedShapeKey by rememberSaveable { mutableStateOf(initialShapeKey) }
    var themedIconsEnabled by remember { mutableStateOf(themeManager.isMonoThemeEnabled) }
    var themedIconsOnWorkspace by remember {
        mutableStateOf(prefs.get(LauncherPrefs.THEMED_ICONS_ON_WORKSPACE))
    }
    var themedIconsInDrawer by remember {
        mutableStateOf(prefs.get(LauncherPrefs.THEMED_ICONS_IN_DRAWER))
    }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context.getString(R.string.home_screen_grid),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                GridOverridesPreview(
                    columns = idp.numColumns,
                    rows = idp.numRows,
                    hotseatColumns = idp.numShownHotseatIcons,
                    homeIconSizeFactor = homeIconSizeFactor,
                    iconShapeKey = appliedShapeKey,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context.getString(R.string.drawer_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DrawerIconPreview(
                    idp = idp,
                    iconSizeFactor = drawerIconSizeFactor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
        Category(title = context.getString(R.string.icon_size_settings_title)) {
            IconSizeSlider(
                title = R.string.home_icon_size,
                factor = initialHomeIconSizeFactor,
                onFactorChange = { homeIconSizeFactor = it },
                onValueChangeFinished = { factor ->
                    applyLauncherSetting(
                        context,
                        LauncherPrefs.HOME_ICON_SIZE_FACTOR.to(factor),
                    )
                },
            )
            IconSizeSlider(
                title = R.string.drawer_icon_size,
                factor = initialDrawerIconSizeFactor,
                onFactorChange = { drawerIconSizeFactor = it },
                onValueChangeFinished = { factor ->
                    applyLauncherSetting(
                        context,
                        LauncherPrefs.DRAWER_ICON_SIZE_FACTOR.to(factor),
                    )
                },
            )
        }
        Category {
            if (shapes.size > 1) {
                IconShapePreference(
                    context = context,
                    shapes = shapes,
                    previewIdp = idp,
                    selectedKey = appliedShapeKey,
                    onApply = { key ->
                        prefs.put(ThemeManager.PREF_ICON_SHAPE, key)
                        appliedShapeKey = key
                        LauncherAppState.getInstance(context).model
                            .reloadIfActive("icon-shape-changed")
                    },
                )
            }
            if (com.android.systemui.shared.Flags.workspaceItemsLabelHidden()) {
                AppLabelsPreference(context, prefs)
            }
            IconPackPreference(context, prefs, packs, selectedPackage, onSelectedPackageChange)
        }
        Category(title = context.getString(R.string.icon_themed_title)) {
            ThemedIconsPreference(
                context = context,
                enabled = themedIconsEnabled,
                onEnabledChange = { enabled ->
                    themedIconsEnabled = enabled
                    themeManager.isMonoThemeEnabled = enabled
                    LauncherAppState.getInstance(context).model
                        .reloadIfActive("icon-themed-changed")
                },
            )
            ThemedIconsLocationPreference(
                context = context,
                titleRes = R.string.icon_theme_workspace,
                checked = themedIconsOnWorkspace,
                enabled = themedIconsEnabled,
                onValueChange = { checked ->
                    themedIconsOnWorkspace = checked
                    prefs.put(LauncherPrefs.THEMED_ICONS_ON_WORKSPACE.to(checked))
                    LauncherAppState.getInstance(context).model
                        .reloadIfActive("workspace-themed-icons-changed")
                },
            )
            ThemedIconsLocationPreference(
                context = context,
                titleRes = R.string.icon_theme_drawer,
                checked = themedIconsInDrawer,
                enabled = themedIconsEnabled,
                onValueChange = { checked ->
                    themedIconsInDrawer = checked
                    prefs.put(LauncherPrefs.THEMED_ICONS_IN_DRAWER.to(checked))
                    LauncherAppState.getInstance(context).model
                        .reloadIfActive("drawer-themed-icons-changed")
                },
            )
        }
    }
}

@Composable
private fun ThemedIconsPreference(
    context: android.content.Context,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        object : SwitchPreferenceModel {
            override val title = context.getString(R.string.icon_theme_enabled)
            override val checked = { enabled }
            override val onCheckedChange: ((Boolean) -> Unit)? = { value: Boolean ->
                onEnabledChange(value)
            }
        }
    )
}

@Composable
private fun ThemedIconsLocationPreference(
    context: android.content.Context,
    titleRes: Int,
    checked: Boolean,
    enabled: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        object : SwitchPreferenceModel {
            override val title = context.getString(titleRes)
            override val checked = { checked }
            override val changeable = { enabled }
            override val onCheckedChange = onValueChange
        }
    )
}

@Composable
private fun IconShapePreference(
    context: android.content.Context,
    shapes: List<com.android.launcher3.shapes.IconShapeModel>,
    previewIdp: InvariantDeviceProfile,
    selectedKey: String,
    onApply: (String) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingShapeKey by rememberSaveable { mutableStateOf(selectedKey) }

    Preference(
        object : PreferenceModel {
            override val title = context.getString(R.string.icon_shape_title)
            override val summary = {
                shapes.firstOrNull { it.key == selectedKey }?.let {
                    context.getString(it.titleId)
                } ?: context.getString(shapes.first().titleId)
            }
            override val onClick = {
                pendingShapeKey = selectedKey
                dialogOpen = true
            }
        }
    )

    if (dialogOpen) {
        IconShapePickerDialog(
            shapes = shapes,
            previewIdp = previewIdp,
            selectedKey = pendingShapeKey,
            onSelectionChange = { pendingShapeKey = it },
            onApply = {
                onApply(it)
                dialogOpen = false
            },
            onDismiss = {
                dialogOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconShapePickerDialog(
    shapes: List<com.android.launcher3.shapes.IconShapeModel>,
    previewIdp: InvariantDeviceProfile,
    selectedKey: String,
    onSelectionChange: (String) -> Unit,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selectedIndexState = remember(selectedKey, shapes) {
        mutableIntStateOf(shapes.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0))
    }
    val shapeOptions = remember(context, shapes) {
        shapes.mapIndexed { index, shape ->
            ListPreferenceOption(
                id = index,
                text = context.getString(shape.titleId),
                icon = { ShapePreviewIcon(shape) },
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            GridOverridesPreview(
                columns = previewIdp.numColumns,
                rows = previewIdp.numRows,
                hotseatColumns = previewIdp.numShownHotseatIcons,
                iconShapeKey = selectedKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 20.dp),
            )
            RadioPreferences(
                object : ListPreferenceModel {
                    override val title = context.getString(R.string.icon_shape_title)
                    override val options = shapeOptions
                    override val selectedId = selectedIndexState
                    override val onIdSelected: (Int) -> Unit = { id ->
                        shapes.getOrNull(id)?.let { onSelectionChange(it.key) }
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.icon_editor_cancel))
                }
                Button(onClick = { onApply(selectedKey) }) {
                    Text(stringResource(R.string.icon_editor_save))
                }
            }
        }
    }
}

@Composable
private fun ShapePreviewIcon(shape: com.android.launcher3.shapes.IconShapeModel) {
    val path = remember(shape.pathString) {
        PathParser.createPathFromPathData(shape.pathString).asComposePath()
    }
    val shapeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(48.dp)) {
        val bounds = path.getBounds()
        val scale = minOf(size.width / bounds.width, size.height / bounds.height) * 0.72f
        val left = (size.width - bounds.width * scale) / 2f - bounds.left * scale
        val top = (size.height - bounds.height * scale) / 2f - bounds.top * scale
        withTransform({
            translate(left = left, top = top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(path, color = shapeColor.copy(alpha = 0.12f))
            drawPath(path, color = selectedColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun AppLabelsPreference(context: android.content.Context, prefs: LauncherPrefs) {
    var enabled by remember {
        mutableStateOf(!prefs.get(LauncherPrefs.WORKSPACE_ITEMS_LABEL_HIDDEN))
    }

    SwitchPreference(
        object : SwitchPreferenceModel {
            override val title = context.getString(R.string.icon_labels_title)
            override val summary = {
                context.getString(
                    if (enabled) R.string.icon_labels_summary_on
                    else R.string.icon_labels_summary_off
                )
            }
            override val checked = { enabled }
            override val onCheckedChange: ((Boolean) -> Unit)? = { value: Boolean ->
                enabled = value
                applyLauncherSetting(
                    context,
                    LauncherPrefs.WORKSPACE_ITEMS_LABEL_HIDDEN.to(!value),
                )
            }
        }
    )
}

@Composable
private fun IconPackPreference(
    context: android.content.Context,
    prefs: LauncherPrefs,
    packs: List<com.android.launcher3.icons.iconpack.IconPackInfo>,
    selectedPackage: String,
    onSelectedPackageChange: (String) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by remember(selectedPackage) {
        mutableStateOf(selectedPackage.split(',').filter { it.isNotBlank() })
    }
    val summary = selectedPackages.mapNotNull { packageName ->
        packs.firstOrNull { it.packageName == packageName }?.label
    }.joinToString(", ").ifEmpty { context.getString(R.string.icon_pack_none) }

    Preference(
        object : PreferenceModel {
            override val title = context.getString(R.string.icon_pack_select_title)
            override val summary = { summary }
            override val onClick = { dialogOpen = true }
        }
    )

    if (dialogOpen) {
        IconPackPickerSheet(
            context = context,
            packs = packs,
            selectedPackages = selectedPackages,
            onApply = { updated ->
                selectedPackages = updated
                val value = updated.joinToString(",")
                prefs.put(LauncherPrefs.ICON_PACK_PACKAGE, value)
                onSelectedPackageChange(value)
                LauncherAppState.getInstance(context).model
                    .reloadIfActive("icon-pack-changed")
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPackPickerSheet(
    context: android.content.Context,
    packs: List<com.android.launcher3.icons.iconpack.IconPackInfo>,
    selectedPackages: List<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(selectedPackages, packs) {
        mutableStateOf(selectedPackages.toList())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                text = context.getString(R.string.icon_pack_select_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            selected.toList().forEachIndexed { index, packageName ->
                val pack = packs.firstOrNull { it.packageName == packageName } ?: return@forEachIndexed
                IconPackPickerRow(
                    pack = pack,
                    selected = true,
                    priority = index + 1,
                    onToggle = { selected = selected.filterNot { it == packageName } },
                )
            }
            packs.filter { it.packageName !in selected }.forEach { pack ->
                IconPackPickerRow(
                    pack = pack,
                    selected = false,
                    priority = null,
                    onToggle = { selected = selected + pack.packageName },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.icon_editor_cancel))
                }
                Button(onClick = { onApply(selected) }) {
                    Text(stringResource(R.string.icon_editor_save))
                }
            }
        }
    }
}

@Composable
private fun IconPackPickerRow(
    pack: com.android.launcher3.icons.iconpack.IconPackInfo,
    selected: Boolean,
    priority: Int?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(pack.label, style = MaterialTheme.typography.bodyLarge)
            if (priority != null) {
                Text(
                    stringResource(R.string.icon_pack_priority, priority),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
