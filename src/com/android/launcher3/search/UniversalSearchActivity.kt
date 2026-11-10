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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.util.compose.painterResource
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 120L

/** Full-screen Universal Search. Every entry point opens this same activity. */
class UniversalSearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                UniversalSearchScreen(onClose = { finish() })
            }
        }
    }
}

/** Recent searches, persisted in a private preference file. */
internal object UniversalSearchHistory {
    private const val PREFS_NAME = "universal_search"
    private const val KEY_RECENT = "recent_queries"
    private const val MAX_RECENT = 10

    fun load(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, "").orEmpty()
            .split('\n')
            .filter { it.isNotEmpty() }

    fun add(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + load(context).filterNot { it.equals(trimmed, true) })
            .take(MAX_RECENT)
        prefs(context).edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_RECENT).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

private fun rankResults(
    items: List<UniversalSearchItem>,
    query: String,
): List<UniversalSearchItem> =
    items
        .map { item ->
            val titleScore = UniversalSearchMatcher.score(query, item.title)
            val subtitleScore = item.subtitle?.let { UniversalSearchMatcher.score(query, it) } ?: 0
            item to maxOf(titleScore, subtitleScore)
        }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<UniversalSearchItem, Int>> { it.second }
                .thenBy { it.first.category.ordinal }
                .thenBy { it.first.title.lowercase() }
        )
        .map { it.first }

@Composable
internal fun UniversalSearchScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Settings are read when the search opens, so changes apply to the next search.
    val prefs = remember { LauncherPrefs.get(context) }
    val appsEnabled = remember { prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_APPS_ENABLED) }
    val contactsEnabled = remember { prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_CONTACTS_ENABLED) }
    val settingsEnabled = remember { prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_SETTINGS_ENABLED) }
    val historyEnabled = remember { prefs.get(LauncherPrefs.UNIVERSAL_SEARCH_HISTORY_ENABLED) }

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UniversalSearchItem>>(emptyList()) }
    var recents by remember {
        mutableStateOf(if (historyEnabled) UniversalSearchHistory.load(context) else emptyList())
    }

    var coreItems by remember { mutableStateOf<List<UniversalSearchItem>?>(null) }
    var contactItems by remember { mutableStateOf<List<UniversalSearchItem>>(emptyList()) }
    var contactsGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val contactsPromptNeeded = contactsEnabled && !contactsGranted
    val catalog = remember(coreItems, contactItems) { coreItems?.plus(contactItems) }

    // Contacts are requested only when the user taps Allow below. Denial leaves them out.
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> contactsGranted = granted }

    LaunchedEffect(Unit) {
        coreItems = withContext(Dispatchers.IO) {
            val apps = if (appsEnabled) {
                UniversalSearchCatalog.loadApplications(context)
            } else {
                emptyList()
            }
            val settings = if (settingsEnabled) {
                UniversalSearchCatalog.loadSettings(context)
            } else {
                emptyList()
            }
            apps + settings
        }
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(contactsGranted) {
        contactItems = if (contactsEnabled && contactsGranted) {
            withContext(Dispatchers.IO) { UniversalSearchCatalog.loadContacts(context) }
        } else {
            emptyList()
        }
    }

    // Each keystroke restarts this effect, which cancels the previous search.
    LaunchedEffect(query, catalog) {
        val items = catalog ?: return@LaunchedEffect
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        val ranked = withContext(Dispatchers.Default) { rankResults(items, query) }
        results = ranked + UniversalSearchCatalog.webSearchItem(context, query.trim())
    }

    val openFailedMessage = stringResource(R.string.universal_search_open_failed)
    val onSelect: (UniversalSearchItem) -> Unit = { item ->
        if (historyEnabled) {
            UniversalSearchHistory.add(context, query)
        }
        try {
            context.startActivity(item.intent)
            onClose()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, openFailedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            onSubmit = { keyboard?.hide() },
        )
        Spacer(Modifier.height(12.dp))

        when {
            catalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            query.isBlank() -> RecentSearches(
                recents = recents,
                onPick = { query = it },
                onClear = {
                    UniversalSearchHistory.clear(context)
                    recents = emptyList()
                },
                showContactsPrompt = contactsPromptNeeded,
                onAllowContacts = {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
            )
            results.isEmpty() -> NoResults(
                showContactsPrompt = contactsPromptNeeded,
                onAllowContacts = {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
            )
            else -> ResultList(
                results = results,
                onSelect = onSelect,
                showContactsPrompt = contactsPromptNeeded,
                onAllowContacts = {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.universal_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.universal_search_clear),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recents: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    showContactsPrompt: Boolean,
    onAllowContacts: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.universal_search_recent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (recents.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.universal_search_clear_history))
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(recents) { recent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(recent) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(text = recent, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        ContactsAccessPrompt(visible = showContactsPrompt, onAllow = onAllowContacts)
        UnavailableCategoriesNote()
    }
}

@Composable
private fun ResultList(
    results: List<UniversalSearchItem>,
    onSelect: (UniversalSearchItem) -> Unit,
    showContactsPrompt: Boolean,
    onAllowContacts: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        val grouped = results.groupBy { it.category }
        UniversalSearchCategory.entries.forEach { category ->
            val categoryItems = grouped[category].orEmpty()
            if (categoryItems.isEmpty()) return@forEach
            item(key = "header_${category.name}") {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(categoryItems, key = { "${it.category.name}_${it.title}" }) { item ->
                ResultRow(item = item, onClick = { onSelect(item) })
            }
        }
        item(key = "contacts_prompt") {
            ContactsAccessPrompt(visible = showContactsPrompt, onAllow = onAllowContacts)
        }
        item(key = "unavailable") {
            UnavailableCategoriesNote()
        }
    }
}

@Composable
private fun ResultRow(item: UniversalSearchItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = item.icon
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    imageVector = if (item.category == UniversalSearchCategory.WEB) {
                        Icons.Outlined.Search
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoResults(showContactsPrompt: Boolean, onAllowContacts: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.universal_search_no_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ContactsAccessPrompt(visible = showContactsPrompt, onAllow = onAllowContacts)
        UnavailableCategoriesNote()
    }
}

/** Explains contact search and asks for READ_CONTACTS. Hidden once access is granted. */
@Composable
private fun ContactsAccessPrompt(visible: Boolean, onAllow: () -> Unit) {
    if (!visible) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.universal_search_contacts_permission_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.universal_search_contacts_permission_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.universal_search_contacts_permission_action))
            }
        }
    }
}

@Composable
private fun UnavailableCategoriesNote() {
    Text(
        text = stringResource(R.string.universal_search_unavailable_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}
