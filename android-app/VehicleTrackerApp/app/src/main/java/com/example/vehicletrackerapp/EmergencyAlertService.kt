package com.example.vehicletrackerapp

import android.app.*
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class EmergencyAlertService : Service() {
    private val firebaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private var killState = 0
    private var ignitionState = 0
    private var notifyHandler = Handler(Looper.getMainLooper())
    private lateinit var alertRunnable: Runnable
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashing = false
    private var vibrator: Vibrator? = null
    private lateinit var notifMgr: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        showEmergencyNotification()

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        startAlertLoop()
    }

    private fun startAlertLoop() {
        alertRunnable = object : Runnable {
            override fun run() {
                firebaseRef.child("killSwitch").get().addOnSuccessListener {
                    killState = it.getValue(Int::class.java) ?: 0
                    firebaseRef.child("ignition/state").get().addOnSuccessListener { snap ->
                        ignitionState = snap.getValue(Int::class.java) ?: 0

                        if (killState == 1 && ignitionState == 1) {
                            flashAndVibrate()
                            notifyHandler.postDelayed(this, 1000)
                        } else {
                            stopService()
                        }
                    }
                }
            }
        }
        notifyHandler.post(alertRunnable)
    }

    private fun flashAndVibrate() {
        // Vibrate
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(500)
            }
        }

        // Toggle flashlight
        cameraId?.let { id ->
            try {
                isFlashing = !isFlashing
                cameraManager.setTorchMode(id, isFlashing)
            } catch (_: CameraAccessException) {}
        }
    }

    private fun showEmergencyNotification() {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, "emergency_alert_channel")
            .setContentTitle("🚨 EMERGENCY STARTED")
            .setContentText("Kill + Ignition ON — alerting now")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .build()

        startForeground(101, notif)
    }

    private fun stopService() {
        notifyHandler.removeCallbacksAndMessages(null)
        stopFlash()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopFlash() {
        cameraId?.let {
            try {
                cameraManager.setTorchMode(it, false)
            } catch (_: CameraAccessException) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifMgr.createNotificationChannel(
                NotificationChannel(
                    "emergency_alert_channel",
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when kill + ignition are ON"
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        notifyHandler.removeCallbacksAndMessages(null)
        stopFlash()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
