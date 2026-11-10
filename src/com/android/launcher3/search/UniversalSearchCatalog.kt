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
package com.android.launcher3.search

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import androidx.annotation.StringRes
import com.android.launcher3.R

enum class UniversalSearchCategory(@StringRes val titleRes: Int) {
    APPLICATIONS(R.string.universal_search_category_apps),
    CONTACTS(R.string.universal_search_category_contacts),
    SETTINGS(R.string.universal_search_category_settings),
}

/**
 * A single search result. [icon] is set for applications only. [subtitle] holds
 * secondary text such as a phone number; it is also matched when searching.
 */
data class UniversalSearchItem(
    val category: UniversalSearchCategory,
    val title: String,
    val intent: Intent,
    val icon: Drawable? = null,
    val subtitle: String? = null,
)

/**
 * Loads the searchable items for the categories that are implemented.
 * Categories that are not implemented are not listed here and are shown
 * as unavailable in the search UI.
 */
object UniversalSearchCatalog {

    private data class SettingsPage(@StringRes val titleRes: Int, val action: String)

    private val SETTINGS_PAGES = listOf(
        SettingsPage(R.string.universal_search_settings_wifi, Settings.ACTION_WIFI_SETTINGS),
        SettingsPage(
            R.string.universal_search_settings_bluetooth,
            Settings.ACTION_BLUETOOTH_SETTINGS,
        ),
        SettingsPage(R.string.universal_search_settings_display, Settings.ACTION_DISPLAY_SETTINGS),
        SettingsPage(
            R.string.universal_search_settings_battery,
            Settings.ACTION_BATTERY_SAVER_SETTINGS,
        ),
        SettingsPage(R.string.universal_search_settings_sound, Settings.ACTION_SOUND_SETTINGS),
        SettingsPage(
            R.string.universal_search_settings_storage,
            Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        ),
        SettingsPage(R.string.universal_search_settings_apps, Settings.ACTION_APPLICATION_SETTINGS),
        SettingsPage(
            R.string.universal_search_settings_notifications,
            Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS,
        ),
        SettingsPage(
            R.string.universal_search_settings_accessibility,
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
        ),
        SettingsPage(
            R.string.universal_search_settings_developer,
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        ),
    )

    /** Installed launcher-visible applications, sorted by title. */
    fun loadApplications(context: Context): List<UniversalSearchItem> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { resolveInfo ->
                val component = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name,
                )
                val launchIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                UniversalSearchItem(
                    category = UniversalSearchCategory.APPLICATIONS,
                    title = resolveInfo.loadLabel(pm).toString(),
                    intent = launchIntent,
                    icon = resolveInfo.loadIcon(pm),
                )
            }
            .distinctBy { it.intent.component }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    /**
     * Contacts with a phone number, one result per number. Returns an empty list when
     * READ_CONTACTS has not been granted, so nothing is read without permission.
     */
    fun loadContacts(context: Context): List<UniversalSearchItem> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
        )
        return try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val lookupIndex = cursor.getColumnIndexOrThrow(Phone.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                val numberIndex = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                val items = mutableListOf<UniversalSearchItem>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val contactUri = ContactsContract.Contacts.getLookupUri(
                        cursor.getLong(idIndex),
                        cursor.getString(lookupIndex),
                    )
                    items += UniversalSearchItem(
                        category = UniversalSearchCategory.CONTACTS,
                        title = name,
                        intent = Intent(Intent.ACTION_VIEW, contactUri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        subtitle = cursor.getString(numberIndex),
                    )
                }
                items
            }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Android Settings pages, each opened with a standard Settings intent. */
    fun loadSettings(context: Context): List<UniversalSearchItem> =
        SETTINGS_PAGES.map { page ->
            UniversalSearchItem(
                category = UniversalSearchCategory.SETTINGS,
                title = context.getString(page.titleRes),
                intent = Intent(page.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}
