package com.example.vehicletrackerapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class IgnitionLogActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val logList = mutableListOf<String>()
    private val locationList = mutableListOf<Pair<Double, Double>>()
    private val prefs by lazy { getSharedPreferences("ignition_logs", Context.MODE_PRIVATE) }
    private val gson = Gson()
    private val firebaseRef = FirebaseDatabase.getInstance().reference
    private var lastIgnitionState = -1  // -1 to force first read

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ignition_log)

        listView = findViewById(R.id.logListView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logList)
        listView.adapter = adapter

        loadLocalLogs()
        watchIgnition()

        listView.setOnItemClickListener { _, _, pos, _ ->
            val (lat, lng) = locationList[pos]
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Ignition)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }

        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            logList.clear()
            locationList.clear()
            adapter.notifyDataSetChanged()
            prefs.edit().clear().apply()
        }
    }

    private fun watchIgnition() {
        firebaseRef.child("ignition").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(Int::class.java) ?: return

                if (state == 1 && lastIgnitionState == 0) {
                    val lat = snapshot.child("lat").getValue(Double::class.java) ?: return
                    val lng = snapshot.child("lng").getValue(Double::class.java) ?: return
                    val time = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                        .format(System.currentTimeMillis())
                    val entry = "Ignition ON at $time"

                    logList.add(0, entry)
                    locationList.add(0, Pair(lat, lng))
                    adapter.notifyDataSetChanged()
                    saveLogs()
                }

                lastIgnitionState = state
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveLogs() {
        prefs.edit()
            .putString("log_list", gson.toJson(logList))
            .putString("location_list", gson.toJson(locationList))
            .apply()
    }

    private fun loadLocalLogs() {
        val logsJson = prefs.getString("log_list", null)
        val coordsJson = prefs.getString("location_list", null)

        if (!logsJson.isNullOrEmpty() && !coordsJson.isNullOrEmpty()) {
            val typeList = object : TypeToken<MutableList<String>>() {}.type
            val typeCoords = object : TypeToken<MutableList<Pair<Double, Double>>>() {}.type
            logList.addAll(gson.fromJson(logsJson, typeList))
            locationList.addAll(gson.fromJson(coordsJson, typeCoords))
            adapter.notifyDataSetChanged()
        }
    }
}
