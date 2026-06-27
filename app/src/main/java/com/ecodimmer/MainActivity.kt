package com.ecodimmer

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ecodimmer.ui.theme.EcoDimmerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PrefsManager

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DimmerAccessibilityService.ACTION_STATE_CHANGED) {
                // Trigger recomposition by re-evaluating state. In a real app we'd use ViewModel + Flow.
                renderUi()
            }
        }
    }

    override fun onCreate(savedBundle: Bundle?) {
        super.onCreate(savedBundle)
        prefsManager = PrefsManager(this)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(DimmerAccessibilityService.ACTION_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(stateReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        renderUi()
    }

    override fun onStop() {
        super.onStop()
        try { 
            unregisterReceiver(stateReceiver) 
        } catch (e: Exception) {}
    }

    private fun renderUi() {
        setContent {
            EcoDimmerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPerm = isAccessibilityServiceEnabled()
                    var isDimming by remember { mutableStateOf(prefsManager.isDimmingActive) }
                    var dimmingLevel by remember { mutableStateOf(prefsManager.dimmingLevel) }
                    var isScheduleEnabled by remember { mutableStateOf(prefsManager.isScheduleEnabled) }
                    
                    SetupScreen(
                        hasPermission = hasPerm,
                        onGrantPermission = { requestAccessibilityPermission() },
                        onToggleDimming = { 
                            toggleDimming()
                            isDimming = prefsManager.isDimmingActive
                        },
                        isDimmingActive = isDimming,
                        dimmingLevel = dimmingLevel,
                        onDimmingLevelChange = { 
                            dimmingLevel = it
                            prefsManager.dimmingLevel = it
                            updateAlpha()
                        },
                        isScheduleEnabled = isScheduleEnabled,
                        onScheduleToggle = {
                            isScheduleEnabled = it
                            prefsManager.isScheduleEnabled = it
                        },
                        prefsManager = prefsManager
                    )
                }
            }
        }
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

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun toggleDimming() {
        val intent = Intent(DimmerAccessibilityService.ACTION_TOGGLE_DIMMER)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        // Note: isDimmingActive in prefs is updated by the service itself
    }

    private fun updateAlpha() {
        val intent = Intent(DimmerAccessibilityService.ACTION_UPDATE_ALPHA)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }
}

@Composable
fun SetupScreen(
    hasPermission: Boolean,
    onGrantPermission: () -> Unit,
    onToggleDimming: () -> Unit,
    isDimmingActive: Boolean,
    dimmingLevel: Float,
    onDimmingLevelChange: (Float) -> Unit,
    isScheduleEnabled: Boolean,
    onScheduleToggle: (Boolean) -> Unit,
    prefsManager: PrefsManager
) {
    val context = LocalContext.current
    
    // States to trigger re-composition on time change
    var startHour by remember { mutableStateOf(prefsManager.scheduleStartHour) }
    var startMinute by remember { mutableStateOf(prefsManager.scheduleStartMinute) }
    var endHour by remember { mutableStateOf(prefsManager.scheduleEndHour) }
    var endMinute by remember { mutableStateOf(prefsManager.scheduleEndMinute) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "EcoDimmer Setup",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Key Features:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Adjustable Intensity Slider", style = MaterialTheme.typography.bodyMedium)
                Text("• Shake to Rescue (Vigorous shake to OFF)", style = MaterialTheme.typography.bodyMedium)
                Text("• Automated Scheduling", style = MaterialTheme.typography.bodyMedium)
                Text("• Persistent Settings (Survivies Reboot)", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (!hasPermission) {
            Text(
                text = "To dim your screen fully, EcoDimmer requires Accessibility Service permission to draw over everything.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Accessibility Settings")
            }
        } else {
            Button(
                onClick = onToggleDimming,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDimmingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isDimmingActive) "Stop Dimming" else "Start Dimming")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(text = "Dimming Intensity: ${(dimmingLevel * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = dimmingLevel,
                onValueChange = onDimmingLevelChange,
                valueRange = 0.1f..0.9f,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scheduled Dimming", style = MaterialTheme.typography.titleMedium)
                        Switch(checked = isScheduleEnabled, onCheckedChange = onScheduleToggle)
                    }
                    
                    if (isScheduleEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = {
                                TimePickerDialog(context, { _, hour, minute ->
                                    prefsManager.scheduleStartHour = hour
                                    prefsManager.scheduleStartMinute = minute
                                    startHour = hour
                                    startMinute = minute
                                }, startHour, startMinute, false).show()
                            }) {
                                Text(String.format(Locale.US, "Start: %02d:%02d", startHour, startMinute))
                            }
                            
                            Button(onClick = {
                                TimePickerDialog(context, { _, hour, minute ->
                                    prefsManager.scheduleEndHour = hour
                                    prefsManager.scheduleEndMinute = minute
                                    endHour = hour
                                    endMinute = minute
                                }, endHour, endMinute, false).show()
                            }) {
                                Text(String.format(Locale.US, "End: %02d:%02d", endHour, endMinute))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tip: Shake phone vigorously to instantly turn off dimming.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
