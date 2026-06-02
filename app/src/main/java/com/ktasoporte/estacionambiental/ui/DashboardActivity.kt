package com.ktasoporte.estacionambiental.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ktasoporte.estacionambiental.R
import com.ktasoporte.estacionambiental.models.AlarmCommand
import com.ktasoporte.estacionambiental.models.PomodoroCommand
import com.ktasoporte.estacionambiental.models.TelemetryData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvTemperaturaValue: TextView
    private lateinit var tvHumedadValue: TextView
    private lateinit var tvCO2Value: TextView
    private lateinit var tvTVOCValue: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var switchExtractor: SwitchCompat

    // Pomodoro Views
    private lateinit var rgPomodoroMinutes: RadioGroup
    private lateinit var btnPomodoroStart: Button
    private lateinit var btnPomodoroPause: Button
    private lateinit var btnPomodoroStop: Button

    // Alarm Views
    private lateinit var etAlarmaHora: EditText
    private lateinit var etAlarmaMinuto: EditText
    private lateinit var btnGuardarAlarma: Button
    private lateinit var switchAlarma: SwitchCompat

    private lateinit var database: FirebaseDatabase
    private lateinit var sensoresRef: DatabaseReference
    private lateinit var controlRef: DatabaseReference
    private lateinit var pomodoroRef: DatabaseReference
    private lateinit var alarmaRef: DatabaseReference

    private var sensoresListener: ValueEventListener? = null
    private var controlListener: ValueEventListener? = null

    private var alertaDisparada = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permiso de notificaciones concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Las alertas de CO2 no podrán mostrar notificaciones", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Inicializar elementos de la vista de telemetría y extractor
        tvTemperaturaValue = findViewById(R.id.tvTemperaturaValue)
        tvHumedadValue = findViewById(R.id.tvHumedadValue)
        tvCO2Value = findViewById(R.id.tvCO2Value)
        tvTVOCValue = findViewById(R.id.tvTVOCValue)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        switchExtractor = findViewById(R.id.switchExtractor)

        // Inicializar elementos de Pomodoro
        rgPomodoroMinutes = findViewById(R.id.rgPomodoroMinutes)
        btnPomodoroStart = findViewById(R.id.btnPomodoroStart)
        btnPomodoroPause = findViewById(R.id.btnPomodoroPause)
        btnPomodoroStop = findViewById(R.id.btnPomodoroStop)

        // Inicializar elementos de Alarma
        etAlarmaHora = findViewById(R.id.etAlarmaHora)
        etAlarmaMinuto = findViewById(R.id.etAlarmaMinuto)
        btnGuardarAlarma = findViewById(R.id.btnGuardarAlarma)
        switchAlarma = findViewById(R.id.switchAlarma)

        // Inicializar Firebase Realtime Database
        database = FirebaseDatabase.getInstance()
        sensoresRef = database.getReference("estacion/sensores")
        controlRef = database.getReference("estacion/control/extractor_activo")
        pomodoroRef = database.getReference("estacion/control/pomodoro")
        alarmaRef = database.getReference("estacion/control/alarma")

        // Crear canal de notificaciones y solicitar permisos si aplica
        createNotificationChannel()
        checkNotificationPermission()

        // Configurar listeners de telemetría y extractor
        setupTelemetryListener()
        setupControlSyncListener()
        setupControlWriter()

        // Configurar listeners de Pomodoro y Alarma
        setupPomodoroControls()
        setupAlarmControls()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Calidad de Aire"
            val descriptionText = "Notificaciones locales para niveles de CO2 críticos"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("alerta_co2", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendCO2Notification(co2Value: Int) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "alerta_co2")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Alerta de Calidad de Aire")
            .setContentText("Peligro: Niveles de CO2 críticos detectados ($co2Value ppm)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@DashboardActivity,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(1001, builder.build())
            }
        }
    }

    private fun setupTelemetryListener() {
        sensoresListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val telemetry = snapshot.getValue(TelemetryData::class.java)
                    if (telemetry != null) {
                        updateTelemetryUI(telemetry)

                        // Lógica de alerta de CO2
                        if (telemetry.co2 > 1800 && !alertaDisparada) {
                            sendCO2Notification(telemetry.co2)
                            alertaDisparada = true
                        } else if (telemetry.co2 < 1500) {
                            alertaDisparada = false
                        }
                    } else {
                        Log.w("DashboardActivity", "Los datos de telemetría son nulos.")
                    }
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error al parsear datos de telemetría", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("DashboardActivity", "Error en lectura de base de datos: ${error.message}")
                Toast.makeText(this@DashboardActivity, "Error al cargar telemetría: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        sensoresRef.addValueEventListener(sensoresListener!!)
    }

    private fun setupControlSyncListener() {
        controlListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isExtractorActive = snapshot.getValue(Boolean::class.java) ?: false
                switchExtractor.setOnCheckedChangeListener(null)
                switchExtractor.isChecked = isExtractorActive
                setupControlWriter()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("DashboardActivity", "Error al leer control: ${error.message}")
            }
        }
        controlRef.addValueEventListener(controlListener!!)
    }

    private fun setupControlWriter() {
        switchExtractor.setOnCheckedChangeListener { _, isChecked ->
            controlRef.setValue(isChecked)
                .addOnSuccessListener {
                    Log.d("DashboardActivity", "Estado del extractor actualizado a: $isChecked")
                }
                .addOnFailureListener { e ->
                    Log.e("DashboardActivity", "Error al escribir en control", e)
                    Toast.makeText(this, "Fallo al actualizar extractor: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupPomodoroControls() {
        btnPomodoroStart.setOnClickListener {
            val minutes = getSelectedPomodoroMinutes()
            writePomodoroCommand("START", minutes)
        }

        btnPomodoroPause.setOnClickListener {
            val minutes = getSelectedPomodoroMinutes()
            writePomodoroCommand("PAUSE", minutes)
        }

        btnPomodoroStop.setOnClickListener {
            writePomodoroCommand("STOP", 0)
        }
    }

    private fun getSelectedPomodoroMinutes(): Int {
        return when (rgPomodoroMinutes.checkedRadioButtonId) {
            R.id.rbPomodoro5 -> 5
            R.id.rbPomodoro15 -> 15
            R.id.rbPomodoro30 -> 30
            else -> 5
        }
    }

    private fun writePomodoroCommand(comando: String, minutos: Int) {
        val command = PomodoroCommand(comando, minutos)
        pomodoroRef.setValue(command)
            .addOnSuccessListener {
                Log.d("DashboardActivity", "Comando Pomodoro enviado: $comando con $minutos minutos")
            }
            .addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error al enviar comando Pomodoro", e)
                Toast.makeText(this, "Fallo al enviar comando Pomodoro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupAlarmControls() {
        btnGuardarAlarma.setOnClickListener {
            saveAlarmCommand()
        }

        switchAlarma.setOnCheckedChangeListener { _, _ ->
            saveAlarmCommand()
        }
    }

    private fun saveAlarmCommand() {
        val horaStr = etAlarmaHora.text.toString().trim()
        val minutoStr = etAlarmaMinuto.text.toString().trim()

        val hora = if (horaStr.isNotEmpty()) horaStr.toIntOrNull() ?: 7 else 7
        val minuto = if (minutoStr.isNotEmpty()) minutoStr.toIntOrNull() ?: 0 else 0
        val activa = switchAlarma.isChecked

        val command = AlarmCommand(hora, minuto, activa)
        alarmaRef.setValue(command)
            .addOnSuccessListener {
                Log.d("DashboardActivity", "Alarma guardada: $hora:$minuto, activa: $activa")
                Toast.makeText(this, "Alarma guardada exitosamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error al guardar alarma", e)
                Toast.makeText(this, "Error al guardar alarma: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTelemetryUI(data: TelemetryData) {
        tvTemperaturaValue.text = String.format(Locale.getDefault(), "%.1f °C", data.temperatura)
        tvHumedadValue.text = String.format(Locale.getDefault(), "%.1f %%", data.humedad)
        tvCO2Value.text = String.format(Locale.getDefault(), "%d ppm", data.co2)
        tvTVOCValue.text = String.format(Locale.getDefault(), "%d mg", data.tvoc)

        if (data.bridge_ts > 0L) {
            val timestampMs = if (data.bridge_ts < 100000000000L) data.bridge_ts * 1000 else data.bridge_ts
            val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
            tvLastUpdate.text = "Última lectura: $formattedDate"
        } else {
            tvLastUpdate.text = "Última lectura: No disponible"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensoresListener?.let { sensoresRef.removeEventListener(it) }
        controlListener?.let { controlRef.removeEventListener(it) }
    }
}
