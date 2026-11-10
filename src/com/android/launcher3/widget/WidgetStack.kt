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
package com.android.launcher3.widget

/**
 * Data format for a widget stack: several widgets that share one home-screen cell.
 *
 * The child widget ids are stored in the favorites table's intent column as
 * `widgetstack:<id>,<id>,...`. The intent column is text that the loader already parses inside
 * a `runCatching`, so this value does not change the database layout. Launchers that do not
 * know the stack type skip the item.
 */
object WidgetStack {
    const val PREFIX = "widgetstack:"

    /** Encodes the child widget ids. An empty list encodes to the prefix alone. */
    fun encode(appWidgetIds: List<Int>): String = PREFIX + appWidgetIds.joinToString(",")

    /** Returns the child widget ids, or null when [intentString] is not a valid widget stack. */
    fun decode(intentString: String?): List<Int>? {
        if (intentString == null || !intentString.startsWith(PREFIX)) {
            return null
        }
        val ids = intentString.removePrefix(PREFIX).split(",").map {
            it.trim().toIntOrNull() ?: return null
        }
        return ids.takeIf { it.isNotEmpty() }
    }
}
