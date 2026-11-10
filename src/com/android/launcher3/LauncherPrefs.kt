/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.android.launcher3.GridType.Companion.GRID_TYPE_ANY
import com.android.launcher3.InvariantDeviceProfile.GRID_NAME_PREFS_KEY
import com.android.launcher3.InvariantDeviceProfile.NON_FIXED_LANDSCAPE_GRID_NAME_PREFS_KEY
import com.android.launcher3.LauncherFiles.DEVICE_PREFERENCES_KEY
import com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.popup.ui.ExpandedSection
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.provider.RestoreDbTask.Companion.FIRST_LOAD_AFTER_RESTORE_KEY
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DefaultsValueProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Manages Launcher [SharedPreferences] through [Item] instances.
 *
 * TODO(b/262721340): Replace all direct SharedPreference refs with LauncherPrefs / Item methods.
 */
@LauncherAppSingleton
open class LauncherPrefs
@Inject
constructor(@ApplicationContext private val encryptedContext: Context) {

    private val deviceProtectedSharedPrefs: SharedPreferences by lazy {
        encryptedContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(BOOT_AWARE_PREFS_KEY, MODE_PRIVATE)
    }

    protected open fun getSharedPrefs(item: Item): SharedPreferences =
        item.run {
            if (encryptionType == EncryptionType.DEVICE_PROTECTED) deviceProtectedSharedPrefs
            else encryptedContext.getSharedPreferences(sharedPrefFile, MODE_PRIVATE)
        }

    @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
    val backedUpPrefs: SharedPreferences
        get() = getSharedPrefs(GRID_NAME)

    @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
    val devicePrefs: SharedPreferences
        get() = getSharedPrefs(IS_FIRST_LOAD_AFTER_RESTORE)

    /** Returns the value with type [T] for [item]. */
    fun <T> get(item: ContextualItem<T>): T =
        getInner(item, item.defaultValueFromContext(encryptedContext))

    /** Returns the value with type [T] for [item]. */
    fun <T> get(item: ConstantItem<T>): T = getInner(item, item.defaultValue)

    /**
     * Retrieves the value for an [Item] from [SharedPreferences]. It handles method typing via the
     * default value type, and will throw an error if the type of the item provided is not a
     * `String`, `Boolean`, `Float`, `Int`, `Long`, or `Set<String>`.
     */
    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    private fun <T> getInner(item: Item, default: T): T {
        val sp = getSharedPrefs(item)
        return when {
            item.type == String::class.java -> sp.getString(item.sharedPrefKey, default as? String)
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                sp.getBoolean(item.sharedPrefKey, default as Boolean)
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                sp.getInt(item.sharedPrefKey, default as Int)
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                sp.getFloat(item.sharedPrefKey, default as Float)
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                sp.getLong(item.sharedPrefKey, default as Long)
            Set::class.java.isAssignableFrom(item.type) ->
                sp.getStringSet(item.sharedPrefKey, default as? Set<String>)
            item.type.isEnum -> {
                val name = sp.getString(item.sharedPrefKey, (default as Enum<*>).name)
                item.type.enumConstants?.find { (it as Enum<*>).name == name } ?: default
            }
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type}" + " is not compatible with sharedPref methods"
                )
        }
            as T
    }

    /**
     * Stores each of the values provided in `SharedPreferences` according to the configuration
     * contained within the associated items provided. Internally, it uses apply, so the caller
     * cannot assume that the values that have been put are immediately available for use.
     *
     * The forEach loop is necessary here since there is 1 `SharedPreference.Editor` returned from
     * prepareToPutValue(itemsToValues) for every distinct `SharedPreferences` file present in the
     * provided item configurations.
     */
    fun put(vararg itemsToValues: Pair<Item, Any>): Unit =
        prepareToPutValues(itemsToValues).forEach { it.apply() }

    /** See referenced `put` method above. */
    fun <T : Any> put(item: Item, value: T): Unit = put(item.to(value))

    /**
     * Synchronously stores all the values provided according to their associated Item
     * configuration.
     */
    fun putSync(vararg itemsToValues: Pair<Item, Any>): Unit =
        prepareToPutValues(itemsToValues).forEach { it.commit() }

    /**
     * Updates the values stored in `SharedPreferences` for each corresponding Item-value pair. If
     * the item is boot aware, this method updates both the boot aware and the encrypted files. This
     * is done because: 1) It allows for easy roll-back if the data is already in encrypted prefs
     * and we need to turn off the boot aware data feature & 2) It simplifies Backup/Restore, which
     * already points to encrypted storage.
     *
     * Returns a list of editors with all transactions added so that the caller can determine to use
     * .apply() or .commit()
     */
    private fun prepareToPutValues(
        updates: Array<out Pair<Item, Any>>
    ): List<SharedPreferences.Editor> {
        val updatesPerPrefFile = updates.groupBy { getSharedPrefs(it.first) }.toMap()

        return updatesPerPrefFile.map { (sharedPref, itemList) ->
            sharedPref.edit().apply { itemList.forEach { (item, value) -> putValue(item, value) } }
        }
    }

    /**
     * Handles adding values to `SharedPreferences` regardless of type. This method is especially
     * helpful for updating `SharedPreferences` values for `List<<Item>Any>` that have multiple
     * types of Item values.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun SharedPreferences.Editor.putValue(
        item: Item,
        value: Any?,
    ): SharedPreferences.Editor =
        when {
            item.type == String::class.java -> putString(item.sharedPrefKey, value as? String)
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                putBoolean(item.sharedPrefKey, value as Boolean)
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                putInt(item.sharedPrefKey, value as Int)
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                putFloat(item.sharedPrefKey, value as Float)
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                putLong(item.sharedPrefKey, value as Long)
            Set::class.java.isAssignableFrom(item.type) ->
                putStringSet(item.sharedPrefKey, value as? Set<String>)
            item.type.isEnum -> putString(item.sharedPrefKey, (value as Enum<*>).name)
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type} is not compatible with sharedPref methods"
                )
        }

    /**
     * After calling this method, the listener will be notified of any future updates to the
     * `SharedPreferences` files associated with the provided list of items. The listener will need
     * to filter update notifications so they don't activate for non-relevant updates.
     */
    fun addListener(listener: LauncherPrefChangeListener, vararg items: Item) {
        items
            .map { getSharedPrefs(it) }
            .distinct()
            .forEach { it.registerOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Stops the listener from getting notified of any more updates to any of the
     * `SharedPreferences` files associated with any of the provided list of [Item].
     */
    fun removeListener(listener: LauncherPrefChangeListener, vararg items: Item) {
        // If a listener is not registered to a SharedPreference, unregistering it does nothing
        items
            .map { getSharedPrefs(it) }
            .distinct()
            .forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Checks if all the provided [Item] have values stored in their corresponding
     * `SharedPreferences` files.
     */
    fun has(vararg items: Item): Boolean {
        items
            .groupBy { getSharedPrefs(it) }
            .forEach { (prefs, itemsSublist) ->
                if (!itemsSublist.none { !prefs.contains(it.sharedPrefKey) }) return false
            }
        return true
    }

    /**
     * Asynchronously removes the [Item]'s value from its corresponding `SharedPreferences` file.
     */
    fun remove(vararg items: Item) = prepareToRemove(items).forEach { it.apply() }

    /** Synchronously removes the [Item]'s value from its corresponding `SharedPreferences` file. */
    fun removeSync(vararg items: Item) = prepareToRemove(items).forEach { it.commit() }

    /**
     * Removes the key value pairs stored in `SharedPreferences` for each corresponding Item. If the
     * item is boot aware, this method removes the data from both the boot aware and encrypted
     * files.
     *
     * @return a list of editors with all transactions added so that the caller can determine to use
     *   .apply() or .commit()
     */
    private fun prepareToRemove(items: Array<out Item>): List<SharedPreferences.Editor> {
        val itemsPerFile = items.groupBy { getSharedPrefs(it) }.toMap()

        return itemsPerFile.map { (prefs, items) ->
            prefs.edit().also { editor ->
                items.forEach { item -> editor.remove(item.sharedPrefKey) }
            }
        }
    }

    companion object {
        @VisibleForTesting const val BOOT_AWARE_PREFS_KEY = "boot_aware_prefs"

        const val COMPOSITION_TRACING_PREF_KEY = "pref_enableCompositionTracing"

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getLauncherPrefs)

        @JvmStatic fun get(context: Context): LauncherPrefs = INSTANCE.get(context)

        const val TASKBAR_PINNING_KEY = "TASKBAR_PINNING_KEY"
        const val TASKBAR_PINNING_DESKTOP_MODE_KEY = "TASKBAR_PINNING_DESKTOP_MODE_KEY"

        const val ENABLE_TWO_LINE_TOGGLE_KEY: String = "pref_enable_two_line_toggle"

        @JvmField
        val ENABLE_TWOLINE_ALLAPPS_TOGGLE =
            backedUpItem(ENABLE_TWO_LINE_TOGGLE_KEY, Boolean::class.java) { context ->
                DefaultsValueProvider.get(context).enableTwoLineToggle
            }

        @JvmField
        val PROMISE_ICON_IDS = nonRestorableItem(InstallSessionHelper.PROMISE_ICON_IDS, "")
        @JvmField val WORK_EDU_STEP = backedUpItem("showed_work_profile_edu", 0)
        @JvmField
        val WORKSPACE_SIZE =
            backedUpItem(DeviceGridState.KEY_WORKSPACE_SIZE, "", EncryptionType.ENCRYPTED)
        @JvmField
        val HOTSEAT_COUNT =
            backedUpItem(DeviceGridState.KEY_HOTSEAT_COUNT, -1, EncryptionType.ENCRYPTED)
        /** Comma-separated flattened ComponentNames of apps hidden from the app drawer. */
        @JvmField
        val HIDDEN_APPS = backedUpItem("hidden_apps", "", EncryptionType.ENCRYPTED)
        /** Universal Search: which result categories are searched. All on by default. */
        @JvmField val UNIVERSAL_SEARCH_APPS_ENABLED = backedUpItem("universal_search_apps", true)
        @JvmField
        val UNIVERSAL_SEARCH_CONTACTS_ENABLED = backedUpItem("universal_search_contacts", true)
        @JvmField
        val UNIVERSAL_SEARCH_SETTINGS_ENABLED = backedUpItem("universal_search_settings", true)
        /** Universal Search: whether recent queries are saved and shown. */
        @JvmField
        val UNIVERSAL_SEARCH_HISTORY_ENABLED = backedUpItem("universal_search_history", true)
        /** Shows a Universal Search button at the end of the home screen search bar. */
        @JvmField
        val UNIVERSAL_SEARCH_HOME_BUTTON = backedUpItem("universal_search_home_button", true)
        /** Groups apps by category under headers, with no chips or A-Z index. */
        @JvmField
        val IOS_APP_DRAWER = backedUpItem("ios_app_drawer", false)
        /** Stacked recents: cards further from the current page are drawn a little smaller. */
        @JvmField
        val STACKED_RECENTS = backedUpItem("stacked_recents", false)
        /** Folder look: 0 keeps the theme, 1 is ColorOS frosted, 2 is OxygenOS solid. */
        @JvmField
        val FOLDER_STYLE = backedUpItem("folder_style", 0)
        /** Folder size: 0 small, 1 medium (the default), 2 large. */
        @JvmField
        val FOLDER_SIZE = backedUpItem("folder_size", 1)
        /** Opens apps with a decelerating curve and a shorter close, in the ColorOS style. */
        @JvmField
        val COLOROS_APP_ANIMATIONS = backedUpItem("coloros_app_animations", false)
        @JvmField
        val TASKBAR_PINNING =
            backedUpItem(TASKBAR_PINNING_KEY, false, EncryptionType.DEVICE_PROTECTED)
        @JvmField
        val TASKBAR_PINNING_IN_DESKTOP_MODE =
            backedUpItem(TASKBAR_PINNING_DESKTOP_MODE_KEY, true, EncryptionType.DEVICE_PROTECTED)
        @JvmField
        val SHOW_GESTURE_NAVIGATION_HINT =
            backedUpItem("show_gesture_navigation_hint", true, EncryptionType.DEVICE_PROTECTED)

        @JvmField
        val DEVICE_TYPE =
            backedUpItem(
                DeviceGridState.KEY_DEVICE_TYPE,
                InvariantDeviceProfile.TYPE_PHONE,
                EncryptionType.ENCRYPTED,
            )
        @JvmField
        val DB_FILE = backedUpItem(DeviceGridState.KEY_DB_FILE, "", EncryptionType.ENCRYPTED)
        @JvmField
        val GRID_TYPE =
            backedUpItem(DeviceGridState.KEY_GRID_TYPE, GRID_TYPE_ANY, EncryptionType.ENCRYPTED)
        @JvmField
        val RESTORE_DEVICE =
            backedUpItem(
                RestoreDbTask.RESTORED_DEVICE_TYPE,
                InvariantDeviceProfile.TYPE_PHONE,
                EncryptionType.ENCRYPTED,
            )
        @JvmField
        val NO_DB_FILES_RESTORED =
            nonRestorableItem("no_db_files_restored", false, EncryptionType.DEVICE_PROTECTED)
        @JvmField
        val IS_FIRST_LOAD_AFTER_RESTORE =
            nonRestorableItem(FIRST_LOAD_AFTER_RESTORE_KEY, false, EncryptionType.ENCRYPTED)
        @JvmField val APP_WIDGET_IDS = backedUpItem(RestoreDbTask.APPWIDGET_IDS, "")
        @JvmField val OLD_APP_WIDGET_IDS = backedUpItem(RestoreDbTask.APPWIDGET_OLD_IDS, "")

        @JvmField
        val GRID_NAME =
            ConstantItem(
                GRID_NAME_PREFS_KEY,
                isBackedUp = true,
                defaultValue = null,
                encryptionType = EncryptionType.ENCRYPTED,
                type = String::class.java,
            )

        @JvmField val ENABLE_COMPOSITION_TRACING = backedUpItem(COMPOSITION_TRACING_PREF_KEY, false)

        @JvmField
        val FIXED_LANDSCAPE_MODE = backedUpItem(SettingsActivity.FIXED_LANDSCAPE_MODE, false)

        @JvmField
        val SMARTSPACER_ENABLED =
            backedUpItem(SettingsActivity.SMARTSPACER_PREFERENCE_KEY, false)

        @JvmField
        val SHOW_AT_A_GLANCE =
            backedUpItem(SettingsActivity.SHOW_AT_A_GLANCE_PREFERENCE_KEY, true)

        @JvmField
        val SMARTSPACER_INSTALL_PROMPT_DISMISSED = backedUpItem(
            SettingsActivity.SMARTSPACER_INSTALL_PROMPT_DISMISSED_PREFERENCE_KEY,
            false,
        )

        @JvmField
        val ICON_PACK_PACKAGE = backedUpItem("pref_icon_pack_package", "")

        @JvmField
        val THEMED_ICONS_ON_WORKSPACE = backedUpItem("pref_themed_icons_on_workspace", true)

        @JvmField
        val THEMED_ICONS_IN_DRAWER = backedUpItem("pref_themed_icons_in_drawer", true)

        @JvmField
        val DEVELOPER_OPTIONS_ENABLED = backedUpItem("pref_developer_options_enabled", false)

        @JvmField
        val WORKSPACE_ITEMS_LABEL_HIDDEN = backedUpItem("pref_workspace_items_label_hidden", false)

        // Customizable grid size preferences. A value of -1 means "unset": fall back to the value
        // defined by the selected device profile (GridOption).
        @JvmField
        val WORKSPACE_COLUMNS = backedUpItem("pref_workspace_columns", -1)

        @JvmField
        val WORKSPACE_ROWS = backedUpItem("pref_workspace_rows", -1)

        @JvmField
        val HOTSEAT_COLUMNS = backedUpItem("pref_hotseat_columns", -1)

        @JvmField
        val HOTSEAT_COLUMNS_UNFOLDED = backedUpItem("pref_hotseat_columns_unfolded", -1)

        @JvmField
        val HOTSEAT_ROWS = backedUpItem("pref_hotseat_rows", 1)

        @JvmField
        val DOCK_PAGES = backedUpItem("pref_dock_pages", 1)

        @JvmField
        val HOTSEAT_ENABLED = backedUpItem("pref_show_hotseat", true)

        @JvmField
        val HOTSEAT_MODE = backedUpItem("hotseat_mode", "lawnchair")

        @JvmField
        val HOTSEAT_BOTTOM_FACTOR = backedUpItem("hotseat_bottom_factor", 1.0f)

        @JvmField
        val HOTSEAT_PAGE_INDICATOR_HEIGHT_FACTOR =
            backedUpItem("page_indicator_height_factor", 1.0f)

        @JvmField
        val HOTSEAT_BACKGROUND_ENABLED = backedUpItem("pref_hotseatBG", false)

        @JvmField
        val HOTSEAT_BACKGROUND_COLOR = backedUpItem("hotseat_bg_color", 0)

        @JvmField
        val HOTSEAT_BACKGROUND_ALPHA = backedUpItem("pref_hotseatBGTransparency", 100)

        @JvmField
        val HOTSEAT_BACKGROUND_CORNER_RADIUS =
            backedUpItem("hotseat_bg_corner_radius", 24.0f)

        @JvmField
        val HOTSEAT_BACKGROUND_INSET_LEFT = backedUpItem("pref_hotseatBGHRinsetLeft", 0)

        @JvmField
        val HOTSEAT_BACKGROUND_INSET_TOP = backedUpItem("pref_hotseatBGVRinsetTop", 0)

        @JvmField
        val HOTSEAT_BACKGROUND_INSET_RIGHT = backedUpItem("pref_hotseatBGHRinsetRight", 0)

        @JvmField
        val HOTSEAT_BACKGROUND_INSET_BOTTOM = backedUpItem("pref_hotseatBGVRinsetBottom", 0)

        @JvmField
        val HOTSEAT_QSB_PROVIDER = backedUpItem("dock_search_bar_provider", "google")

        @JvmField
        val APP_SEARCH_INPUT_MODE = backedUpItem("app_search_input_mode", "native")

        @JvmField
        val HOTSEAT_QSB_FORCE_WEBSITE =
            backedUpItem("dock_search_bar_force_website", false)

        @JvmField
        val HOTSEAT_QSB_THEMED = backedUpItem("themed_hotseat_qsb", true)

        @JvmField
        val HOTSEAT_QSB_CORNER_RADIUS = backedUpItem("pref_hotseatQsbCornerRadius", 1.0f)

        @JvmField
        val HOTSEAT_QSB_ALPHA = backedUpItem("pref_searchHotseatTranparency", 100)

        @JvmField
        val HOTSEAT_QSB_STROKE_WIDTH = backedUpItem("pref_searchStrokeWidth", 0.0f)

        /** Zero means to use the current theme accent color. */
        @JvmField
        val HOTSEAT_QSB_STROKE_COLOR = backedUpItem("stroke_color", 0)

        @JvmField
        val HOTSEAT_QSB_MATCH_DRAWER = backedUpItem("use_drawer_search_icon", false)

        @JvmField
        val FOLDER_ROWS = backedUpItem("pref_folder_rows", -1)

        @JvmField
        val FOLDER_COLUMNS = backedUpItem("pref_folder_columns", -1)

        @JvmField
        val DRAWER_COLUMNS = backedUpItem("pref_drawer_columns", -1)

        @JvmField
        val DRAWER_COLUMNS_UNFOLDED = backedUpItem("pref_drawer_columns_unfolded", -1)

        @JvmField
        val HOME_ICON_SIZE_FACTOR = backedUpItem("pref_home_icon_size_factor", 1.0f)

        @JvmField
        val DRAWER_ICON_SIZE_FACTOR = backedUpItem("pref_drawer_icon_size_factor", 1.0f)

        @JvmField
        val DRAWER_ICON_TEXT_SIZE_FACTOR =
            backedUpItem("pref_drawer_icon_text_size_factor", 1.0f)

        @JvmField
        val EXPANDED_POPUP_MENU_SECTION =
            backedUpItem("pref_expanded_popup_menu", ExpandedSection.SYSTEM)

        @JvmField
        val NON_FIXED_LANDSCAPE_GRID_NAME =
            ConstantItem(
                NON_FIXED_LANDSCAPE_GRID_NAME_PREFS_KEY,
                isBackedUp = true,
                defaultValue = null,
                encryptionType = EncryptionType.ENCRYPTED,
                type = String::class.java,
            )

        // Preferences for widget configurations
        @JvmField
        val RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN =
            backedUpItem("launcher.reconfigurable_widget_education_tip_seen", false)

        // Preferences for Launcher statistics
        /* Format: a string converted from List<Long>. Sorted descendingly (latest to earliest). */
        @JvmField
        val ALL_APPS_TIP_SHOWN_TIMESTAMPS =
            backedUpItem("launcher.all_apps_tip_shown_timestamps", "")

        @JvmField val ALL_APPS_OPEN_UP_COUNT = backedUpItem("launcher.all_apps_open_up_count", 0)

        @JvmField val INITIAL_TIMESTAMP = backedUpItem("launcher.initial_timestamp", 0L)

        @JvmField val SELECT_TIP_SEEN = backedUpItem("launcher.select_tip_seen", false)

        @JvmStatic
        fun <T> backedUpItem(
            sharedPrefKey: String,
            defaultValue: T,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
        ): ConstantItem<T> =
            ConstantItem(sharedPrefKey, isBackedUp = true, defaultValue, encryptionType)

        @JvmStatic
        fun <T> backedUpItem(
            sharedPrefKey: String,
            type: Class<out T>,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
            defaultValueFromContext: (c: Context) -> T,
        ): ContextualItem<T> =
            ContextualItem(
                sharedPrefKey,
                isBackedUp = true,
                defaultValueFromContext,
                encryptionType,
                type,
            )

        @JvmStatic
        fun <T> nonRestorableItem(
            sharedPrefKey: String,
            defaultValue: T,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
        ): ConstantItem<T> =
            ConstantItem(sharedPrefKey, isBackedUp = false, defaultValue, encryptionType)

        @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
        @JvmStatic
        fun getPrefs(context: Context) = INSTANCE[context].backedUpPrefs
    }
}

abstract class Item {
    abstract val sharedPrefKey: String
    abstract val isBackedUp: Boolean
    abstract val type: Class<*>
    abstract val encryptionType: EncryptionType
    val sharedPrefFile: String
        get() = if (isBackedUp) SHARED_PREFERENCES_KEY else DEVICE_PREFERENCES_KEY

    fun <T> to(value: T): Pair<Item, T> = Pair(this, value)
}

data class ConstantItem<T>(
    override val sharedPrefKey: String,
    override val isBackedUp: Boolean,
    val defaultValue: T,
    override val encryptionType: EncryptionType,
    // The default value can be null. If so, the type needs to be explicitly stated, or else NPE
    override val type: Class<out T> = defaultValue!!::class.java,
) : Item() {

    fun get(c: Context): T = LauncherPrefs.get(c).get(this)
}

data class ContextualItem<T>(
    override val sharedPrefKey: String,
    override val isBackedUp: Boolean,
    private val defaultSupplier: (c: Context) -> T,
    override val encryptionType: EncryptionType,
    override val type: Class<out T>,
) : Item() {
    private var default: T? = null

    fun defaultValueFromContext(context: Context): T {
        if (default == null) {
            default = defaultSupplier(context)
        }
        return default!!
    }

    fun get(c: Context): T = LauncherPrefs.get(c).get(this)
}

enum class EncryptionType {
    ENCRYPTED,
    DEVICE_PROTECTED,
}

/**
 * LauncherPrefs which delegates all lookup to [prefs] but uses the real prefs for initial values
 */
class ProxyPrefs(context: Context, private val prefs: SharedPreferences) : LauncherPrefs(context) {

    private val copiedPrefs = ConcurrentHashMap<SharedPreferences, Boolean>()

    override fun getSharedPrefs(item: Item): SharedPreferences {
        val originalPrefs = super.getSharedPrefs(item)
        // Copy all existing values, when the pref is accessed for the first time
        copiedPrefs.computeIfAbsent(originalPrefs) { op ->
            val editor = prefs.edit()
            op.all.forEach { (key, value) ->
                if (value != null) {
                    editor.putValue(backedUpItem(key, value), value)
                }
            }
            editor.commit()
        }
        return prefs
    }
}
