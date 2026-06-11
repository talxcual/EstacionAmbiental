package com.ktasoporte.estacionambiental.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ktasoporte.estacionambiental.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AirQualityActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var circleStatusIndicator: MaterialCardView
    private lateinit var tvStatusEmoji: TextView
    private lateinit var tvAirQualityStatus: TextView
    private lateinit var tvAirQualityDescription: TextView
    
    private lateinit var tvIALastUpdate: TextView
    private lateinit var cardConnectionError: MaterialCardView
    private lateinit var tvErrorMessage: TextView

    private lateinit var database: FirebaseDatabase
    private lateinit var iaRef: DatabaseReference
    private lateinit var connectionRef: DatabaseReference

    private var iaListener: ValueEventListener? = null
    private var connectionListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_air_quality)

        // Inicializar vistas
        btnBack = findViewById(R.id.btnBack)
        circleStatusIndicator = findViewById(R.id.circleStatusIndicator)
        tvStatusEmoji = findViewById(R.id.tvStatusEmoji)
        tvAirQualityStatus = findViewById(R.id.tvAirQualityStatus)
        tvAirQualityDescription = findViewById(R.id.tvAirQualityDescription)
        tvIALastUpdate = findViewById(R.id.tvIALastUpdate)
        cardConnectionError = findViewById(R.id.cardConnectionError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)

        // Configurar botón de volver
        btnBack.setOnClickListener {
            finish()
        }

        // Inicializar Firebase
        database = FirebaseDatabase.getInstance()
        iaRef = database.getReference("estacion/actual/estado_ia")
        connectionRef = database.getReference(".info/connected")

        // Iniciar listeners
        setupConnectionListener()
        setupAirQualityIAListener()
    }

    private fun setupConnectionListener() {
        connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                if (isConnected) {
                    // Si la conexión global de Firebase está activa, el listener de la IA gestionará el estado del error
                    // (por si el nodo en sí no existe).
                } else {
                    showError("Conexión perdida con el servidor de Firebase. Intentando reconectar...")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("AirQualityActivity", "Error al escuchar estado de conexión: ${error.message}")
            }
        }
        connectionRef.addValueEventListener(connectionListener!!)
    }

    private fun setupAirQualityIAListener() {
        iaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        showError("El nodo de clasificación IA ('estacion/actual/estado_ia') no existe o está vacío temporalmente.")
                        updateStatusOffline("SIN CONFIGURAR", "Esperando que el ESP32 envíe la primera clasificación...")
                        return
                    }

                    val estado = snapshot.getValue(String::class.java)
                    if (estado != null) {
                        updateUIWithStatus(estado)
                        cardConnectionError.visibility = View.GONE // Ocultar error si se cargó correctamente
                    } else {
                        showError("Los datos recibidos del nodo IA tienen un formato inválido.")
                        updateStatusOffline("DATOS INVÁLIDOS", "El formato del valor almacenado en Firebase no es un String.")
                    }
                } catch (e: Exception) {
                    Log.e("AirQualityActivity", "Error al procesar el estado de la IA", e)
                    showError("Error interno al parsear la clasificación: ${e.localizedMessage}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AirQualityActivity", "Error en Firebase: ${error.message}")
                showError("Error de lectura en Firebase: ${error.message}")
                updateStatusOffline("ERROR", "No se pudo sincronizar con la base de datos.")
            }
        }
        iaRef.addValueEventListener(iaListener!!)
    }

    private fun updateUIWithStatus(status: String) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val lastUpdateStr = dateFormat.format(Date())
        tvIALastUpdate.text = lastUpdateStr

        when (status.uppercase(Locale.getDefault())) {
            "AIRE_LIMPIO" -> {
                // Configurar textos
                tvAirQualityStatus.text = "AIRE LIMPIO"
                tvAirQualityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
                tvAirQualityDescription.text = "Clasificación IA: Entorno seguro. Los niveles de gases detectados por la red neuronal del ESP32 son óptimos."
                tvStatusEmoji.text = "🍃"

                // Cambiar el diseño del indicador circular (Brillo verde)
                circleStatusIndicator.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_mint_trans))
                circleStatusIndicator.strokeColor = ContextCompat.getColor(this, R.color.accent_mint)
            }
            "AIRE_CONTAMINADO" -> {
                // Configurar textos
                tvAirQualityStatus.text = "AIRE CONTAMINADO"
                tvAirQualityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                tvAirQualityDescription.text = "Clasificación IA: ¡Alerta de contaminación! Se aconseja encender el extractor de aire y ventilar la habitación inmediatamente."
                tvStatusEmoji.text = "🚨"

                // Cambiar el diseño del indicador circular (Brillo rojo)
                circleStatusIndicator.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_red_trans))
                circleStatusIndicator.strokeColor = ContextCompat.getColor(this, R.color.accent_red)
            }
            else -> {
                // Clasificación desconocida de TinyML
                tvAirQualityStatus.text = status.uppercase(Locale.getDefault())
                tvAirQualityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
                tvAirQualityDescription.text = "Clasificación IA: Estado desconocido detectado ('$status'). La red neuronal devolvió un parámetro no registrado en la aplicación."
                tvStatusEmoji.text = "❓"

                // Cambiar el diseño del indicador circular (Brillo amarillo)
                circleStatusIndicator.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_yellow_trans))
                circleStatusIndicator.strokeColor = ContextCompat.getColor(this, R.color.accent_yellow)
            }
        }
    }

    private fun updateStatusOffline(title: String, description: String) {
        tvAirQualityStatus.text = title
        tvAirQualityStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvAirQualityDescription.text = description
        tvStatusEmoji.text = "📡"

        // Diseño en gris/apagado
        circleStatusIndicator.setCardBackgroundColor(ColorStateList.valueOf(0x0AFFFFFF))
        circleStatusIndicator.strokeColor = ContextCompat.getColor(this, R.color.card_border)
        tvIALastUpdate.text = "No disponible"
    }

    private fun showError(message: String) {
        tvErrorMessage.text = message
        cardConnectionError.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar listeners para evitar memory leaks
        iaListener?.let { iaRef.removeEventListener(it) }
        connectionListener?.let { connectionRef.removeEventListener(it) }
    }
}
