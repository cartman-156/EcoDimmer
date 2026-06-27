package com.ecodimmer

import android.content.Intent
import android.provider.Settings
import android.content.ComponentName
import android.text.TextUtils
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

class QuickSettingsTileService : TileService() {

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DimmerAccessibilityService.ACTION_STATE_CHANGED) {
                updateTile()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        
        val filter = IntentFilter(DimmerAccessibilityService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) {}
    }

    override fun onClick() {
        super.onClick()
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(DimmerAccessibilityService.ACTION_TOGGLE_DIMMER)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            // The tile will be updated by the broadcast receiver
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val isActive = DimmerAccessibilityService.isDimming
        qsTile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        // Dynamic icon based on state
        val iconRes = if (isActive) R.drawable.ic_dim_tile_on else R.drawable.ic_dim_tile_off
        qsTile.icon = Icon.createWithResource(this, iconRes)
        
        qsTile.updateTile()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, DimmerAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
