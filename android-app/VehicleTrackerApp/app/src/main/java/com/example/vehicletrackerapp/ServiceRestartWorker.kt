package com.example.vehicletrackerapp

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class ServiceRestartWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result = runBlocking {
        val firebaseRef = FirebaseDatabase.getInstance().reference

        try {
            val kill = firebaseRef.child("killSwitch").get().await()
            val ign = firebaseRef.child("ignition/state").get().await()

            val killState = kill.getValue(Int::class.java) ?: 0
            val ignState = ign.getValue(Int::class.java) ?: 0

            if (killState == 1 && ignState == 1) {
                val intent = Intent(applicationContext, EmergencyAlertService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
