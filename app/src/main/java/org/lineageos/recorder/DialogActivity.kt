/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import org.lineageos.recorder.utils.PermissionManager
import org.lineageos.recorder.utils.PreferencesManager

class DialogActivity : AppCompatActivity() {
    // Views
    private lateinit var highQualitySwitch: MaterialSwitch
    private lateinit var locationSwitch: MaterialSwitch
    private lateinit var circularSwitch: MaterialSwitch

    private val permissionManager: PermissionManager by lazy { PermissionManager(this) }

    private val preferences by lazy { PreferencesManager(this) }

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        setFinishOnTouchOutside(true)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(R.layout.dialog_content_settings)
            .setOnDismissListener { finish() }
            .show()

        val isRecording = intent.getBooleanExtra(EXTRA_IS_RECORDING, false)
        locationSwitch = dialog.findViewById(R.id.locationSwitch)!!

        setupLocationSwitch(locationSwitch, isRecording)

        highQualitySwitch = dialog.findViewById(R.id.highQualitySwitch)!!
        setupHighQualitySwitch(highQualitySwitch, isRecording)

        circularSwitch = dialog.findViewById(R.id.circularSwitch)!!
        setupCircularSwitch(circularSwitch, isRecording)

        val circularPeriodInput: AppCompatEditText? = dialog.findViewById(
                R.id.dialog_content_settings_circular_recording_period_input)
        if (circularPeriodInput != null) {
            setupCircularPeriodInput(circularPeriodInput, isRecording)
        }
        val circularNumberInput: AppCompatEditText? = dialog.findViewById(
                R.id.dialog_content_settings_circular_recording_number_input)
        if (circularNumberInput != null) {
            setupCircularNumberInput(circularNumberInput, isRecording)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == PermissionManager.REQUEST_CODE) {
            if (permissionManager.hasLocationPermission()) {
                toggleAfterPermissionRequest()
            } else {
                permissionManager.onLocationPermissionDenied()
                locationSwitch.isChecked = false
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out, Color.TRANSPARENT
            )
        } else {
            @Suppress("deprecation")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    private fun setupLocationSwitch(
        locationSwitch: MaterialSwitch,
        isRecording: Boolean
    ) {
        val tagWithLocation = if (preferences.tagWithLocation) {
            if (permissionManager.hasLocationPermission()) {
                true
            } else {
                // Permission revoked -> disabled feature
                preferences.tagWithLocation = false
                false
            }
        } else {
            false
        }
        locationSwitch.isChecked = tagWithLocation
        if (isRecording) {
            locationSwitch.isEnabled = false
        } else {
            locationSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    if (permissionManager.hasLocationPermission()) {
                        preferences.tagWithLocation = true
                    } else {
                        permissionManager.requestLocationPermission()
                    }
                } else {
                    preferences.tagWithLocation = false
                }
            }
        }
    }

    private fun setupCircularSwitch(
        circularSwitch: MaterialSwitch,
        isRecording: Boolean
    ) {
        val circularRecording = if (preferences.circularRecording) {
            if (permissionManager.hasBatteryPermission()) {
                true
            } else {
                // Permission revoked -> disabled feature
                preferences.circularRecording = false
                false
            }
        } else {
            false
        }
        circularSwitch.isChecked = circularRecording
        if (isRecording) {
            circularSwitch.isEnabled = false
        } else {
            circularSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    if (permissionManager.hasBatteryPermission()) {
                        preferences.circularRecording = true
                    } else {
                        permissionManager.requestBatteryPermission()
                    }
                } else {
                    preferences.circularRecording = false
                }
            }
        }
    }

    private fun setupCircularPeriodInput(input: AppCompatEditText, isRecording: Boolean) {
        val v: Long = preferences.circularRecordingPeriod
        input.setText(java.text.DecimalFormat("0.#").format(v.toDouble() / 60.0))

        if (isRecording) {
            input.setEnabled(false)
        } else {
            input.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    val str: String = s.toString()
                    if (str.length > 0) {
                        preferences.circularRecordingPeriod = (str.toDouble() * 60.0).toLong()
                    }
                }
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            })
        }
    }

    private fun setupCircularNumberInput(input: AppCompatEditText, isRecording: Boolean) {
        val v: Int = preferences.circularRecordingNumber
        input.setText(Integer.toString(v))

        if (isRecording) {
            input.setEnabled(false)
        } else {
            input.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    val str: String = s.toString()
                    if (str.length > 0) {
                        preferences.circularRecordingNumber = str.toInt()
                    }
                }
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            })
        }
    }

    private fun setupHighQualitySwitch(
        highQualitySwitch: MaterialSwitch,
        isRecording: Boolean
    ) {
        val highQuality = preferences.recordInHighQuality
        highQualitySwitch.isChecked = highQuality
        if (isRecording) {
            highQualitySwitch.isEnabled = false
        } else {
            highQualitySwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                preferences.recordInHighQuality = isChecked
            }
        }
    }

    private fun toggleAfterPermissionRequest() {
        locationSwitch.isChecked = true
        preferences.tagWithLocation = true
    }

    companion object {
        const val EXTRA_IS_RECORDING = "is_recording"
    }
}
