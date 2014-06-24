/*
 * Copyright (C) 2012 The CyanogenMod project
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

package com.android.settings.cyanogenmod;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.EditText;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.chameleonos.AppMultiSelectListPreference;

import java.util.HashSet;
import java.util.Set;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;

public class SystemUiSettings extends SettingsPreferenceFragment  implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SystemSettings";

    private static final String KEY_ANIMATION_OPTIONS = "category_animation_options";
    private static final String KEY_POWER_CRT_MODE = "system_power_crt_mode";
    private static final String KEY_TOAST_ANIMATION = "toast_animation";
    private static final String KEY_LISTVIEW_ANIMATION = "listview_animation";
    private static final String KEY_LISTVIEW_INTERPOLATOR = "listview_interpolator";
    private static final String KEY_SCREEN_GESTURE_SETTINGS = "touch_screen_gesture_settings";
    private static final String PREF_INCLUDE_APP_CIRCLE_BAR_KEY = "app_circle_bar_included_apps";

    private ListPreference mCrtMode;
    private ListPreference mToastAnimation;
    private ListPreference mListViewAnimation;
    private ListPreference mListViewInterpolator;
    private AppMultiSelectListPreference mIncludedAppCircleBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.system_ui_settings);
        PreferenceScreen prefSet = getPreferenceScreen();

        // respect device default configuration
        // true fades while false animates
        boolean electronBeamFadesConfig = getResources().getBoolean(
                com.android.internal.R.bool.config_animateScreenLights);
        PreferenceCategory animationOptions =
            (PreferenceCategory) prefSet.findPreference(KEY_ANIMATION_OPTIONS);
        mCrtMode = (ListPreference) prefSet.findPreference(KEY_POWER_CRT_MODE);
        if (!electronBeamFadesConfig && mCrtMode != null) {
            int crtMode = Settings.System.getInt(getContentResolver(),
                    Settings.System.SYSTEM_POWER_CRT_MODE, 1);
            mCrtMode.setValue(String.valueOf(crtMode));
            mCrtMode.setSummary(mCrtMode.getEntry());
            mCrtMode.setOnPreferenceChangeListener(this);
        } else if (animationOptions != null) {
            prefSet.removePreference(animationOptions);
        }

        mToastAnimation = (ListPreference)findPreference(KEY_TOAST_ANIMATION);
        mToastAnimation.setSummary(mToastAnimation.getEntry());
        int CurrentToastAnimation = Settings.System.getInt(getContentResolver(), Settings.System.TOAST_ANIMATION, 1);
        mToastAnimation.setValueIndex(CurrentToastAnimation);
        mToastAnimation.setSummary(mToastAnimation.getEntries()[CurrentToastAnimation]);
        mToastAnimation.setOnPreferenceChangeListener(this);

        mListViewAnimation = (ListPreference) findPreference(KEY_LISTVIEW_ANIMATION);
        if (mListViewAnimation != null) {
           int listviewanimation = Settings.System.getInt(getContentResolver(),
                   Settings.System.LISTVIEW_ANIMATION, 1);
           mListViewAnimation.setSummary(mListViewAnimation.getEntry());
           mListViewAnimation.setValue(String.valueOf(listviewanimation));
        }
        mListViewAnimation.setOnPreferenceChangeListener(this);

        mListViewInterpolator = (ListPreference) findPreference(KEY_LISTVIEW_INTERPOLATOR);
        if (mListViewInterpolator != null) {
           int listviewinterpolator = Settings.System.getInt(getContentResolver(),
                   Settings.System.LISTVIEW_INTERPOLATOR, 1);
           mListViewInterpolator.setSummary(mListViewInterpolator.getEntry());
           mListViewInterpolator.setValue(String.valueOf(listviewinterpolator));
        }
        mListViewInterpolator.setOnPreferenceChangeListener(this);

        mIncludedAppCircleBar = (AppMultiSelectListPreference) prefSet.findPreference(PREF_INCLUDE_APP_CIRCLE_BAR_KEY);
        Set<String> includedApps = getIncludedApps();
        if (includedApps != null) mIncludedAppCircleBar.setValues(includedApps);
        mIncludedAppCircleBar.setOnPreferenceChangeListener(this);

        Utils.updatePreferenceToSpecificActivityFromMetaDataOrRemove(getActivity(),
                getPreferenceScreen(), KEY_SCREEN_GESTURE_SETTINGS);

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
		if (preference == mIncludedAppCircleBar) {
            storeIncludedApps((Set<String>) objValue);
            return true;
        } else if (KEY_POWER_CRT_MODE.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            int index = mCrtMode.findIndexOfValue((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SYSTEM_POWER_CRT_MODE,
                    value);
            mCrtMode.setSummary(mCrtMode.getEntries()[index]);
            return true;
        } else if (preference == mToastAnimation) {
            int index = mToastAnimation.findIndexOfValue((String) objValue);
            Settings.System.putString(getContentResolver(), Settings.System.TOAST_ANIMATION, (String) objValue);
            mToastAnimation.setSummary(mToastAnimation.getEntries()[index]);
            Toast.makeText(mContext, "Toast Test", Toast.LENGTH_SHORT).show();
            return true;
        } else if (KEY_LISTVIEW_ANIMATION.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            int index = mListViewAnimation.findIndexOfValue((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LISTVIEW_ANIMATION,
                    value);
            mListViewAnimation.setSummary(mListViewAnimation.getEntries()[index]);
            return true;
        } else if (KEY_LISTVIEW_INTERPOLATOR.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            int index = mListViewInterpolator.findIndexOfValue((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LISTVIEW_INTERPOLATOR,
                    value);
            mListViewInterpolator.setSummary(mListViewInterpolator.getEntries()[index]);
            return true;
        }

        return false;
    }

    private Set<String> getIncludedApps() {
        String included = Settings.System.getString(getActivity().getContentResolver(),
                Settings.System.WHITELIST_APP_CIRCLE_BAR);
        if (TextUtils.isEmpty(included)) {
            return null;
        }
        return new HashSet<String>(Arrays.asList(included.split("\\|")));
    }

    private void storeIncludedApps(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        String delimiter = "";
        for (String value : values) {
            builder.append(delimiter);
            builder.append(value);
            delimiter = "|";		 
		}
        Settings.System.putString(getActivity().getContentResolver(),
                Settings.System.WHITELIST_APP_CIRCLE_BAR, builder.toString());
    }
}
