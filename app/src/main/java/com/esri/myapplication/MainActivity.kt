package com.esri.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task

class MainActivity : AppCompatActivity() {

  private companion object {
    const val TAG = "MainActivity"

    fun log(s: String) = Log.i(TAG, s)
    fun isGranted(context: Context, permission: String): Boolean =
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun logNewLocation(location: Location) = log("Location updated to $location")
  }

  // Keep this in sync with the manifest.
  private val isRequestingFineLocation = false

  @SuppressLint("VisibleForTests")
  private lateinit var fusedLocationClient: FusedLocationProviderClient

  private lateinit var locationManager: LocationManager

  private val locationLogger =
    //::logLocationWithFusedLocationClient
    ::logLocationWithLocationManager

  private val fusedLocationClientLocationCallback: LocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
      logNewLocation(locationResult.lastLocation)
    }
  }

  private val locationManagerLocationCallback = LocationListener { logNewLocation(it) }

  private lateinit var permissionRequest: ActivityResultLauncher<Array<String>>

  override fun onCreate(savedInstanceState: Bundle?) {
    fun printGranted(name: String) = log("$name permission granted!")

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      val isFinePermissionGranted = it.getOrElse(Manifest.permission.ACCESS_FINE_LOCATION, { false })
      val isCoarsePermissionGranted = it.getOrElse(Manifest.permission.ACCESS_COARSE_LOCATION, { false })

      if (isCoarsePermissionGranted) {
        if (isFinePermissionGranted) {
          printGranted("Fine location")
        }
        printGranted("Coarse location")
        locationLogger()
      } else {
        printGranted("No")
      }
    }

    requestMissingPermissionsAndLogLocation(locationLogger)
  }

  private fun requestMissingPermissionsAndLogLocation(locationLogger: () -> Unit) {
    when {
      isGranted(this, Manifest.permission.ACCESS_COARSE_LOCATION) &&
          (isGranted(this, Manifest.permission.ACCESS_FINE_LOCATION) || !isRequestingFineLocation) -> {
        log("Permissions already granted!")
        locationLogger()
      }
      else -> {
        permissionRequest.launch(
          if (isRequestingFineLocation) arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
          )
          else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun logLocationWithLocationManager() {
    val providersList = listOf(
      LocationManager.FUSED_PROVIDER,
      LocationManager.GPS_PROVIDER,
      LocationManager.NETWORK_PROVIDER,
      //LocationManager.PASSIVE_PROVIDER
    )

    fun printProviderStatus(locationManager: LocationManager) {
      fun printEnabled(p: String) = log("$p provider enabled")
      fun printHas(p: String) = log("$p provider exists for device")

      providersList.filter { locationManager.isProviderEnabled(it) }.forEach(::printEnabled)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        providersList.filter { locationManager.hasProvider(it) }.forEach(::printHas)
      }
    }

    printProviderStatus(locationManager)
    log("Starting to receive location updates.")
    providersList.forEach {
      log("LocationManager: using $it provider")
      locationManager.requestLocationUpdates(it, 5000, 0f, locationManagerLocationCallback)
    }
  }

  @SuppressLint("MissingPermission")
  private fun logLocationWithFusedLocationClient() {
    val locationRequest = LocationRequest.create().apply {
      interval = 10000
      fastestInterval = 5000
      priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
    val client: SettingsClient = LocationServices.getSettingsClient(this)
    val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
    task.addOnSuccessListener {
      log("Starting to receive location updates.")
      fusedLocationClient.requestLocationUpdates(
        locationRequest,
        fusedLocationClientLocationCallback,
        Looper.getMainLooper()
      )
    }
    task.addOnFailureListener {
      log("User needs to change their settings...")
    }
  }

}