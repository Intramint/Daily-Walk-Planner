package com.marcin.dailywalkplanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.navigation.NavigationView
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.auth.FirebaseAuth

import java.io.IOException
import java.util.Locale
import kotlin.math.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var currentLocationText: TextView
    private lateinit var otherText: TextView
    private lateinit var recordButton: Button
    private lateinit var stopButton: Button
    private lateinit var pauseButton: Button
    private lateinit var timerTextView: TextView
    private lateinit var distanceTextView: TextView

    private lateinit var minusFiveButton: Button
    private lateinit var minusOneButton: Button
    private lateinit var plusOneButton: Button
    private lateinit var plusFiveButton: Button
    private lateinit var adjustButtonsLayout: LinearLayout

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var firebaseAuth: FirebaseAuth

    private var activeTextViewId: Int = R.id.otherText

    private var currentLocationMarker: Marker? = null
    private var otherLocationMarker: Marker? = null

    private var isRecording = false
    private var isPaused = false

    private var routePolyline: Polyline? = null

    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            secondsElapsed++
            val minutes = secondsElapsed / 60
            val seconds = secondsElapsed % 60
            timerTextView.text = String.format("%02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    private val routePoints = mutableListOf<LatLng>()
    private val CHECK_ALL_WALKS_REQUEST = 1001

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseAuth = FirebaseAuth.getInstance()

        currentLocationText = findViewById(R.id.currentLocationText)
        otherText = findViewById(R.id.otherText)
        recordButton = findViewById(R.id.recordButton)
        stopButton = findViewById(R.id.stopButton)
        pauseButton = findViewById(R.id.pauseButton)
        timerTextView = findViewById(R.id.timerTextView)
        distanceTextView = findViewById(R.id.distanceTextView)

        adjustButtonsLayout = findViewById(R.id.adjustButtonsLayout)
        minusFiveButton = findViewById(R.id.minusFiveButton)
        minusOneButton = findViewById(R.id.minusOneButton)
        plusOneButton = findViewById(R.id.plusOneButton)
        plusFiveButton = findViewById(R.id.plusFiveButton)

        val greenColor = ContextCompat.getColor(this, R.color.green)
        val lightGreenColor = ContextCompat.getColor(this, R.color.light_green)

        val buttons = listOf(
            recordButton, stopButton, pauseButton,
            minusFiveButton, minusOneButton, plusOneButton, plusFiveButton
        )
        for (button in buttons) {
            button.setBackgroundColor(greenColor)
            button.setTextColor(lightGreenColor)
        }

        pauseButton.visibility = View.GONE
        adjustButtonsLayout.visibility = View.GONE

        val toolbar = findViewById<Toolbar>(R.id.topToolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)

        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    firebaseAuth.signOut()
                    val intent = Intent(this, SignInActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }
                R.id.nav_check_all_walks -> {
                    val intent = Intent(this, CheckAllWalksActivity::class.java)
                    startActivityForResult(intent, CHECK_ALL_WALKS_REQUEST)
                    true
                }

                else -> false
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (!isRecording || isPaused) return

                for (location in locationResult.locations) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    routePoints.add(latLng)
                    if (routePolyline == null) {
                        routePolyline = map.addPolyline(
                            PolylineOptions()
                                .addAll(routePoints)
                                .color(ContextCompat.getColor(this@MainActivity, R.color.green))
                                .width(8f)
                        )
                    } else {
                        routePolyline?.points = routePoints
                    }
                }
            }
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        currentLocationText.setOnClickListener {
            if (!isRecording) {
                activeTextViewId = R.id.currentLocationText
                updateActiveTextView()
            }
        }
        otherText.setOnClickListener {
            if (!isRecording) {
                activeTextViewId = R.id.otherText
                updateActiveTextView()
            }
        }

        recordButton.setOnClickListener {
            currentLocationText.visibility = View.GONE
            otherText.visibility = View.GONE

            stopButton.visibility = View.VISIBLE
            pauseButton.visibility = View.VISIBLE
            recordButton.visibility = View.GONE

            adjustButtonsLayout.visibility = View.VISIBLE

            timerTextView.visibility = View.VISIBLE
            secondsElapsed = 0
            timerTextView.text = "00:00"
            handler.postDelayed(timerRunnable, 1000)

            isRecording = true
            isPaused = false
            pauseButton.text = "Pause"

            routePoints.clear()
            routePolyline?.remove()
            routePolyline = null
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            }

        }

        stopButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Save Walk")
                .setMessage("Do you want to save this walk?")
                .setPositiveButton("Yes") { dialog, _ ->
                    val totalDistance = calculateTotalDistance(routePoints)
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    val walkData = hashMapOf(
                        "user_id" to userId,
                        "start_address" to currentLocationText.text.toString(),
                        "start_coords" to hashMapOf(
                            "lat" to currentLocationMarker?.position?.latitude,
                            "lng" to currentLocationMarker?.position?.longitude
                        ),
                        "destination_address" to otherText.text.toString(),
                        "destination_coords" to hashMapOf(
                            "lat" to otherLocationMarker?.position?.latitude,
                            "lng" to otherLocationMarker?.position?.longitude
                        ),
                        "duration_seconds" to secondsElapsed,
                        "points" to routePoints.map { point ->
                            hashMapOf("lat" to point.latitude, "lng" to point.longitude)
                        },
                        "total_distance_meters" to totalDistance
                    )

                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    firestore.collection("walks")
                        .add(walkData)
                        .addOnSuccessListener {
                            android.widget.Toast.makeText(this, "Walk saved!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            android.widget.Toast.makeText(this, "Error saving walk.", android.widget.Toast.LENGTH_SHORT).show()
                        }

                    dialog.dismiss()
                }

                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

            currentLocationText.visibility = View.VISIBLE
            otherText.visibility = View.VISIBLE

            stopButton.visibility = View.GONE
            pauseButton.visibility = View.GONE
            recordButton.visibility = View.VISIBLE

            adjustButtonsLayout.visibility = View.GONE

            timerTextView.visibility = View.GONE
            handler.removeCallbacks(timerRunnable)

            isRecording = false
            isPaused = false

            routePolyline?.remove()
            routePolyline = null

            fusedLocationClient.removeLocationUpdates(locationCallback)

        }

        pauseButton.setOnClickListener {
            if (isPaused) {
                handler.postDelayed(timerRunnable, 1000)
                pauseButton.text = "Pause"
                isPaused = false

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                }

            } else {
                handler.removeCallbacks(timerRunnable)
                pauseButton.text = "Unpause"
                isPaused = true

                fusedLocationClient.removeLocationUpdates(locationCallback)

            }
        }

        minusFiveButton.setOnClickListener {
            adjustTimer(-5)
        }

        minusOneButton.setOnClickListener {
            adjustTimer(-1)
        }

        plusOneButton.setOnClickListener {
            adjustTimer(1)
        }

        plusFiveButton.setOnClickListener {
            adjustTimer(5 )
        }

        updateActiveTextView()
        checkIfReadyToRecord()
    }

    private fun adjustTimer(seconds: Int) {
        secondsElapsed = (secondsElapsed + seconds).coerceAtLeast(0)
        val minutes = secondsElapsed / 60
        val secs = secondsElapsed % 60
        timerTextView.text = String.format("%02d:%02d", minutes, secs)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            getCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        map.setOnMapClickListener { latLng ->
            if (isRecording) return@setOnMapClickListener

            val address = getAddressFromLatLng(latLng)
            if (activeTextViewId == R.id.currentLocationText) {
                currentLocationText.text = address

                currentLocationMarker?.remove()
                currentLocationMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Start")
                )
            } else {
                otherText.text = address

                otherLocationMarker?.remove()
                otherLocationMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Destination")
                )
            }
            checkIfReadyToRecord()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                val address = getAddressFromLocation(it)
                currentLocationText.text = address
                otherText.text = "Destination"

                currentLocationMarker?.remove()
                currentLocationMarker = map.addMarker(
                    MarkerOptions()
                        .position(currentLatLng)
                        .title("You're here")
                )
                checkIfReadyToRecord()
            }
        }
    }

    private fun getAddressFromLocation(location: Location): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                address.getAddressLine(0) ?: "Unknown address"
            } else {
                "Unknown address"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            "Geocoding error"
        }
    }

    private fun getAddressFromLatLng(latLng: LatLng): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                address.getAddressLine(0) ?: "Nieznany adres"
            } else {
                "Adres nieznany"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            "Błąd geokodowania"
        }
    }

    private fun updateActiveTextView() {
        val greenColor = ContextCompat.getColor(this, R.color.green)
        val whiteColor = ContextCompat.getColor(this, R.color.ghost_white)

        if (activeTextViewId == R.id.currentLocationText) {
            currentLocationText.setBackgroundColor(greenColor)
            otherText.setBackgroundColor(whiteColor)
        } else {
            currentLocationText.setBackgroundColor(whiteColor)
            otherText.setBackgroundColor(greenColor)
        }
    }

    private fun checkIfReadyToRecord() {
        val currentText = currentLocationText.text.toString()
        val otherTextValue = otherText.text.toString()

        val ready = currentText.isNotBlank() && otherTextValue.isNotBlank() &&
                currentText != "Destination" && otherTextValue != "Destination"

        if (ready && currentLocationMarker != null && otherLocationMarker != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(
                currentLocationMarker!!.position.latitude,
                currentLocationMarker!!.position.longitude,
                otherLocationMarker!!.position.latitude,
                otherLocationMarker!!.position.longitude,
                distance
            )

            val distanceMeters = distance[0].roundToInt()
            distanceTextView.text = "Distance: $distanceMeters m"
            distanceTextView.visibility = View.VISIBLE
        } else {
            distanceTextView.visibility = View.GONE
        }

        recordButton.visibility = if (ready && !isRecording) View.VISIBLE else View.GONE
    }

    private fun calculateTotalDistance(points: List<LatLng>): Double {
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            val result = FloatArray(1)
            Location.distanceBetween(
                start.latitude, start.longitude,
                end.latitude, end.longitude,
                result
            )
            totalDistance += result[0]
        }
        return totalDistance
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    map.isMyLocationEnabled = true
                    getCurrentLocation()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val start = data?.getStringExtra("start_address")
            val destination = data?.getStringExtra("destination_address")

            val startTextView = findViewById<TextView>(R.id.currentLocationText)
            val destinationTextView = findViewById<TextView>(R.id.otherText)

            startTextView.text = start
            destinationTextView.text = destination
        }
    }

}