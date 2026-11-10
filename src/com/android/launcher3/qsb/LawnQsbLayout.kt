/*
 * Copyright (C) 2026 Lawnchair
 * Copyright (C) 2026 The uwuAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.qsb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Item
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.util.Themes
import com.android.launcher3.search.UniversalSearchActivity
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import kotlin.math.roundToInt

class LawnQsbLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val launcher = ActivityContext.lookupContextNoThrow(context) as? Launcher
    private val prefs = LauncherPrefs.get(context)
    private val bar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isLongClickable = true
    }
    private val prefListener = LauncherPrefChangeListener { key ->
        if (QSB_PREFS.any { it.sharedPrefKey == key }) post { updateContent() }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateContent()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.addListener(prefListener, *QSB_PREFS)
        updateContent()
    }

    override fun onDetachedFromWindow() {
        prefs.removeListener(prefListener, *QSB_PREFS)
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateContent()
    }

    private fun updateContent() {
        val provider = QsbSearchProvider.fromId(prefs.get(LauncherPrefs.HOTSEAT_QSB_PROVIDER))
            .takeIf { it.isAvailable(context) }
            ?: QsbSearchProvider.resolveDefault(context)
        val voiceIntent = provider.createVoiceIntent().takeIf {
            provider.supportsVoice && context.canResolve(it)
        }
        val lensIntent = getLensIntent(context).takeIf {
            provider.supportsLens && context.canResolve(it)
        }
        val themed = prefs.get(LauncherPrefs.HOTSEAT_QSB_THEMED)
        SearchBarUI.applyBackground(bar)
        bar.contentDescription = context.getString(R.string.label_search)
        bar.setOnClickListener {
            launcher?.let {
                if (prefs.get(LauncherPrefs.HOTSEAT_QSB_MATCH_DRAWER)) it.toggleAllApps(true)
                else provider.launch(it, prefs.get(LauncherPrefs.HOTSEAT_QSB_FORCE_WEBSITE))
            }
        }
        bar.setOnLongClickListener { openOptions(); true }
        bar.removeAllViews()

        val searchIcon = if (themed) provider.themedIcon else provider.icon
        bar.addView(iconView(QsbIconState(
            QsbIconId.SEARCH, searchIcon,
            themed || searchIcon == R.drawable.ic_qsb_search,
            context.getString(R.string.label_search), provider.themingMethod,
        ), false))
        bar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val isGoogle = provider == QsbSearchProvider.GOOGLE ||
            provider == QsbSearchProvider.GOOGLE_GO ||
            provider == QsbSearchProvider.PIXEL_SEARCH
        if (voiceIntent != null) {
            bar.addView(iconView(QsbIconState(
                QsbIconId.MIC,
                if (isGoogle) R.drawable.ic_mic_color else R.drawable.ic_mic_flat,
                (isGoogle && themed) || !isGoogle,
                context.getString(R.string.label_voice_search),
                if (isGoogle) ThemingMethod.THEME_BY_LAYER_ID else ThemingMethod.TINT,
            ), true) { runCatching { context.startActivity(voiceIntent) } })
        }
        if (lensIntent != null) {
            bar.addView(iconView(QsbIconState(
                QsbIconId.LENS, R.drawable.ic_lens_color, themed,
                context.getString(R.string.label_lens),
            ), true) { runCatching { context.startActivity(lensIntent) } })
        }
        if (prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_HOME_BUTTON)) {
            bar.addView(iconView(QsbIconState(
                QsbIconId.SEARCH, R.drawable.ic_allapps_search, true,
                context.getString(R.string.universal_search_title),
            ), true) {
                context.startActivity(
                    Intent(context, UniversalSearchActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
        }
    }

    private fun iconView(icon: QsbIconState, clickable: Boolean, onClick: (() -> Unit)? = null): View {
        val width = resources.getDimensionPixelSize(R.dimen.uwu_qsb_icon_width)
        val iconSize = if (clickable) width -
            2 * resources.getDimensionPixelSize(R.dimen.uwu_qsb_icon_padding)
        else (24 * resources.displayMetrics.density).roundToInt()
        val image = ImageView(context).apply {
            setImageDrawable(themedDrawable(icon))
            contentDescription = icon.contentDescription
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return FrameLayout(context).apply {
            addView(image, LayoutParams(iconSize, iconSize, Gravity.CENTER))
            if (clickable) {
                isClickable = true
                isFocusable = true
                contentDescription = icon.contentDescription
                setOnClickListener { onClick?.invoke() }
            }
            layoutParams = LinearLayout.LayoutParams(width, LayoutParams.MATCH_PARENT)
        }
    }

    private fun themedDrawable(icon: QsbIconState) =
        requireNotNull(ResourcesCompat.getDrawable(resources, icon.resId, context.theme)).mutate().also {
            if (!icon.themed) return@also
            if (icon.method == ThemingMethod.THEME_BY_LAYER_ID && it is LayerDrawable) {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
                val colors = if (night) intArrayOf(
                    context.getColor(android.R.color.system_accent3_100),
                    context.getColor(android.R.color.system_accent1_400),
                    context.getColor(android.R.color.system_accent2_10),
                    context.getColor(android.R.color.system_accent1_200),
                ) else intArrayOf(
                    context.getColor(android.R.color.system_accent3_400),
                    context.getColor(android.R.color.system_accent1_500),
                    context.getColor(android.R.color.system_accent2_300),
                    context.getColor(android.R.color.system_accent1_600),
                )
                val ids = intArrayOf(R.id.qsbIconTintPrimary, R.id.qsbIconTintSecondary,
                    R.id.qsbIconTintTertiary, R.id.qsbIconTintQuaternary)
                for (index in 0 until it.numberOfLayers) {
                    val tintIndex = ids.indexOf(it.getId(index))
                    if (tintIndex >= 0) it.getDrawable(index).setTint(colors[tintIndex])
                }
            } else {
                it.setTint(Themes.getColorAccent(context))
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dp = launcher?.deviceProfile
        if (dp == null || !dp.deviceProperties.isPhone) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val cellWidth = DeviceProfile.calculateCellWidth(
            requestedWidth, dp.workspaceProfile.cellLayoutBorderSpacePx.x,
            dp.hotseatProfile.numShownIcons,
        )
        val iconSize = (dp.workspaceProfile.iconSizePx * 0.92f).toInt()
        val width = (requestedWidth - cellWidth + iconSize).coerceAtLeast(0)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    private fun openOptions() {
        val launcher = launcher ?: return
        val position = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(bar, position)
        OptionsPopupView.show<Launcher>(
            launcher, RectF(position),
            listOf(OptionsPopupView.OptionItem(
                context, R.string.action_customize, R.drawable.ic_setting,
                StatsLogManager.LauncherEvent.IGNORE,
            ) {
                launcher.startActivity(Intent(launcher, SettingsActivity::class.java).putExtra(
                    SettingsActivity.EXTRA_START_ROUTE, SettingsActivity.SEARCH_ROUTE,
                ))
                true
            }),
            true,
        )
    }

    companion object {
        private const val LENS_PACKAGE = "com.google.ar.lens"
        private const val LENS_ACTIVITY =
            "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"
        private val QSB_PREFS = arrayOf<Item>(
            LauncherPrefs.HOTSEAT_QSB_PROVIDER, LauncherPrefs.HOTSEAT_QSB_THEMED,
            LauncherPrefs.HOTSEAT_QSB_ALPHA, LauncherPrefs.HOTSEAT_QSB_CORNER_RADIUS,
            LauncherPrefs.HOTSEAT_QSB_STROKE_COLOR, LauncherPrefs.HOTSEAT_QSB_STROKE_WIDTH,
            LauncherPrefs.HOTSEAT_QSB_MATCH_DRAWER, LauncherPrefs.HOTSEAT_QSB_FORCE_WEBSITE,
            LauncherPrefs.UNIVERSAL_SEARCH_HOME_BUTTON,
        )

        fun getLensIntent(context: Context): Intent =
            Intent.makeMainActivity(ComponentName(LENS_PACKAGE, LENS_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}

private fun Context.canResolve(intent: Intent): Boolean = packageManager.resolveActivity(intent, 0) != null
