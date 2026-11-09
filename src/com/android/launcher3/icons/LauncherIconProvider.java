/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.icons;

import android.content.Context;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageItemInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.R;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.iconpack.IconPackDrawable;
import com.android.launcher3.icons.iconpack.IconPackEntry;
import com.android.launcher3.icons.iconpack.IconPackRepository;
import com.android.launcher3.icons.iconpack.IconOverrideRepository;
import com.android.launcher3.icons.iconpack.CustomIconPack;
import com.android.launcher3.util.ComponentKey;

import org.xmlpull.v1.XmlPullParser;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * Extension of {@link IconProvider} with support for overriding theme icons
 */
@LauncherAppSingleton
public class LauncherIconProvider extends IconProvider {

    private static final String TAG_ICON = "icon";
    private static final String ATTR_PACKAGE = "package";
    private static final String ATTR_DRAWABLE = "drawable";

    private static final String TAG = "LIconProvider";
    private static final Map<String, ThemeData> DISABLED_MAP = Collections.emptyMap();

    private Map<String, ThemeData> mThemedIconMap;

    protected final ThemeManager mThemeManager;

    @Inject
    public LauncherIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager) {
        super(context);
        mThemeManager = themeManager;
        mThemedIconMap = FeatureFlags.USE_LOCAL_ICON_OVERRIDES.get() ? null : DISABLED_MAP;
    }

    @Override
    protected ThemeData getThemeDataForPackage(String packageName) {
        return getThemedIconMap().get(packageName);
    }

    @Override
    public void updateSystemState() {
        super.updateSystemState();
        mSystemState = mSystemState.withTheme(
                mThemeManager.getIconState().getThemeCode(),
                mThemeManager.getIconState().isCircle());
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int density) {
        if (info.isArchived) return super.getIcon(info, density);
        Drawable icon = getIconPackDrawable(info, info.applicationInfo, density);
        return icon != null ? icon : super.getIcon(info, density);
    }

    @Override
    public Drawable getIcon(ApplicationInfo info, int density) {
        if (info.isArchived) return super.getIcon(info, density);
        Drawable icon = getIconPackDrawable(info, info, density);
        return icon != null ? icon : super.getIcon(info, density);
    }

    /** Returns a per-component custom icon, or the selected pack's mapping. */
    protected final Drawable getIconPackDrawable(
            PackageItemInfo info, ApplicationInfo appInfo, int density) {
        ComponentName componentName = info instanceof ComponentInfo
                ? new ComponentName(info.packageName, ((ComponentInfo) info).name) : null;
        UserHandle user = UserHandle.getUserHandleForUid(appInfo.uid);
        if (componentName == null) {
            LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                for (android.content.pm.LauncherActivityInfo activity
                        : launcherApps.getActivityList(appInfo.packageName, user)) {
                    componentName = activity.getComponentName();
                    break;
                }
            }
        }
        if (componentName == null) return null;

        ComponentKey key = new ComponentKey(componentName, user);
        Drawable customImage = IconOverrideRepository.INSTANCE
                .getCustomImageDrawable(mContext, key);
        if (customImage != null) return new IconPackDrawable(customImage);

        IconPackEntry entry = IconOverrideRepository.INSTANCE.get(mContext, key);
        if (entry == null) {
            String packages = LauncherPrefs.get(mContext)
                    .get(LauncherPrefs.ICON_PACK_PACKAGE);
            for (String packageName : packages.split(",")) {
                if (packageName.isEmpty()) continue;
                CustomIconPack pack = IconPackRepository.INSTANCE.getPack(mContext, packageName);
                if (pack != null) {
                    entry = pack.getIcon(componentName);
                    if (entry != null) break;
                }
            }
        }
        Drawable icon = entry == null ? null
                : IconPackRepository.INSTANCE.getDrawable(mContext, entry, density);
        return icon == null ? null : new IconPackDrawable(icon);
    }

    @Override
    public PersistedItemState getStateForApp(ApplicationInfo info) {
        if (info == null) return super.getStateForApp(null);
        UserHandle user = UserHandle.getUserHandleForUid(info.uid);
        return super.getStateForApp(info).withAdditionalValues(
                LauncherPrefs.get(mContext).get(LauncherPrefs.ICON_PACK_PACKAGE),
                IconOverrideRepository.INSTANCE.getPackageState(
                        mContext, info.packageName, user),
                "icon-pack-artwork-v1");
    }

    private Map<String, ThemeData> getThemedIconMap() {
        if (mThemedIconMap != null) {
            return mThemedIconMap;
        }
        ArrayMap<String, ThemeData> map = new ArrayMap<>();
        Resources res = mContext.getResources();
        try (XmlResourceParser parser = res.getXml(R.xml.grayscale_icon_map)) {
            final int depth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT);

            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                if (TAG_ICON.equals(parser.getName())) {
                    String pkg = parser.getAttributeValue(null, ATTR_PACKAGE);
                    int iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0);
                    if (iconId != 0 && !TextUtils.isEmpty(pkg)) {
                        map.put(pkg, new ThemeData(res, iconId));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to parse icon map", e);
        }
        mThemedIconMap = map;
        return mThemedIconMap;
    }
}
