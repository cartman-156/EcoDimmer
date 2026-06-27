package com.ecodimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DimOverlayService : Service(), SensorEventListener, BroadcastReceiver {
    companion object {
        const val ACTION_TOGGLE_DIMMER = "com.ecodimmer.TOGGLE_DIMMER"
        const val ACTION_UPDATE_ALPHA = "com.ecodimmer.UPDATE_ALPHA"
        const val ACTION_STATE_CHANGED = "com.ecodimmer.STATE_CHANGED"
        var isDimming = false
            private set
    }

    private lateinit var prefsManager: PrefsManager
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkIntervalMs = 2000L // check every 2 seconds

    // List of known UPI app package names (common ones and a generic keyword match)
    private val upiPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // GPay / BHIM
        "com.phonepe.app",
        "net.one97.paytm",
        "com.bhimupi.navi",
        "com.shubhupay.supermoney",
        // Add generic bank UPI packages (example placeholders)
        "com.sbi.upi",
        "com.hdfc.upi",
        "com.icicibank.upi",
        "com.axisbank.upi",
        "com.kotak.upi"
    )

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        isDimming = prefsManager.isDimmingActive
        registerReceivers()
        startForegroundService()
        updateOverlay()
        startUpiMonitor()
    }

    private fun startForegroundService() {
        val channelId = "dim_overlay_channel"
        val channel = NotificationChannel(channelId, "Dim Overlay", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("EcoDimmer")
            .setContentText("Dim overlay active")
            .setSmallIcon(R.drawable.ic_dim_tile)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun registerReceivers() {
        val systemFilter = IntentFilter(Intent.ACTION_TIME_TICK)
        val customFilter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_DIMMER)
            addAction(ACTION_UPDATE_ALPHA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this, systemFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(this, customFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(this, systemFilter)
            registerReceiver(this, customFilter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUpiMonitor()
        hideOverlay()
        sensorManager?.unregisterListener(this)
        try { unregisterReceiver(this) } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_TOGGLE_DIMMER -> {
                isDimming = !isDimming
                prefsManager.isDimmingActive = isDimming
                updateOverlay()
                broadcastState()
            }
            ACTION_UPDATE_ALPHA -> {
                overlayView?.alpha = prefsManager.dimmingLevel
            }
            Intent.ACTION_TIME_TICK -> {
                // Schedule handling could be added here if needed
            }
        }
    }

    private fun broadcastState() {
        val i = Intent(ACTION_STATE_CHANGED)
        i.setPackage(packageName)
        sendBroadcast(i)
    }

    private fun updateOverlay() {
        if (isDimming && !isUppiAppForeground()) {
            showOverlay()
            accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            hideOverlay()
            sensorManager?.unregisterListener(this)
        }
    }

    private fun showOverlay() {
        if (overlayView == null) {
            overlayView = View(this).apply { setBackgroundColor(Color.BLACK) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            windowManager?.addView(overlayView, params)
        }
        overlayView?.alpha = prefsManager.dimmingLevel
    }

    private fun hideOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    // ---------- UPI detection ----------
    private fun startUpiMonitor() {
        handler.post(upiCheckRunnable)
    }

    private fun stopUpiMonitor() {
        handler.removeCallbacks(upiCheckRunnable)
    }

    private val upiCheckRunnable = object : Runnable {
        override fun run() {
            try {
                if (isUppiAppForeground()) {
                    if (isDimming) {
                        // Immediately disable dimming when a UPI app is in foreground
                        isDimming = false
                        prefsManager.isDimmingActive = false
                        hideOverlay()
                        broadcastState()
                    }
                }
            } catch (e: Exception) {
                Log.e("DimOverlayService", "UPI monitor error", e)
            }
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    private fun isUppiAppForeground(): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
        if (usageStatsManager == null) return false
        val end = System.currentTimeMillis()
        val begin = end - TimeUnit.MINUTES.toMillis(1)
        val usageStats: List<UsageStats> = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        if (usageStats.isEmpty()) return false
        val recent = usageStats.maxByOrNull { it.lastTimeUsed } ?: return false
        val foregroundPkg = recent.packageName
        // Direct match or generic contains "upi"
        return upiPackages.contains(foregroundPkg) || foregroundPkg.contains("upi", ignoreCase = true)
    }

    // ---------- SensorEventListener ----------
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Existing shake‑to‑rescue logic (unchanged)
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            // Simple threshold example – you may adjust the value as needed
            val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            if (acceleration > 12 && isDimming) {
                isDimming = false
                prefsManager.isDimmingActive = false
                hideOverlay()
                broadcastState()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.Calendar
import kotlin.math.sqrt

class DimOverlayService : Service(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var prefsManager: PrefsManager
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    
    companion object {
        const val ACTION_TOGGLE_DIMMER = "com.ecodimmer.TOGGLE_DIMMER"
        const val ACTION_UPDATE_ALPHA = "com.ecodimmer.UPDATE_ALPHA"
        const val ACTION_STATE_CHANGED = "com.ecodimmer.STATE_CHANGED"
        
        var isDimming = false
            private set
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_DIMMER -> {
                    isDimming = !isDimming
                    prefsManager.isDimmingActive = isDimming
                    updateOverlay()
                    broadcastState()
                }
                ACTION_UPDATE_ALPHA -> {
                    overlayView?.alpha = prefsManager.dimmingLevel
                }
                Intent.ACTION_TIME_TICK -> {
                    checkSchedule()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefsManager = PrefsManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        isDimming = prefsManager.isDimmingActive
        
        // System broadcasts (TIME_TICK) must be handled carefully on Android 14+
        val systemFilter = IntentFilter(Intent.ACTION_TIME_TICK)
        
        // Custom broadcasts for internal communication
        val customFilter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_DIMMER)
            addAction(ACTION_UPDATE_ALPHA)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // System broadcasts are implicitly exported, but custom ones should be NOT_EXPORTED for security
            registerReceiver(receiver, systemFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(receiver, customFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, systemFilter)
            registerReceiver(receiver, customFilter)
        }

        updateOverlay()
    }

    private fun checkSchedule() {
        if (!prefsManager.isScheduleEnabled) return
        
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        if (currentHour == prefsManager.scheduleStartHour && currentMinute == prefsManager.scheduleStartMinute) {
            if (!isDimming) {
                isDimming = true
                prefsManager.isDimmingActive = true
                updateOverlay()
                broadcastState()
            }
        } else if (currentHour == prefsManager.scheduleEndHour && currentMinute == prefsManager.scheduleEndMinute) {
            if (isDimming) {
                isDimming = false
                prefsManager.isDimmingActive = false
                updateOverlay()
                broadcastState()
            }
        }
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_CHANGED)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun updateOverlay() {
        if (isDimming) {
            showOverlay()
            // CRITICAL: Only register if sensor exists to avoid IllegalArgumentException
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            hideOverlay()
            sensorManager?.unregisterListener(this)
        }
    }

    private fun showOverlay() {
        if (overlayView == null) {
            overlayView = View(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            windowManager?.addView(overlayView, layoutParams)
        }
        
        overlayView?.alpha = prefsManager.dimmingLevel
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    private var lastAcceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var acceleration = 0f

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            if (acceleration > 12) { 
                if (isDimming) {
                    isDimming = false
                    prefsManager.isDimmingActive = false
                    updateOverlay()
                    broadcastState()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        if (::prefsManager.isInitialized) {
            isDimming = false
            prefsManager.isDimmingActive = false
            updateOverlay()
            broadcastState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        sensorManager?.unregisterListener(this)
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }
}
