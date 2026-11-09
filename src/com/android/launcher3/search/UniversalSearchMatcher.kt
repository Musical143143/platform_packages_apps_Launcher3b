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

/**
 * Scores how well a search query matches a candidate title.
 *
 * Higher scores rank first. A score of 0 means the title does not match.
 * Matching tiers, strongest first: exact, prefix, word prefix, substring,
 * ordered-letter (fuzzy), and single-edit typo tolerance.
 */
object UniversalSearchMatcher {

    private val WORD_SEPARATORS = charArrayOf(' ', '-', '_', '/', '(', ')', '.', ',')
    private const val MIN_TYPO_QUERY_LENGTH = 4

    fun score(query: String, title: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val t = title.lowercase()
        return when {
            t == q -> 100
            t.startsWith(q) -> 90
            words(t).any { it.startsWith(q) } -> 75
            t.contains(q) -> 60
            isOrderedSubsequence(q, t) -> 40
            isTypoMatch(q, t) -> 25
            else -> 0
        }
    }

    private fun words(text: String): List<String> =
        text.split(*WORD_SEPARATORS).filter { it.isNotEmpty() }

    private fun isOrderedSubsequence(query: String, text: String): Boolean {
        var matched = 0
        for (c in text) {
            if (matched < query.length && c == query[matched]) {
                matched++
            }
        }
        return matched == query.length
    }

    private fun isTypoMatch(query: String, text: String): Boolean {
        if (query.length < MIN_TYPO_QUERY_LENGTH) return false
        return words(text).any { word ->
            levenshtein(query, word.take(query.length)) <= 1
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
