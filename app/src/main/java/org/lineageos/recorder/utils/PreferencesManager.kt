/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.Context
import android.net.Uri

class PreferencesManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isCurrentlyRecording: Boolean
        get() = preferences.getBoolean(PREF_IS_CURRENTLY_RECORDING, false)
        set(isCurrentlyRecording) {
            preferences.edit()
                .putBoolean(PREF_IS_CURRENTLY_RECORDING, isCurrentlyRecording)
                .apply()
        }

    var recordInHighQuality: Boolean
        get() = preferences.getInt(PREF_RECORDING_QUALITY, 0) == 1
        set(value) {
            preferences.edit()
                .putInt(PREF_RECORDING_QUALITY, if (value) 1 else 0)
                .apply()
        }

    var circularRecording: Boolean
        get() = preferences.getInt(PREF_CIRCULAR_RECORDING, 0) == 1
        set(value) {
            preferences.edit()
                .putInt(PREF_CIRCULAR_RECORDING, if (value) 1 else 0)
                .apply()
        }

    var circularRecordingPeriod: Long
        get() = preferences.getLong(PREF_CIRCULAR_RECORDING_PERIOD, 3600)
        set(circularRecordingPeriod) {
            preferences.edit()
                .putLong(PREF_CIRCULAR_RECORDING_PERIOD, circularRecordingPeriod)
                .apply()
        }

    var circularRecordingNumber: Int
        get() = preferences.getInt(PREF_CIRCULAR_RECORDING_NUMBER, 3)
        set(circularRecordingNumber) {
            preferences.edit()
                .putInt(PREF_CIRCULAR_RECORDING_NUMBER, circularRecordingNumber)
                .apply()
        }

    var tagWithLocation: Boolean
        get() = preferences.getBoolean(PREF_TAG_WITH_LOCATION, false)
        set(tagWithLocation) {
            preferences.edit()
                .putBoolean(PREF_TAG_WITH_LOCATION, tagWithLocation)
                .apply()
        }

    var onboardSettingsCounter: Int
        get() = preferences.getInt(PREF_ONBOARD_SETTINGS_COUNTER, 0)
        set(value) {
            preferences.edit()
                .putInt(PREF_ONBOARD_SETTINGS_COUNTER, value)
                .apply()
        }

    var onboardListCounter: Int
        get() = preferences.getInt(PREF_ONBOARD_SOUND_LIST_COUNTER, 0)
        set(value) {
            preferences.edit()
                .putInt(PREF_ONBOARD_SOUND_LIST_COUNTER, value)
                .apply()
        }

    var lastItemUri: Uri?
        get() {
            val uriStr = preferences.getString(PREF_LAST_SOUND, null)
            return if (uriStr == null) null else Uri.parse(uriStr)
        }
        set(value) {
            preferences.edit()
                .putString(PREF_LAST_SOUND, value?.toString())
                .apply()
        }

    companion object {
        private const val PREFS = "preferences"
        private const val PREF_IS_CURRENTLY_RECORDING = "is_currently_recording"
        private const val PREF_TAG_WITH_LOCATION = "tag_with_location"
        private const val PREF_RECORDING_QUALITY = "recording_quality"
        private const val PREF_CIRCULAR_RECORDING = "circular_recording"
        private const val PREF_CIRCULAR_RECORDING_PERIOD = "circular_recording_period"
        private const val PREF_CIRCULAR_RECORDING_NUMBER = "circular_recording_number"
        private const val PREF_ONBOARD_SETTINGS_COUNTER = "onboard_settings"
        private const val PREF_ONBOARD_SOUND_LIST_COUNTER = "onboard_list"
        private const val PREF_LAST_SOUND = "sound_last_path"
    }
}
