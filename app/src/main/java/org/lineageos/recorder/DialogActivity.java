/*
 * Copyright (C) 2017-2021 The LineageOS Project
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
package org.lineageos.recorder;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;

import org.lineageos.recorder.utils.PermissionManager;
import org.lineageos.recorder.utils.PreferencesManager;

public class DialogActivity extends AppCompatActivity {
    public static final String EXTRA_IS_RECORDING = "is_recording";

    private PermissionManager mPermissionManager;
    private PreferencesManager mPreferences;
    private SwitchCompat mLocationSwitch;
    private SwitchCompat mCircularSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        mPermissionManager = new PermissionManager(this);
        mPreferences = new PreferencesManager(this);

        setFinishOnTouchOutside(true);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(R.layout.dialog_content_settings)
                .setOnDismissListener(dialogInterface -> finish())
                .show();

        final boolean isRecording = getIntent().getBooleanExtra(EXTRA_IS_RECORDING, false);

        mLocationSwitch = dialog.findViewById(
                R.id.dialog_content_settings_location_switch);
        if (mLocationSwitch != null) {
            setupLocationSwitch(mLocationSwitch, isRecording);
        }
        final SwitchCompat highQualitySwitch = dialog.findViewById(
                R.id.dialog_content_settings_high_quality_switch);
        if (highQualitySwitch != null) {
            setupHighQualitySwitch(highQualitySwitch, isRecording);
        }
        mCircularSwitch = dialog.findViewById(
                R.id.dialog_content_settings_circular_recording_switch);
        if (mCircularSwitch != null) {
            setupCircularSwitch(mCircularSwitch, isRecording);
        }
        final AppCompatEditText circularPeriodInput = dialog.findViewById(
                R.id.dialog_content_settings_circular_recording_period_input);
        if (circularPeriodInput != null) {
            setupCircularPeriodInput(circularPeriodInput, isRecording);
        }
        final AppCompatEditText circularNumberInput = dialog.findViewById(
                R.id.dialog_content_settings_circular_recording_number_input);
        if (circularNumberInput != null) {
            setupCircularNumberInput(circularNumberInput, isRecording);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PermissionManager.REQUEST_CODE) {
            if (mPermissionManager.hasLocationPermission()) {
                toggleAfterPermissionRequest();
            } else {
                mPermissionManager.onLocationPermissionDenied();
                mLocationSwitch.setChecked(false);
            }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void setupLocationSwitch(@NonNull SwitchCompat locationSwitch,
                                     boolean isRecording) {
        final boolean tagWithLocation;
        if (mPreferences.getTagWithLocation()) {
            if (mPermissionManager.hasLocationPermission()) {
                tagWithLocation = true;
            } else {
                // Permission revoked -> disabled feature
                mPreferences.setTagWithLocation(false);
                tagWithLocation = false;
            }
        } else {
            tagWithLocation = false;
        }

        locationSwitch.setChecked(tagWithLocation);

        if (isRecording) {
            locationSwitch.setEnabled(false);
        } else {
            locationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    if (mPermissionManager.hasLocationPermission()) {
                        mPreferences.setTagWithLocation(true);
                    } else {
                        mPermissionManager.requestLocationPermission();
                    }
                } else {
                    mPreferences.setTagWithLocation(false);
                }
            });
        }
    }

    private void setupHighQualitySwitch(@NonNull SwitchCompat highQualitySwitch,
                                        boolean isRecording) {
        final boolean highQuality = mPreferences.getRecordInHighQuality();
        highQualitySwitch.setChecked(highQuality);

        if (isRecording) {
            highQualitySwitch.setEnabled(false);
        } else {
            highQualitySwitch.setOnCheckedChangeListener((button, isChecked) ->
                    mPreferences.setRecordingHighQuality(isChecked));
        }
    }

    // basically a copy of setupLocationSwitch
    private void setupCircularSwitch(@NonNull SwitchCompat circularSwitch,
                                     boolean isRecording) {
        final boolean circularRecording;
        if (mPreferences.getCircularRecording()) {
            if (mPermissionManager.hasBatteryPermission()) {
                circularRecording = true;
            } else {
                // Permission revoked -> disabled feature
                mPreferences.setCircularRecording(false);
                circularRecording = false;
            }
        } else {
            circularRecording = false;
        }

        circularSwitch.setChecked(circularRecording);

        if (isRecording) {
            circularSwitch.setEnabled(false);
        } else {
            circularSwitch.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    if (mPermissionManager.hasBatteryPermission()) {
                        mPreferences.setCircularRecording(true);
                    } else {
                        mPermissionManager.requestBatteryPermission();
                    }
                } else {
                    mPreferences.setCircularRecording(false);
                }
            });
        }
    }

    private void setupCircularPeriodInput(@NonNull AppCompatEditText input, boolean isRecording) {
        final long val = mPreferences.getCircularRecordingPeriod();
        input.setText(new java.text.DecimalFormat("0.#").format(((double)val) / 60.0));

        if (isRecording) {
            input.setEnabled(false);
        } else {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    final String str = s.toString();
                    if (str.length() > 0) {
                        mPreferences.setCircularRecordingPeriod((long)(Double.parseDouble(str) * 60.0));
                    }
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            });
        }
    }

    private void setupCircularNumberInput(@NonNull AppCompatEditText input, boolean isRecording) {
        final int val = mPreferences.getCircularRecordingNumber();
        input.setText(Integer.toString(val));

        if (isRecording) {
            input.setEnabled(false);
        } else {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    final String str = s.toString();
                    if (str.length() > 0) {
                        mPreferences.setCircularRecordingNumber(Integer.parseInt(str));
                    }
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            });
        }
    }

    private void toggleAfterPermissionRequest() {
        mLocationSwitch.setChecked(true);
        mPreferences.setTagWithLocation(true);
    }
}
