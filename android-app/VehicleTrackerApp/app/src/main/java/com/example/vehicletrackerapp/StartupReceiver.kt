package com.example.vehicletrackerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.database.FirebaseDatabase

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val firebaseRef = FirebaseDatabase.getInstance().reference

        // Fetch both values first before deciding to start service
        firebaseRef.child("killSwitch").get().addOnSuccessListener { killSnap ->
            val killState = killSnap.getValue(Int::class.java) ?: 0

            firebaseRef.child("ignition/state").get().addOnSuccessListener { ignSnap ->
                val ignitionState = ignSnap.getValue(Int::class.java) ?: 0

                if (killState == 1 && ignitionState == 1) {
                    val serviceIntent = Intent(context, EmergencyAlertService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
