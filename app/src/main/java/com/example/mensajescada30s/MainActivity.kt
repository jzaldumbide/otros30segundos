package com.example.mensajescada30s

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var tvMensaje: TextView
    private lateinit var tvLatLong: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var contador = 1

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Supabase info
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_KEY
    private val tableName = "locations"


    private val mensajeRunnable = object : Runnable {
        override fun run() {
            obtenerUbicacionYEnviar()
            tvMensaje.text = "Ubicación enviada $contador"
            contador++
            handler.postDelayed(this, 10000) // cada 10 segundos
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLatLong = findViewById(R.id.tvLatLong)
        tvMensaje = findViewById(R.id.tvMensaje)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        verificarPermisosUbicacion()
    }

    private fun verificarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            mostrarDialogoPermisoEnSegundoPlano()
        } else {
            handler.post(mensajeRunnable)
        }
    }

    private fun mostrarDialogoPermisoEnSegundoPlano() {
        AlertDialog.Builder(this)
            .setTitle("Permiso en segundo plano requerido")
            .setMessage("Para enviar ubicación en segundo plano, activa el permiso en la configuración.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            verificarPermisosUbicacion() // vuelve a verificar background
        } else {
            tvMensaje.text = "Permiso de ubicación no concedido"
        }
    }

    private fun obtenerUbicacionYEnviar() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val lat = it.latitude
                val lon = it.longitude
                tvLatLong.text = "Lat: $lat Long: $lon"
                enviarUbicacionASupabase(lat, lon)
            }
        }
    }

    private fun enviarUbicacionASupabase(lat: Double, lon: Double) {
        val client = OkHttpClient()
        val json = JSONObject()
        json.put("latitude", lat)
        json.put("longitude", lon)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/$tableName")
            .post(body)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvMensaje.text = "Error al enviar ubicación"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvMensaje.text = "Ubicación enviada $contador"
                        contador++
                    } else {
                        tvMensaje.text = "Error en respuesta Supabase"
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(mensajeRunnable)
    }
}
