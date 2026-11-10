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
package com.android.launcher3.allapps;

import android.content.pm.ApplicationInfo;

/**
 * Category filter chips shown in the app drawer. Categories come from the
 * application's {@link ApplicationInfo#category} tag. Apps without a recognised
 * tag fall under {@link #OTHERS}.
 */
public enum AllAppsCategory {
    ALL,
    COMMUNICATION,
    ENTERTAINMENT,
    TOOLS,
    OTHERS;

    /** Maps an {@link ApplicationInfo} category constant to a drawer chip. */
    public static AllAppsCategory fromApplicationCategory(int category) {
        switch (category) {
            case ApplicationInfo.CATEGORY_SOCIAL:
            case ApplicationInfo.CATEGORY_NEWS:
                return COMMUNICATION;
            case ApplicationInfo.CATEGORY_GAME:
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO:
            case ApplicationInfo.CATEGORY_IMAGE:
                return ENTERTAINMENT;
            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
            case ApplicationInfo.CATEGORY_MAPS:
                return TOOLS;
            default:
                return OTHERS;
        }
    }
}
