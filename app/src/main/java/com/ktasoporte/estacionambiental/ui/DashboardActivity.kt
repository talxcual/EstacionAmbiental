package com.ktasoporte.estacionambiental.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ktasoporte.estacionambiental.R
import com.ktasoporte.estacionambiental.models.AlarmCommand
import com.ktasoporte.estacionambiental.models.PomodoroCommand
import com.ktasoporte.estacionambiental.models.TelemetryData
import com.ktasoporte.estacionambiental.utils.TelemetryDatabaseHelper
import java.io.File
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

    // Nuevas vistas del Bento Grid de Sensores
    private lateinit var tvCO2Status: TextView
    private lateinit var tvTempStatus: TextView
    private lateinit var tvHumedadStatus: TextView
    private lateinit var tvTVOCStatus: TextView

    // Banner Superior
    private lateinit var cardAmbientalStatus: MaterialCardView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusText: TextView

    // Extractor
    private lateinit var fanContainer: FrameLayout
    private lateinit var tvFanIcon: TextView
    private lateinit var tvExtractorStatusLabel: TextView
    private var fanAnimator: ObjectAnimator? = null

    // Pomodoro Views
    private lateinit var rgPomodoroMinutes: RadioGroup
    private lateinit var btnPomodoroStart: Button
    private lateinit var btnPomodoroPause: Button
    private lateinit var btnPomodoroStop: Button
    private lateinit var pbPomodoro: ProgressBar
    private lateinit var pbPomodoroInner: ProgressBar
    private lateinit var tvPomodoroTime: TextView
    private var pomodoroOuterAnimator: ObjectAnimator? = null
    private var pomodoroInnerAnimator: ObjectAnimator? = null

    // Alarm Views
    private lateinit var layoutTimePickerTrigger: LinearLayout
    private lateinit var tvAlarmaHoraText: TextView
    private lateinit var tvAlarmaAmPm: TextView
    private lateinit var tvAlarmaStatusMessage: TextView
    private lateinit var switchAlarma: SwitchCompat

    // IA Navigation
    private lateinit var cardAirQualityIA: MaterialCardView

    // Pestañas (Tab layouts)
    private lateinit var viewHome: View
    private lateinit var viewCharts: View
    private lateinit var viewSettings: View

    // Botones e iconos de navegación inferior
    private lateinit var ivNavHomeIcon: ImageView
    private lateinit var ivNavChartsIcon: ImageView
    private lateinit var ivNavSettingsIcon: ImageView
    private lateinit var ivNavProfileIcon: ImageView

    // Componentes del gráfico e historial
    private lateinit var cyberChartsView: CyberpunkChartView
    private lateinit var btnExportCSV: Button
    private lateinit var dbHelper: TelemetryDatabaseHelper
    private var lastRecordedTs: Long = 0L

    // Barras de indicación LED de temperatura
    private lateinit var tempBar1: View
    private lateinit var tempBar2: View
    private lateinit var tempBar3: View
    private lateinit var tempBar4: View
    private lateinit var tempBar5: View

    private lateinit var database: FirebaseDatabase
    private lateinit var sensoresRef: DatabaseReference
    private lateinit var controlRef: DatabaseReference
    private lateinit var pomodoroRef: DatabaseReference
    private lateinit var alarmaRef: DatabaseReference
    private lateinit var iaRef: DatabaseReference

    private var sensoresListener: ValueEventListener? = null
    private var controlListener: ValueEventListener? = null
    private var alarmaListener: ValueEventListener? = null
    private var iaListener: ValueEventListener? = null

    private var alarmMediaPlayer: MediaPlayer? = null

    // Estados Locales
    private var alarmaHora = 7
    private var alarmaMinuto = 30

    private var pomodoroTimer: CountDownTimer? = null
    private var pomodoroSecondsRemaining = 25 * 60L
    private var pomodoroTotalSeconds = 25 * 60L
    private var isPomodoroRunning = false
    private var isPomodoroPaused = false

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

        // Inicializar Base de Datos Local
        dbHelper = TelemetryDatabaseHelper(this)

        // Inicializar vistas de telemetría
        tvTemperaturaValue = findViewById(R.id.tvTemperaturaValue)
        tvHumedadValue = findViewById(R.id.tvHumedadValue)
        tvCO2Value = findViewById(R.id.tvCO2Value)
        tvTVOCValue = findViewById(R.id.tvTVOCValue)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        switchExtractor = findViewById(R.id.switchExtractor)

        // Inicializar vistas del Bento Grid de Sensores
        tvCO2Status = findViewById(R.id.tvCO2Status)
        tvTempStatus = findViewById(R.id.tvTempStatus)
        tvHumedadStatus = findViewById(R.id.tvHumedadStatus)
        tvTVOCStatus = findViewById(R.id.tvTVOCStatus)

        // Inicializar Banner de Estado
        cardAmbientalStatus = findViewById(R.id.cardAmbientalStatus)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusText = findViewById(R.id.tvStatusText)

        // Inicializar Extractor
        fanContainer = findViewById(R.id.fanContainer)
        tvFanIcon = findViewById(R.id.tvFanIcon)
        tvExtractorStatusLabel = findViewById(R.id.tvExtractorStatusLabel)

        // Inicializar Pomodoro
        rgPomodoroMinutes = findViewById(R.id.rgPomodoroMinutes)
        btnPomodoroStart = findViewById(R.id.btnPomodoroStart)
        btnPomodoroPause = findViewById(R.id.btnPomodoroPause)
        btnPomodoroStop = findViewById(R.id.btnPomodoroStop)
        pbPomodoro = findViewById(R.id.pbPomodoro)
        pbPomodoroInner = findViewById(R.id.pbPomodoroInner)
        tvPomodoroTime = findViewById(R.id.tvPomodoroTime)

        // Inicializar Alarma
        layoutTimePickerTrigger = findViewById(R.id.layoutTimePickerTrigger)
        tvAlarmaHoraText = findViewById(R.id.tvAlarmaHoraText)
        tvAlarmaAmPm = findViewById(R.id.tvAlarmaAmPm)
        tvAlarmaStatusMessage = findViewById(R.id.tvAlarmaStatusMessage)
        switchAlarma = findViewById(R.id.switchAlarma)

        // Inicializar IA Navigation
        cardAirQualityIA = findViewById(R.id.cardAirQualityIA)
        cardAirQualityIA.setOnClickListener {
            val intent = Intent(this, AirQualityActivity::class.java)
            startActivity(intent)
        }

        // Inicializar Pestañas (Tab Layouts)
        viewHome = findViewById(R.id.viewHome)
        viewCharts = findViewById(R.id.viewCharts)
        viewSettings = findViewById(R.id.viewSettings)

        // Inicializar Barra de Navegación Inferior
        ivNavHomeIcon = findViewById(R.id.ivNavHomeIcon)
        ivNavChartsIcon = findViewById(R.id.ivNavChartsIcon)
        ivNavSettingsIcon = findViewById(R.id.ivNavSettingsIcon)
        ivNavProfileIcon = findViewById(R.id.ivNavProfileIcon)

        findViewById<View>(R.id.btnNavHome).setOnClickListener { selectTab(0) }
        findViewById<View>(R.id.btnNavCharts).setOnClickListener { selectTab(1) }
        findViewById<View>(R.id.btnNavSettings).setOnClickListener { selectTab(2) }
        findViewById<View>(R.id.btnNavProfile).setOnClickListener { showProfilePopup() }

        // Inicializar vistas del gráfico e historial
        cyberChartsView = findViewById(R.id.cyberChartsView)
        btnExportCSV = findViewById(R.id.btnExportCSV)
        btnExportCSV.setOnClickListener { exportDatabaseToCSV() }

        // Inicializar barras de indicación LED de temperatura
        tempBar1 = findViewById(R.id.tempBar1)
        tempBar2 = findViewById(R.id.tempBar2)
        tempBar3 = findViewById(R.id.tempBar3)
        tempBar4 = findViewById(R.id.tempBar4)
        tempBar5 = findViewById(R.id.tempBar5)

        // Inicializar Firebase Realtime Database
        database = FirebaseDatabase.getInstance()
        sensoresRef = database.getReference("estacion/sensores")
        controlRef = database.getReference("estacion/control/extractor_activo")
        pomodoroRef = database.getReference("estacion/control/pomodoro")
        alarmaRef = database.getReference("estacion/control/alarma")
        iaRef = database.getReference("estacion/actual/estado_ia")

        // Crear canal de notificaciones y solicitar permisos
        createNotificationChannel()
        checkNotificationPermission()

        // Configurar sincronización inicial
        setupTelemetryListener()
        setupControlSyncListener()
        setupControlWriter()

        // Configurar controles locales e interacciones
        setupPomodoroControls()
        setupAlarmControls()
        setupAlarmSyncListener()
        setupAirQualityIAListener()

        // Seleccionar pestaña por defecto (Home)
        selectTab(0)
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
                
                // Actualizar UI del extractor
                if (isExtractorActive) {
                    startFanAnimation()
                    tvExtractorStatusLabel.text = "Encendido (Manual)"
                    tvExtractorStatusLabel.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.accent_mint))
                } else {
                    stopFanAnimation()
                    tvExtractorStatusLabel.text = "Apagado / Automático"
                    tvExtractorStatusLabel.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_secondary))
                }
                setupControlWriter()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("DashboardActivity", "Error al leer control: ${error.message}")
            }
        }
        controlRef.addValueEventListener(controlListener!!)
    }

    private fun startFanAnimation() {
        if (fanAnimator == null) {
            fanAnimator = ObjectAnimator.ofFloat(tvFanIcon, "rotation", 0f, 360f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        if (fanAnimator?.isRunning == false) {
            fanAnimator?.start()
        }
    }

    private fun stopFanAnimation() {
        fanAnimator?.cancel()
        tvFanIcon.rotation = 0f
    }

    private fun startPomodoroAnimations() {
        if (pomodoroOuterAnimator == null) {
            pomodoroOuterAnimator = ObjectAnimator.ofFloat(pbPomodoro, "rotation", 0f, 360f).apply {
                duration = 12000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        if (pomodoroInnerAnimator == null) {
            pomodoroInnerAnimator = ObjectAnimator.ofFloat(pbPomodoroInner, "rotation", 180f, -180f).apply {
                duration = 9000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }

        if (pomodoroOuterAnimator?.isRunning == false) {
            pomodoroOuterAnimator?.start()
        }
        if (pomodoroInnerAnimator?.isRunning == false) {
            pomodoroInnerAnimator?.start()
        }
    }

    private fun pausePomodoroAnimations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            pomodoroOuterAnimator?.pause()
            pomodoroInnerAnimator?.pause()
        } else {
            pomodoroOuterAnimator?.cancel()
            pomodoroInnerAnimator?.cancel()
        }
    }

    private fun resumePomodoroAnimations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (pomodoroOuterAnimator?.isPaused == true) {
                pomodoroOuterAnimator?.resume()
            } else if (pomodoroOuterAnimator?.isRunning == false) {
                pomodoroOuterAnimator?.start()
            }

            if (pomodoroInnerAnimator?.isPaused == true) {
                pomodoroInnerAnimator?.resume()
            } else if (pomodoroInnerAnimator?.isRunning == false) {
                pomodoroInnerAnimator?.start()
            }
        } else {
            startPomodoroAnimations()
        }
    }

    private fun stopPomodoroAnimations() {
        pomodoroOuterAnimator?.cancel()
        pomodoroInnerAnimator?.cancel()
        pbPomodoro.rotation = 0f
        pbPomodoroInner.rotation = 180f
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
        rgPomodoroMinutes.setOnCheckedChangeListener { _, checkedId ->
            val minutes = when (checkedId) {
                R.id.rbPomodoro5 -> 5
                R.id.rbPomodoro15 -> 15
                R.id.rbPomodoro30 -> 30
                else -> 5
            }
            if (!isPomodoroRunning) {
                pomodoroSecondsRemaining = minutes * 60L
                pomodoroTotalSeconds = minutes * 60L
                updatePomodoroUI()
            }
        }

        btnPomodoroStart.setOnClickListener {
            val minutes = getSelectedPomodoroMinutes()
            writePomodoroCommand("START", minutes)
            startPomodoroLocal(pomodoroSecondsRemaining)
        }

        btnPomodoroPause.setOnClickListener {
            val minutes = getSelectedPomodoroMinutes()
            writePomodoroCommand("PAUSE", minutes)
            pausePomodoroLocal()
        }

        btnPomodoroStop.setOnClickListener {
            writePomodoroCommand("STOP", 0)
            stopPomodoroLocal()
        }

        updatePomodoroUI()
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

    private fun startPomodoroLocal(seconds: Long) {
        pomodoroTimer?.cancel()
        if (isPomodoroPaused) {
            resumePomodoroAnimations()
        } else {
            startPomodoroAnimations()
        }
        isPomodoroRunning = true
        isPomodoroPaused = false

        pomodoroTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                pomodoroSecondsRemaining = millisUntilFinished / 1000
                updatePomodoroUI()
            }

            override fun onFinish() {
                isPomodoroRunning = false
                pomodoroSecondsRemaining = pomodoroTotalSeconds
                updatePomodoroUI()
                stopPomodoroAnimations()
                Toast.makeText(this@DashboardActivity, "¡Pomodoro finalizado!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun pausePomodoroLocal() {
        if (isPomodoroRunning) {
            pomodoroTimer?.cancel()
            isPomodoroRunning = false
            isPomodoroPaused = true
            pausePomodoroAnimations()
        }
    }

    private fun stopPomodoroLocal() {
        pomodoroTimer?.cancel()
        isPomodoroRunning = false
        isPomodoroPaused = false
        pomodoroSecondsRemaining = pomodoroTotalSeconds
        updatePomodoroUI()
        stopPomodoroAnimations()
    }

    private fun updatePomodoroUI() {
        val minutes = pomodoroSecondsRemaining / 60
        val seconds = pomodoroSecondsRemaining % 60
        tvPomodoroTime.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        val progress = if (pomodoroTotalSeconds > 0) {
            ((pomodoroSecondsRemaining.toDouble() / pomodoroTotalSeconds) * 100).toInt()
        } else {
            100
        }
        pbPomodoro.progress = progress
    }

    private fun setupAlarmControls() {
        layoutTimePickerTrigger.setOnClickListener {
            val timePickerDialog = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    alarmaHora = hourOfDay
                    alarmaMinuto = minute
                    updateAlarmUI()
                    saveAlarmCommand()
                },
                alarmaHora,
                alarmaMinuto,
                false
            )
            timePickerDialog.show()
        }

        switchAlarma.setOnCheckedChangeListener { _, isChecked ->
            saveAlarmCommand()
            updateAlarmUI()
        }
    }

    private fun setupAlarmSyncListener() {
        alarmaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val alarmCmd = snapshot.getValue(AlarmCommand::class.java)
                    if (alarmCmd != null) {
                        alarmaHora = alarmCmd.hora
                        alarmaMinuto = alarmCmd.minuto
                        
                        switchAlarma.setOnCheckedChangeListener(null)
                        switchAlarma.isChecked = alarmCmd.activa
                        
                        updateAlarmUI()
                        setupAlarmControls()
                    }
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error al parsear alarma de Firebase", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("DashboardActivity", "Error al leer alarma de Firebase: ${error.message}")
            }
        }
        alarmaRef.addValueEventListener(alarmaListener!!)
    }

    private fun setupAirQualityIAListener() {
        iaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val estado = snapshot.getValue(String::class.java)
                    if (estado != null && estado.uppercase(Locale.getDefault()) == "AIRE_CONTAMINADO") {
                        if (alarmMediaPlayer == null) {
                            alarmMediaPlayer = MediaPlayer.create(this@DashboardActivity, R.raw.alarma).apply {
                                isLooping = true
                            }
                        }
                        if (alarmMediaPlayer?.isPlaying == false) {
                            alarmMediaPlayer?.start()
                            Log.d("DashboardActivity", "Alarma activada por aire contaminado.")
                        }
                    } else {
                        stopAlarmSound()
                    }
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error al procesar estado de IA para alarma", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("DashboardActivity", "Error al escuchar estado de IA: ${error.message}")
            }
        }
        iaRef.addValueEventListener(iaListener!!)
    }

    private fun stopAlarmSound() {
        alarmMediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {
                Log.e("DashboardActivity", "Error al detener MediaPlayer", e)
            } finally {
                it.release()
            }
        }
        alarmMediaPlayer = null
        Log.d("DashboardActivity", "Alarma desactivada.")
    }

    private fun saveAlarmCommand() {
        val command = AlarmCommand(alarmaHora, alarmaMinuto, switchAlarma.isChecked)
        alarmaRef.setValue(command)
            .addOnSuccessListener {
                Log.d("DashboardActivity", "Alarma guardada en Firebase: $alarmaHora:$alarmaMinuto, activa: ${switchAlarma.isChecked}")
            }
            .addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error al guardar alarma en Firebase", e)
                Toast.makeText(this, "Error al guardar alarma: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAlarmUI() {
        val amPm = if (alarmaHora >= 12) "PM" else "AM"
        val displayHour = when {
            alarmaHora == 0 -> 12
            alarmaHora > 12 -> alarmaHora - 12
            else -> alarmaHora
        }
        tvAlarmaHoraText.text = String.format(Locale.getDefault(), "%02d:%02d", displayHour, alarmaMinuto)
        tvAlarmaAmPm.text = amPm

        if (switchAlarma.isChecked) {
            tvAlarmaStatusMessage.text = String.format(Locale.getDefault(), "Sonará a las %d:%02d %s", displayHour, alarmaMinuto, amPm)
            tvAlarmaStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
        } else {
            tvAlarmaStatusMessage.text = "Alarma desactivada"
            tvAlarmaStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updateTelemetryUI(data: TelemetryData) {
        tvTemperaturaValue.text = String.format(Locale.getDefault(), "%.1f °C", data.temperatura)
        tvHumedadValue.text = String.format(Locale.getDefault(), "%.1f %%", data.humedad)
        tvCO2Value.text = String.format(Locale.getDefault(), "%d ppm", data.co2)
        tvTVOCValue.text = String.format(Locale.getDefault(), "%d mg", data.tvoc)

        // Registrar lectura en Base de Datos SQLite si es un nuevo timestamp
        val ts = if (data.bridge_ts > 0L) {
            if (data.bridge_ts < 100000000000L) data.bridge_ts * 1000 else data.bridge_ts
        } else {
            System.currentTimeMillis()
        }

        if (data.bridge_ts > 0L && data.bridge_ts != lastRecordedTs) {
            lastRecordedTs = data.bridge_ts
            dbHelper.insertTelemetry(data)
        }

        // Actualizar barras de indicación LED de temperatura
        val activeColor = Color.parseColor("#00D9FF")
        val inactiveColor = Color.parseColor("#1A00D9FF")
        tempBar1.setBackgroundColor(if (data.temperatura >= -10.0) activeColor else inactiveColor)
        tempBar2.setBackgroundColor(if (data.temperatura >= 10.0) activeColor else inactiveColor)
        tempBar3.setBackgroundColor(if (data.temperatura >= 18.0) activeColor else inactiveColor)
        tempBar4.setBackgroundColor(if (data.temperatura >= 24.0) activeColor else inactiveColor)
        tempBar5.setBackgroundColor(if (data.temperatura >= 30.0) activeColor else inactiveColor)

        // Actualizar textos de estado del Bento Grid de sensores
        // Temperatura
        if (data.temperatura > 26) {
            tvTempStatus.text = "Caluroso"
            tvTempStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
        } else if (data.temperatura < 18) {
            tvTempStatus.text = "Frío"
            tvTempStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
        } else {
            tvTempStatus.text = "Normal"
            tvTempStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
        }

        // Humedad
        if (data.humedad > 65) {
            tvHumedadStatus.text = "Húmedo"
            tvHumedadStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
        } else if (data.humedad < 35) {
            tvHumedadStatus.text = "Seco"
            tvHumedadStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
        } else {
            tvHumedadStatus.text = "Confort"
            tvHumedadStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
        }

        // TVOC
        if (data.tvoc > 300) {
            tvTVOCStatus.text = "Alto"
            tvTVOCStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            tvTVOCStatus.text = "Seguro"
            tvTVOCStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
        }

        // CO2 y Banner General Contextual
        if (data.co2 > 1800) {
            tvCO2Status.text = "Crítico"
            tvCO2Status.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            
            cardAmbientalStatus.setCardBackgroundColor(ContextCompat.getColorStateList(this, R.color.accent_red_trans))
            cardAmbientalStatus.setStrokeColor(ContextCompat.getColorStateList(this, R.color.accent_red))
            tvStatusTitle.text = "🔴 ALERTA CRÍTICA"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            tvStatusText.text = "Calidad del aire peligrosa. CO2 muy alto. Se aconseja ventilar e iniciar el extractor."
        } else if (data.co2 > 1200) {
            tvCO2Status.text = "Regular"
            tvCO2Status.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            
            cardAmbientalStatus.setCardBackgroundColor(ContextCompat.getColorStateList(this, R.color.accent_yellow_trans))
            cardAmbientalStatus.setStrokeColor(ContextCompat.getColorStateList(this, R.color.accent_yellow))
            tvStatusTitle.text = "🟡 CALIDAD REGULAR"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            tvStatusText.text = "Concentración moderada de CO2. Activa el extractor para renovar el aire."
        } else {
            tvCO2Status.text = "Excelente"
            tvCO2Status.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
            
            cardAmbientalStatus.setCardBackgroundColor(ContextCompat.getColorStateList(this, R.color.accent_mint_trans))
            cardAmbientalStatus.setStrokeColor(ContextCompat.getColorStateList(this, R.color.accent_mint))
            tvStatusTitle.text = "🟢 ESTADO EXCELENTE"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_mint))
            tvStatusText.text = "Todos los parámetros están en rango óptimo. Ambiente saludable."
        }

        // Last Update Time
        if (data.bridge_ts > 0L) {
            val timestampMs = if (data.bridge_ts < 100000000000L) data.bridge_ts * 1000 else data.bridge_ts
            val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
            tvLastUpdate.text = "Última lectura: $formattedDate"
        } else {
            tvLastUpdate.text = "Última lectura: No disponible"
        }
    }

    /**
     * Alterna la visibilidad de las vistas según la pestaña seleccionada
     */
    private fun selectTab(tabIndex: Int) {
        val selectedColor = Color.parseColor("#00D9FF")
        val unselectedColor = Color.parseColor("#7DB0D6")

        when (tabIndex) {
            0 -> {
                viewHome.visibility = View.VISIBLE
                viewCharts.visibility = View.GONE
                viewSettings.visibility = View.GONE

                ivNavHomeIcon.setColorFilter(selectedColor)
                ivNavChartsIcon.setColorFilter(unselectedColor)
                ivNavSettingsIcon.setColorFilter(unselectedColor)
                ivNavProfileIcon.setColorFilter(unselectedColor)
            }
            1 -> {
                viewHome.visibility = View.GONE
                viewCharts.visibility = View.VISIBLE
                viewSettings.visibility = View.GONE

                ivNavHomeIcon.setColorFilter(unselectedColor)
                ivNavChartsIcon.setColorFilter(selectedColor)
                ivNavSettingsIcon.setColorFilter(unselectedColor)
                ivNavProfileIcon.setColorFilter(unselectedColor)

                // Cargar registros locales y refrescar el gráfico
                val readings = dbHelper.getLastReadings(24)
                cyberChartsView.setData(readings)
            }
            2 -> {
                viewHome.visibility = View.GONE
                viewCharts.visibility = View.GONE
                viewSettings.visibility = View.VISIBLE

                ivNavHomeIcon.setColorFilter(unselectedColor)
                ivNavChartsIcon.setColorFilter(unselectedColor)
                ivNavSettingsIcon.setColorFilter(selectedColor)
                ivNavProfileIcon.setColorFilter(unselectedColor)
            }
        }
    }

    /**
     * Muestra el cuadro de diálogo de opciones de perfil (Cambiar cuenta / Cerrar sesión)
     */
    private fun showProfilePopup() {
        val options = arrayOf("Cambiar de cuenta", "Cerrar sesión", "Cancelar")
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("GESTIÓN DE CUENTA HUD")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> { // Cambiar de cuenta (hace signout y va a AuthActivity)
                    performLogout()
                }
                1 -> { // Cerrar sesión
                    performLogout()
                }
                2 -> { // Cancelar
                    dialog.dismiss()
                }
            }
        }
        builder.show()
    }

    private fun performLogout() {
        // Sign out Firebase Auth
        FirebaseAuth.getInstance().signOut()

        // Sign out Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    /**
     * Exporta el contenido de la base de datos a un archivo CSV y abre el Share Sheet para guardarlo/enviarlo
     */
    private fun exportDatabaseToCSV() {
        try {
            val csvContent = dbHelper.getTelemetryAsCSV()
            val cacheFile = File(cacheDir, "historial_telemetria.csv")
            cacheFile.writeText(csvContent)

            val fileUri = FileProvider.getUriForFile(
                this,
                "com.ktasoporte.estacionambiental.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Historial de Telemetria Ambiental")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Exportar Historial en CSV"))
            Toast.makeText(this, "CSV generado. Selecciona cómo guardarlo o enviarlo.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error al exportar CSV", e)
            Toast.makeText(this, "Error al generar CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensoresListener?.let { sensoresRef.removeEventListener(it) }
        controlListener?.let { controlRef.removeEventListener(it) }
        alarmaListener?.let { alarmaRef.removeEventListener(it) }
        iaListener?.let { iaRef.removeEventListener(it) }
        
        pomodoroTimer?.cancel()
        fanAnimator?.cancel()
        pomodoroOuterAnimator?.cancel()
        pomodoroInnerAnimator?.cancel()
        
        stopAlarmSound()
    }
}
