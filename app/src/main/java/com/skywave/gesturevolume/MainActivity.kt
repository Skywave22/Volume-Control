package com.skywave.gesturevolume

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.skywave.gesturevolume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        b.switchGestures.setOnCheckedChangeListener { _, checked ->
            Prefs.setGesturesEnabled(this, checked)
        }
        b.switchHaptics.setOnCheckedChangeListener { _, checked ->
            Prefs.setHaptics(this, checked)
        }
        b.switchSystemUi.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowSystemUi(this, checked)
        }

        b.seekSensitivity.max = 4
        b.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                Prefs.setSensitivity(this@MainActivity, progress + 1)
                b.txtSensitivityValue.text = sensitivityLabel(progress + 1)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // live volume slider inside the app
        b.seekVolume.max = VolumeController.maxVolume(this)
        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    VolumeController.setLevel(this@MainActivity, progress, false)
                    updateVolumeText()
                    VolumeWidgetProvider.refreshAll(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.btnMute.setOnClickListener {
            VolumeController.toggleMute(this, false)
            refreshVolumeUi()
            VolumeWidgetProvider.refreshAll(this)
        }
        b.btnMinus.setOnClickListener {
            VolumeController.adjust(this, -1, false)
            refreshVolumeUi()
            VolumeWidgetProvider.refreshAll(this)
        }
        b.btnPlus.setOnClickListener {
            VolumeController.adjust(this, 1, false)
            refreshVolumeUi()
            VolumeWidgetProvider.refreshAll(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isAccessibilityServiceEnabled()
        b.switchGestures.isChecked = Prefs.gesturesEnabled(this)
        b.switchHaptics.isChecked = Prefs.hapticsEnabled(this)
        b.switchSystemUi.isChecked = Prefs.showSystemUi(this)
        val s = Prefs.sensitivity(this)
        b.seekSensitivity.progress = s - 1
        b.txtSensitivityValue.text = sensitivityLabel(s)

        b.txtServiceStatus.text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                getString(R.string.status_unsupported)
            enabled -> getString(R.string.status_active)
            else -> getString(R.string.status_inactive)
        }
        b.btnEnableService.text =
            if (enabled) getString(R.string.open_accessibility_settings)
            else getString(R.string.enable_service)

        refreshVolumeUi()
    }

    private fun refreshVolumeUi() {
        b.seekVolume.max = VolumeController.maxVolume(this)
        b.seekVolume.progress = VolumeController.currentVolume(this)
        updateVolumeText()
    }

    private fun updateVolumeText() {
        val max = VolumeController.maxVolume(this).coerceAtLeast(1)
        val cur = VolumeController.currentVolume(this)
        val muted = VolumeController.isMuted(this) || cur == 0
        b.txtVolumeValue.text = if (muted) getString(R.string.muted) else "${cur * 100 / max}%"
    }

    private fun sensitivityLabel(level: Int): String = when (level) {
        1 -> getString(R.string.sens_lowest)
        2 -> getString(R.string.sens_low)
        3 -> getString(R.string.sens_medium)
        4 -> getString(R.string.sens_high)
        else -> getString(R.string.sens_highest)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${VolumeGestureService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
