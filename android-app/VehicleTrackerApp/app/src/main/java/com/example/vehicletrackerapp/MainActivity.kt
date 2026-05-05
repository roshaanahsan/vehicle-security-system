package com.example.vehicletrackerapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var gMap: GoogleMap
    private lateinit var statusBadge: TextView
    private lateinit var killSwitchBtn: Button
    private lateinit var openMapsBtn: Button
    private lateinit var viewLogsBtn: Button
    private var currentLatLng: LatLng? = null

    private var killSwitchState = -1
    private var ignitionState = -1
    private var prevKill = -1
    private var prevIgn = -1
    private var lastHeartbeatMillis: Long = 0

    private var hasKillSwitchValue = false
    private var hasIgnitionValue = false

    private val handler = Handler(Looper.getMainLooper())
    private val firebaseRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        statusBadge = findViewById(R.id.statusBadge)
        killSwitchBtn = findViewById(R.id.killSwitchBtn)
        openMapsBtn = findViewById(R.id.openMapsBtn)
        viewLogsBtn = findViewById(R.id.viewLogsBtn)

        setupFirebaseListeners()
        setupButtonActions()
        startHeartbeatChecker()
        scheduleServiceRestartWorker()
    }

    override fun onMapReady(map: GoogleMap) {
        gMap = map
        gMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun setupFirebaseListeners() {
        firebaseRef.child("gps").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val lat = snap.child("latitude").getValue(Double::class.java)
                val lng = snap.child("longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    currentLatLng = LatLng(lat, lng).also {
                        gMap.clear()
                        gMap.addMarker(MarkerOptions().position(it).title("Tracker"))
                        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16f))
                    }
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        })

        firebaseRef.child("killSwitch").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                killSwitchState = snap.getValue(Int::class.java) ?: 0
                killSwitchBtn.text = "Kill Switch: ${if (killSwitchState == 1) "ON" else "OFF"}"
                hasKillSwitchValue = true
                maybeTriggerEmergency()
            }

            override fun onCancelled(e: DatabaseError) {}
        })

        firebaseRef.child("ignition/state").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                ignitionState = snap.getValue(Int::class.java) ?: 0
                hasIgnitionValue = true
                maybeTriggerEmergency()
            }

            override fun onCancelled(e: DatabaseError) {}
        })

        firebaseRef.child("heartbeat/timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                lastHeartbeatMillis = snap.getValue(Long::class.java) ?: 0
            }

            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun maybeTriggerEmergency() {
        if (!hasKillSwitchValue || !hasIgnitionValue) return

        if (prevKill == -1 && prevIgn == -1) {
            prevKill = killSwitchState
            prevIgn = ignitionState
            return
        }

        if (killSwitchState == 1 && ignitionState == 1 &&
            !(prevKill == 1 && prevIgn == 1)) {
            Intent(this, EmergencyAlertService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(intent)
                else startService(intent)
            }
        }

        if (!(killSwitchState == 1 && ignitionState == 1) &&
            (prevKill == 1 && prevIgn == 1)) {
            stopService(Intent(this, EmergencyAlertService::class.java))
        }

        prevKill = killSwitchState
        prevIgn = ignitionState
    }

    private fun startHeartbeatChecker() {
        handler.post(object : Runnable {
            override fun run() {
                val isOnline = (System.currentTimeMillis() - lastHeartbeatMillis) <= 8000
                statusBadge.text = if (isOnline) "Online" else "Offline"
                statusBadge.setBackgroundResource(
                    if (isOnline) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                )
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun setupButtonActions() {
        killSwitchBtn.setOnClickListener {
            firebaseRef.child("killSwitch").setValue(if (killSwitchState == 1) 0 else 1)
        }

        openMapsBtn.setOnClickListener {
            currentLatLng?.let {
                val uri = Uri.parse("geo:${it.latitude},${it.longitude}?q=${it.latitude},${it.longitude}(Tracker)")
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                })
            }
        }

        viewLogsBtn.setOnClickListener {
            startActivity(Intent(this, IgnitionLogActivity::class.java))
        }
    }

    private fun scheduleServiceRestartWorker() {
        val work = PeriodicWorkRequestBuilder<ServiceRestartWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ServiceRestart",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
