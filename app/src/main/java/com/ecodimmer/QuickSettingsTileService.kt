package com.ecodimmer

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.provider.Settings
import android.content.ComponentName
import android.text.TextUtils

class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(DimmerAccessibilityService.ACTION_TOGGLE_DIMMER)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            // Assuming the state flipped successfully
            val willBeActive = !DimmerAccessibilityService.isDimming
            qsTile.state = if (willBeActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            qsTile.updateTile()
        } else {
            // Service not enabled, prompt user somehow?
            // TileService can launch an activity
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val isActive = DimmerAccessibilityService.isDimming
        qsTile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
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
