/*
 * Copyright (C) 2026 The uwuAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities.shouldReduceWorkspaceBlurUsage
import com.android.launcher3.icons.iconpack.IconPackEntry
import com.android.launcher3.icons.iconpack.IconPackRepository
import com.android.launcher3.icons.iconpack.IconOverrideRepository
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.WindowBlurState
import com.android.launcher3.views.DialogTheme
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class LauncherIconCustomizeActivity : ComponentActivity() {
    private lateinit var componentKey: ComponentKey
    private lateinit var rootView: FrameLayout
    private var blurDrawable: BackgroundBlurDrawable? = null
    private var pendingImagePickerResult: ((Uri?) -> Unit)? = null
    private val imagePickerLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pendingImagePickerResult?.invoke(uri)
            pendingImagePickerResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply { dimAmount = 0f }

        val component = ComponentName.unflattenFromString(
            intent.getStringExtra(EXTRA_COMPONENT) ?: ""
        ) ?: return finish()
        val user = intent.getParcelableExtra(EXTRA_USER, UserHandle::class.java)
            ?: return finish()
        componentKey = ComponentKey(component, user)

        rootView = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        setContentView(rootView)
        rootView.addView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    DialogTheme {
                        val blurEnabled = !shouldReduceWorkspaceBlurUsage(this@LauncherIconCustomizeActivity) &&
                            WindowBlurState.getInstance(this@LauncherIconCustomizeActivity).value
                        LaunchedEffect(blurEnabled) {
                            if (blurEnabled) {
                                rootView.post { updateBlurBackground(rootView, 1f) }
                            } else {
                                rootView.background = null
                            }
                        }
                        CustomizeSheet(
                            componentKey,
                            onClose = { finish() },
                            onPickImage = { onResult ->
                                pendingImagePickerResult = onResult
                                imagePickerLauncher.launch("image/*")
                            },
                        )
                    }
                }
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        rootView.post {
            if (!shouldReduceWorkspaceBlurUsage(this) &&
                WindowBlurState.getInstance(this).value) {
                updateBlurBackground(rootView, 1f)
            }
        }
    }

    private fun updateBlurBackground(view: View, progress: Float) {
        if (blurDrawable == null) {
            blurDrawable = view.viewRootImpl?.createBackgroundBlurDrawable()
        }
        blurDrawable?.apply {
            setBlurRadius(
                (resources.getDimensionPixelSize(R.dimen.popup_blur_radius) *
                    progress.coerceIn(0f, 1f)).toInt()
            )
            setVisible(true, false)
        }
        view.background = blurDrawable
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !shouldReduceWorkspaceBlurUsage(this) &&
            WindowBlurState.getInstance(this).value) {
            rootView.post { updateBlurBackground(rootView, 1f) }
        }
    }

    override fun onDestroy() {
        blurDrawable?.setVisible(false, false)
        blurDrawable = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_COMPONENT = "component"
        private const val EXTRA_USER = "user"

        @JvmStatic
        fun createIntent(context: Context, key: ComponentKey) =
            android.content.Intent(context, LauncherIconCustomizeActivity::class.java).apply {
                putExtra(EXTRA_COMPONENT, key.componentName.flattenToString())
                putExtra(EXTRA_USER, key.user)
            }
    }
}

@Composable
private fun CustomizeSheet(
    key: ComponentKey,
    onClose: () -> Unit,
    onPickImage: ((Uri?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val packName = remember { LauncherPrefs.get(context).get(LauncherPrefs.ICON_PACK_PACKAGE) }
    val packNames = remember(packName) { packName.split(',').filter { it.isNotBlank() } }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    var editorHeightPx by remember { mutableIntStateOf(0) }
    var showCropEditor by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val keepEditorHeight = showIconPicker && packNames.isEmpty()
    val sheetVisibility = remember { MutableTransitionState(false) }
    var closeRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sheetVisibility.targetState = true
    }
    LaunchedEffect(sheetVisibility.isIdle, sheetVisibility.currentState, closeRequested) {
        if (closeRequested && sheetVisibility.isIdle && !sheetVisibility.currentState) {
            onClose()
        }
    }
    val closeSheet = {
        closeRequested = true
        sheetVisibility.targetState = false
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = closeSheet),
        )
        AnimatedVisibility(
            visibleState = sheetVisibility,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(animationSpec = tween(180)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showCropEditor) {
                            Modifier.fillMaxHeight()
                        } else if (keepEditorHeight && editorHeightPx > 0) {
                            Modifier.height(with(density) { editorHeightPx.toDp() })
                        } else {
                            Modifier.wrapContentHeight()
                        }
                    )
                    .heightIn(max = 640.dp)
                    .animateContentSize(
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f),
                tonalElevation = 0.dp,
            ) {
                CustomizeContent(
                    key = key,
                    onClose = closeSheet,
                    onPickImage = onPickImage,
                    packName = packName,
                    showIconPicker = showIconPicker,
                    onShowIconPickerChange = { showIconPicker = it },
                    onShowCropEditorChange = { showCropEditor = it },
                    onEditorHeightMeasured = { height -> editorHeightPx = maxOf(editorHeightPx, height) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomizeContent(
    key: ComponentKey,
    onClose: () -> Unit,
    onPickImage: ((Uri?) -> Unit) -> Unit,
    packName: String,
    showIconPicker: Boolean,
    onShowIconPickerChange: (Boolean) -> Unit,
    onShowCropEditorChange: (Boolean) -> Unit,
    onEditorHeightMeasured: (Int) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val packNames = remember(packName) { packName.split(',').filter { it.isNotBlank() } }
    val defaultTitle = remember {
        context.packageManager.getActivityInfo(key.componentName, 0).loadLabel(
            context.packageManager
        ).toString()
    }
    var title by remember {
        mutableStateOf(IconOverrideRepository.getLabel(context, key) ?: defaultTitle)
    }
    var showIconSourceOptions by remember { mutableStateOf(false) }
    var iconSearchQuery by rememberSaveable { mutableStateOf("") }
    var loadingIcons by remember { mutableStateOf(false) }
    var customImage by remember(key) { mutableStateOf<Drawable?>(null) }
    var cropBitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
    var cropUri by remember(key) { mutableStateOf<Uri?>(null) }
    var hasIconOverride by remember(key) {
        mutableStateOf(IconOverrideRepository.hasOverride(context, key))
    }
    var entries by remember(packNames) { mutableStateOf<List<IconPackEntry>>(emptyList()) }
    LaunchedEffect(packNames, showIconPicker) {
        if (showIconPicker) {
            loadingIcons = true
            entries = withContext(Dispatchers.IO) {
                runCatching {
                    IconPackRepository.getAvailablePacks(context).flatMap { pack ->
                        IconPackRepository.getPack(context, pack.packageName)?.getAllIcons().orEmpty()
                    }
                }.getOrDefault(emptyList())
            }
            loadingIcons = false
        }
    }
    LaunchedEffect(key, packName) {
        customImage = withContext(Dispatchers.IO) {
            IconOverrideRepository.getCustomImageDrawable(context, key)
                ?: run {
                    val entry = IconOverrideRepository.get(context, key)
                        ?: packNames.firstNotNullOfOrNull { packageName ->
                            IconPackRepository.getPack(context, packageName)?.getIcon(key.componentName)
                        }
                    entry?.let {
                        IconPackRepository.getDrawable(
                            context,
                            it,
                            context.resources.configuration.densityDpi,
                        )
                    }
                }
        }
    }
    val filteredEntries = entries.filter {
        it.drawableName.contains(iconSearchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 380,
                    easing = FastOutSlowInEasing,
                ),
            )
            .then(
                if (showIconPicker && packName.isEmpty() || cropBitmap != null) Modifier.fillMaxHeight()
                else Modifier.wrapContentHeight()
            )
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .onSizeChanged { size ->
                if (!showIconPicker && cropBitmap == null) onEditorHeightMeasured(size.height)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (cropBitmap != null) {
            IconImageCropEditor(
                bitmap = cropBitmap!!,
                onCancel = {
                    cropBitmap?.recycle()
                    cropBitmap = null
                    cropUri = null
                    onShowCropEditorChange(false)
                },
                onApply = { cropBounds ->
                    val sourceUri = cropUri
                    coroutineScope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            sourceUri?.let {
                                IconOverrideRepository.setCustomImage(context, key, it, cropBounds)
                            } ?: false
                        }
                        if (saved) {
                            customImage = withContext(Dispatchers.IO) {
                                IconOverrideRepository.getCustomImageDrawable(context, key)
                            }
                            hasIconOverride = true
                            LauncherAppState.getInstance(context).model
                                .reloadIfActive("custom-icon-image-changed")
                            cropBitmap?.recycle()
                            cropBitmap = null
                            cropUri = null
                            onShowCropEditorChange(false)
                        } else {
                            Toast.makeText(context, R.string.icon_image_load_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        } else if (showIconPicker) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onShowIconPickerChange(false) },
                    shape = IconButtonDefaults.smallRoundShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = context.getString(R.string.icon_picker_back),
                    )
                }
                Text(
                    text = context.getString(R.string.icon_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (IconPackRepository.getAvailablePacks(context).isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(R.string.icon_picker_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = iconSearchQuery,
                    onValueChange = { iconSearchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text(context.getString(R.string.icon_search)) },
                )
                if (loadingIcons) {
                    Text(
                        text = context.getString(R.string.icon_picker_loading),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (filteredEntries.isEmpty()) {
                    Text(
                        text = context.getString(
                            if (iconSearchQuery.isBlank()) R.string.icon_picker_no_icons
                            else R.string.icon_picker_no_results
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filteredEntries.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(56.dp),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredEntries, key = { "${it.packPackageName}:${it.drawableName}" }) { entry ->
                            IconPackPreview(entry) { drawable ->
                                IconOverrideRepository.set(context, key, entry)
                                customImage = drawable
                                hasIconOverride = true
                                LauncherAppState.getInstance(context).model
                                    .reloadIfActive("icon-customization-changed")
                                onShowIconPickerChange(false)
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showIconSourceOptions = !showIconSourceOptions }
                    .padding(8.dp),
            ) {
                val icon = remember {
                    runCatching {
                        context.packageManager.getActivityInfo(key.componentName, 0).loadIcon(
                            context.packageManager
                        )
                    }.getOrNull()
                }
                DrawablePreview(customImage ?: icon, 54)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = context.getString(R.string.customize_icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp).size(12.dp),
                    )
                }
            }
            if (showIconSourceOptions) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconSourceOption(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = context.getString(R.string.icon_choose_photo),
                        modifier = Modifier.weight(1f),
                    ) {
                        showIconSourceOptions = false
                        onPickImage { uri ->
                            if (uri != null) {
                                coroutineScope.launch {
                                    val decoded = withContext(Dispatchers.IO) {
                                        IconOverrideRepository.decodeCustomImage(context, uri)
                                    }
                                    if (decoded != null) {
                                        cropBitmap?.recycle()
                                        cropUri = uri
                                        cropBitmap = decoded
                                        onShowCropEditorChange(true)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.icon_image_load_failed,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                    IconSourceOption(
                        icon = Icons.Outlined.GridView,
                        title = context.getString(R.string.icon_choose_pack),
                        modifier = Modifier.weight(1f),
                    ) {
                        showIconSourceOptions = false
                        iconSearchQuery = ""
                        onShowIconPickerChange(true)
                    }
                }
            } else {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text(context.getString(R.string.icon_name_label)) },
                )
            }
            Text(
                text = key.componentName.flattenToString(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!showIconPicker) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (hasIconOverride) {
                    TextButton(onClick = {
                        IconOverrideRepository.remove(context, key)
                        customImage = null
                        hasIconOverride = false
                        LauncherAppState.getInstance(context).model
                            .reloadIfActive("custom-icon-reset")
                    }) {
                        Text(context.getString(R.string.reset_icon))
                    }
                }
                TextButton(onClick = onClose) {
                    Text(context.getString(R.string.icon_editor_cancel))
                }
                Button(onClick = {
                    IconOverrideRepository.setLabel(context, key, title)
                    LauncherAppState.getInstance(context).model.reloadIfActive("icon-label-changed")
                    onClose()
                }) {
                    Text(context.getString(R.string.icon_editor_save))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun IconImageCropEditor(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onApply: (RectF) -> Unit,
) {
    val context = LocalContext.current
    var zoom by remember(bitmap) { mutableStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewportSizePx by remember(bitmap) { mutableStateOf(1f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val maskColor = MaterialTheme.colorScheme.primary
    val shapePath = remember {
        runCatching {
            ThemeManager.INSTANCE.get(context).iconShape
                .getPath(100f).asComposePath()
        }.getOrElse {
            Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onCancel,
                shape = IconButtonDefaults.smallRoundShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = context.getString(R.string.icon_crop_cancel))
            }
            Text(
                text = context.getString(R.string.icon_crop_title),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(enabled = viewportSizePx > 1f, onClick = {
                val baseScale = maxOf(
                    viewportSizePx / bitmap.width,
                    viewportSizePx / bitmap.height,
                )
                val displayScale = baseScale * zoom
                val sourcePixelsPerViewport = viewportSizePx / displayScale
                val maxPanX = ((bitmap.width * displayScale - viewportSizePx) / 2f).coerceAtLeast(0f)
                val maxPanY = ((bitmap.height * displayScale - viewportSizePx) / 2f).coerceAtLeast(0f)
                val centerX = bitmap.width / 2f - pan.x.coerceIn(-maxPanX, maxPanX) / displayScale
                val centerY = bitmap.height / 2f - pan.y.coerceIn(-maxPanY, maxPanY) / displayScale
                val left = (centerX - sourcePixelsPerViewport / 2f).toInt()
                    .coerceIn(0, (bitmap.width - sourcePixelsPerViewport.toInt()).coerceAtLeast(0))
                val top = (centerY - sourcePixelsPerViewport / 2f).toInt()
                    .coerceIn(0, (bitmap.height - sourcePixelsPerViewport.toInt()).coerceAtLeast(0))
                val side = sourcePixelsPerViewport.toInt().coerceAtLeast(1)
                    .coerceAtMost(minOf(bitmap.width - left, bitmap.height - top))
                onApply(
                    RectF(
                        left.toFloat() / bitmap.width,
                        top.toFloat() / bitmap.height,
                        (left + side).toFloat() / bitmap.width,
                        (top + side).toFloat() / bitmap.height,
                    )
                )
            }) {
                Text(context.getString(R.string.icon_crop_use))
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                val viewportDp = minOf(maxWidth, maxHeight)
                val viewportSize = with(LocalDensity.current) { viewportDp.toPx() }
                LaunchedEffect(viewportSize) { viewportSizePx = viewportSize }
                val baseScale = maxOf(viewportSize / bitmap.width, viewportSize / bitmap.height)
                val imageWidth = bitmap.width * baseScale * zoom
                val imageHeight = bitmap.height * baseScale * zoom
                val maxPanX = ((imageWidth - viewportSize) / 2f).coerceAtLeast(0f)
                val maxPanY = ((imageHeight - viewportSize) / 2f).coerceAtLeast(0f)
                val boundedPan = Offset(
                    pan.x.coerceIn(-maxPanX, maxPanX),
                    pan.y.coerceIn(-maxPanY, maxPanY),
                )
                LaunchedEffect(boundedPan) { if (pan != boundedPan) pan = boundedPan }
                Canvas(
                    modifier = Modifier
                        .size(viewportDp)
                        .pointerInput(bitmap, viewportSize) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                pan = Offset(pan.x + panChange.x, pan.y + panChange.y)
                            }
                        },
                ) {
                    val imageWidthPx = bitmap.width * baseScale * zoom
                    val imageHeightPx = bitmap.height * baseScale * zoom
                    val left = (size.width - imageWidthPx) / 2f + boundedPan.x
                    val top = (size.height - imageHeightPx) / 2f + boundedPan.y
                    clipRect {
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(imageWidthPx.toInt(), imageHeightPx.toInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                    val bounds = shapePath.getBounds()
                    val shapeScale = minOf(size.width / bounds.width, size.height / bounds.height)
                    val shapeLeft = (size.width - bounds.width * shapeScale) / 2f - bounds.left * shapeScale
                    val shapeTop = (size.height - bounds.height * shapeScale) / 2f - bounds.top * shapeScale
                    val mask = Path().apply {
                        addPath(shapePath)
                        transform(androidx.compose.ui.graphics.Matrix().apply {
                            translate(shapeLeft, shapeTop)
                            scale(shapeScale, shapeScale)
                        })
                    }
                    val outside = Path.combine(
                        PathOperation.Difference,
                        Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) },
                        mask,
                    )
                    drawPath(outside, Color.Black.copy(alpha = 0.58f))
                    drawPath(mask, maskColor, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        Text(
            text = context.getString(R.string.icon_crop_instructions),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Slider(
            value = zoom,
            onValueChange = { zoom = it },
            valueRange = 1f..4f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun IconSourceOption(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun IconPackPreview(entry: IconPackEntry, onClick: (Drawable?) -> Unit) {
    val context = LocalContext.current
    var drawable by remember(entry) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(entry) {
        drawable = withContext(Dispatchers.IO) {
            IconPackRepository.getDrawable(
                context,
                entry,
                context.resources.configuration.densityDpi,
            )
        }
    }
    Box(
        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small).clickable {
            onClick(drawable)
        },
        contentAlignment = Alignment.Center,
    ) {
        DrawablePreview(drawable, 48)
    }
}

@Composable
private fun DrawablePreview(drawable: Drawable?, size: Int) {
    val context = LocalContext.current
    AndroidView(
        factory = { ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
        update = { it.setImageDrawable(drawable) },
        modifier = Modifier.size(size.dp),
    )
}
