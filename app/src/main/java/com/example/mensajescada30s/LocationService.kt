package com.example.mensajescada30s

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private val intervalo: Long = 10000 // 10 segundos

    private val supabaseUrl = "https://rubsmsszczixgemppvar.supabase.co"
    private val supabaseKey = ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ1YnNtc3N6Y3ppeGdlbXBwdmFyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTMwOTQxNTQsImV4cCI6MjA2ODY3MDE1NH0.JimObZdYD5375tD5yFBESlHj_yBOFTawfYEA3IzzV3Q"
    private val tableName = "locations"

    private val runnable = object : Runnable {
        override fun run() {
            enviarUbicacion()
            handler.postDelayed(this, intervalo)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundServiceWithNotification()
        handler.post(runnable)
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Seguimiento de ubicación",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Enviando ubicación")
            .setContentText("Tu ubicación se actualiza cada 10 segundos.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)
    }

    private fun enviarUbicacion() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val lat = it.latitude
                    val lon = it.longitude

                    val json = JSONObject()
                    json.put("latitude", lat)
                    json.put("longitude", lon)

                    val client = OkHttpClient()
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("$supabaseUrl/rest/v1/$tableName")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {}
                        override fun onResponse(call: Call, response: Response) {}
                    })
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
