package com.marcin.dailywalkplanner

import WalkAdapter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.app.Activity
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.marcin.dailywalkplanner.Walk

class CheckAllWalksActivity : AppCompatActivity() {
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WalkAdapter
    private val walks = mutableListOf<Walk>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_all_walks)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WalkAdapter(walks) { selectedWalk ->
            val intent = Intent()
            intent.putExtra("selected_start", selectedWalk.startAddress)
            intent.putExtra("selected_destination", selectedWalk.destinationAddress)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
        recyclerView.adapter = adapter

        fetchWalksFromFirestore()
    }

    private fun fetchWalksFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.collection("walks")
            .whereEqualTo("user_id", userId)
            .get()
            .addOnSuccessListener { result ->
                walks.clear()
                for (document in result) {
                    val start = document.getString("start_address") ?: ""
                    val destination = document.getString("destination_address") ?: ""
                    val durationSec = document.getLong("duration_seconds") ?: 0
                    val distanceMeters = document.getLong("total_distance_meters") ?: 0

                    val durationText = if (durationSec >= 60) {
                        "${durationSec / 60} min"
                    } else {
                        "$durationSec sec"
                    }
                    val distanceText = "${distanceMeters} m"

                    walks.add(Walk(start, destination, durationText, distanceText))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load walks.", Toast.LENGTH_SHORT).show()
            }
    }
}

data class Walk(
    val startAddress: String,
    val destinationAddress: String,
    val duration: String,
    val distance: String
)