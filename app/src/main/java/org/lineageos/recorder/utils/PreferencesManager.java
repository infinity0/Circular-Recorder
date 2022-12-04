/*
 * Copyright (C) 2021-2022 The LineageOS Project
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
package org.lineageos.recorder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;

public final class PreferencesManager {
    private static final String PREFS = "preferences";
    private static final String PREF_TAG_WITH_LOCATION = "tag_with_location";
    private static final String PREF_RECORDING_QUALITY = "recording_quality";
    private static final String PREF_CIRCULAR_RECORDING = "circular_recording";
    private static final String PREF_CIRCULAR_RECORDING_PERIOD = "circular_recording_period";
    private static final String PREF_CIRCULAR_RECORDING_NUMBER = "circular_recording_number";
    private static final String PREF_ONBOARD_SETTINGS_COUNTER = "onboard_settings";
    private static final String PREF_ONBOARD_SOUND_LIST_COUNTER = "onboard_list";
    private static final String PREF_LAST_SOUND = "sound_last_path";

    private final SharedPreferences mPreferences;

    public PreferencesManager(Context context) {
        mPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setRecordingHighQuality(boolean highQuality) {
        mPreferences.edit()
                .putInt(PREF_RECORDING_QUALITY, highQuality ? 1 : 0)
                .apply();
    }

    public boolean getRecordInHighQuality() {
        return mPreferences.getInt(PREF_RECORDING_QUALITY, 0) == 1;
    }

    public void setTagWithLocation(boolean tagWithLocation) {
        mPreferences.edit()
                .putBoolean(PREF_TAG_WITH_LOCATION, tagWithLocation)
                .apply();
    }

    public boolean getTagWithLocation() {
        return mPreferences.getBoolean(PREF_TAG_WITH_LOCATION, false);
    }

    public void setCircularRecording(boolean circularRecording) {
        mPreferences.edit()
                .putBoolean(PREF_CIRCULAR_RECORDING, circularRecording)
                .apply();
    }

    public boolean getCircularRecording() {
        // TODO: switch the default back to "false" if we merge back into upstream
        return mPreferences.getBoolean(PREF_CIRCULAR_RECORDING, true);
    }

    public void setCircularRecordingPeriod(long circularRecordingPeriod) {
        mPreferences.edit()
                .putLong(PREF_CIRCULAR_RECORDING_PERIOD, circularRecordingPeriod)
                .apply();
    }

    public long getCircularRecordingPeriod() {
        return mPreferences.getLong(PREF_CIRCULAR_RECORDING_PERIOD, 3600);
    }

    public void setCircularRecordingNumber(int circularRecordingNumber) {
        mPreferences.edit()
                .putInt(PREF_CIRCULAR_RECORDING_NUMBER, circularRecordingNumber)
                .apply();
    }

    public int getCircularRecordingNumber() {
        return mPreferences.getInt(PREF_CIRCULAR_RECORDING_NUMBER, 3);
    }

    public int getOnboardSettingsCounter() {
        return mPreferences.getInt(PREF_ONBOARD_SETTINGS_COUNTER, 0);
    }

    public void setOnboardSettingsCounter(int value) {
        mPreferences.edit()
                .putInt(PREF_ONBOARD_SETTINGS_COUNTER, value)
                .apply();
    }

    public int getOnboardListCounter() {
        return mPreferences.getInt(PREF_ONBOARD_SOUND_LIST_COUNTER, 0);
    }

    public void setOnboardListCounter(int value) {
        mPreferences.edit()
                .putInt(PREF_ONBOARD_SOUND_LIST_COUNTER, value)
                .apply();
    }

    public void setLastItemUri(String path) {
        mPreferences.edit()
                .putString(PREF_LAST_SOUND, path)
                .apply();
    }

    @Nullable
    public Uri getLastItemUri() {
        String uriStr = mPreferences.getString(PREF_LAST_SOUND, null);
        return uriStr == null ? null : Uri.parse(uriStr);
    }
}
