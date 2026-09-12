package com.dsh.mobile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dsh.mobile.databinding.ActivityMainBinding

/**
 * Control panel for the pet: request the overlay permission, then start or
 * stop the floating whale.
 *
 * The spawn button stays clickable even without the permission — tapping it
 * sends the user to the system grant screen. Disabling it instead makes the
 * button look broken, because a disabled tap produces no visible feedback.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSpawn.setOnClickListener {
            when {
                !canDrawOverlays() -> requestOverlayPermission()
                isPetRunning -> {
                    Toast.makeText(this, R.string.toast_already_running, Toast.LENGTH_SHORT).show()
                }
                else -> startPet()
            }
        }

        binding.buttonStop.setOnClickListener {
            stopService(Intent(this, PetService::class.java))
            Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show()
            refreshState()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    /** Whether the pet service is alive right now. */
    private val isPetRunning: Boolean
        get() = PetState.running

    private fun refreshState() {
        val allowed = canDrawOverlays()
        val headline = getString(
            when {
                !allowed -> R.string.status_need_permission
                isPetRunning -> R.string.status_running
                else -> R.string.status_ready
            }
        )
        // Append the service's own report so a blank overlay can be explained
        // without needing logcat on the device.
        val detail = PetState.diagnostic
        binding.statusText.text =
            if (detail.isNullOrBlank()) headline else "$headline\n\n$detail"

        binding.buttonSpawn.text = getString(
            if (allowed) R.string.action_spawn else R.string.action_grant
        )
    }

    private fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, R.string.toast_grant, Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // Some OEM builds ship no such settings screen; keep the app usable.
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPet() {
        val intent = Intent(this, PetService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.onFailure {
            Toast.makeText(this, R.string.toast_start_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.toast_spawned, Toast.LENGTH_SHORT).show()
        // The pet lives on the home screen; get out of her way.
        moveTaskToBack(true)
    }
}
