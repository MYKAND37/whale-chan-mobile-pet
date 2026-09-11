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
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSpawn.setOnClickListener {
            if (canDrawOverlays()) {
                startPet()
            } else {
                requestOverlayPermission()
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

    private fun refreshState() {
        val allowed = canDrawOverlays()
        binding.statusText.text = getString(
            if (allowed) R.string.status_ready else R.string.status_need_permission
        )
        binding.buttonSpawn.isEnabled = allowed
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
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun startPet() {
        val intent = Intent(this, PetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, R.string.toast_spawned, Toast.LENGTH_SHORT).show()
        // The pet lives on the home screen; get out of her way.
        moveTaskToBack(true)
    }
}
