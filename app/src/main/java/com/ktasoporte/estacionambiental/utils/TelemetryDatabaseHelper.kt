package com.ktasoporte.estacionambiental.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.ktasoporte.estacionambiental.models.TelemetryData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

class TelemetryDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "telemetry_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_TELEMETRY = "telemetry"

        private const val KEY_ID = "id"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_HUMIDITY = "humidity"
        private const val KEY_CO2 = "co2"
        private const val KEY_TVOC = "tvoc"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE " + TABLE_TELEMETRY + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_TIMESTAMP + " INTEGER,"
                + KEY_TEMPERATURE + " REAL,"
                + KEY_HUMIDITY + " REAL,"
                + KEY_CO2 + " INTEGER,"
                + KEY_TVOC + " INTEGER" + ")")
        db.execSQL(createTableQuery)
        Log.d("TelemetryDB", "Base de datos creada. Iniciando precarga de datos mock de 24 horas.")
        prepopulateMockData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TELEMETRY")
        onCreate(db)
    }

    /**
     * Inserta una nueva lectura en la base de datos local
     */
    fun insertTelemetry(data: TelemetryData) {
        // Obtenemos el timestamp actual o el provisto por la telemetría
        val timestamp = if (data.bridge_ts > 0L) {
            if (data.bridge_ts < 100000000000L) data.bridge_ts * 1000 else data.bridge_ts
        } else {
            System.currentTimeMillis()
        }

        val db = this.writableDatabase
        
        // Evitamos duplicados en el mismo segundo (o minuto) si es necesario, 
        // pero un insert directo suele bastar para registrar cada actualización.
        val values = ContentValues().apply {
            put(KEY_TIMESTAMP, timestamp)
            put(KEY_TEMPERATURE, data.temperatura)
            put(KEY_HUMIDITY, data.humedad)
            put(KEY_CO2, data.co2)
            put(KEY_TVOC, data.tvoc)
        }

        db.insert(TABLE_TELEMETRY, null, values)
        db.close()
        Log.d("TelemetryDB", "Lectura registrada: Temp=${data.temperatura}, Hum=${data.humedad}, CO2=${data.co2}")
    }

    /**
     * Obtiene los últimos registros para dibujar el gráfico
     */
    fun getLastReadings(limit: Int): List<TelemetryRecord> {
        val list = mutableListOf<TelemetryRecord>()
        val query = "SELECT * FROM $TABLE_TELEMETRY ORDER BY $KEY_TIMESTAMP DESC LIMIT $limit"
        val db = this.readableDatabase
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val record = TelemetryRecord(
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP)),
                    temperature = cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_TEMPERATURE)),
                    humidity = cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_HUMIDITY)),
                    co2 = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CO2)),
                    tvoc = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TVOC))
                )
                list.add(record)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        
        // Retornamos la lista ordenada cronológicamente (de más antiguo a más reciente)
        return list.reversed()
    }

    /**
     * Genera un archivo CSV con todo el historial de datos
     */
    fun getTelemetryAsCSV(): String {
        val csv = StringBuilder()
        csv.append("Timestamp,Fecha,Hora,Temperatura(C),Humedad(%),CO2(ppm),TVOC(mg)\n")

        val query = "SELECT * FROM $TABLE_TELEMETRY ORDER BY $KEY_TIMESTAMP ASC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(query, null)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        if (cursor.moveToFirst()) {
            do {
                val ts = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP))
                val temp = cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_TEMPERATURE))
                val hum = cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_HUMIDITY))
                val co2 = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CO2))
                val tvoc = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TVOC))

                val date = Date(ts)
                val dateStr = dateFormat.format(date)
                val timeStr = timeFormat.format(date)

                csv.append(String.format(Locale.US, "%d,%s,%s,%.1f,%.1f,%d,%d\n", ts, dateStr, timeStr, temp, hum, co2, tvoc))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return csv.toString()
    }

    /**
     * Precarga datos de las últimas 24 horas usando una onda senoidal
     * combinada con ruido aleatorio para obtener curvas de aspecto natural.
     */
    private fun prepopulateMockData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val oneHourMs = 3600 * 1000L

        // Creamos 24 horas de registros (desde hace 24 horas hasta hace 1 hora)
        for (i in 24 downTo 1) {
            val ts = now - (i * oneHourMs)
            
            // Usamos el índice de la hora para simular los ciclos térmicos/de CO2 diarios
            val hourOfDay = ((ts / oneHourMs) % 24).toDouble()
            
            // Simulación senoidal del ciclo de temperatura (más frío a las 5am, más cálido a las 3pm)
            val radTemp = Math.toRadians((hourOfDay - 9.0) * 15.0)
            val tempBase = 20.0 + 4.0 * sin(radTemp)
            val temp = Math.round((tempBase + (Math.random() - 0.5) * 1.5) * 10) / 10.0

            // Humedad inversa a la temperatura (más húmedo por la noche/madrugada)
            val radHum = Math.toRadians((hourOfDay - 21.0) * 15.0)
            val humBase = 55.0 + 15.0 * sin(radHum)
            val hum = Math.round((humBase + (Math.random() - 0.5) * 3.0) * 10) / 10.0

            // CO2 simulando ocupación (picos a mediodía y noche, bajo de madrugada)
            val radCo2 = Math.toRadians((hourOfDay - 14.0) * 15.0)
            val co2Base = 800.0 + 400.0 * sin(radCo2 * 2.0) // Frecuencia duplicada para simular dos picos de actividad
            val co2 = (co2Base + (Math.random() - 0.5) * 150.0).toInt().coerceIn(400, 1900)

            // TVOC siguiendo de cerca el ciclo de CO2 (acumulación en espacios cerrados)
            val tvocBase = 80.0 + 50.0 * sin(radCo2 * 2.0)
            val tvoc = (tvocBase + (Math.random() - 0.5) * 30.0).toInt().coerceIn(0, 350)

            val values = ContentValues().apply {
                put(KEY_TIMESTAMP, ts)
                put(KEY_TEMPERATURE, temp)
                put(KEY_HUMIDITY, hum)
                put(KEY_CO2, co2)
                put(KEY_TVOC, tvoc)
            }
            db.insert(TABLE_TELEMETRY, null, values)
        }
    }
}

data class TelemetryRecord(
    val timestamp: Long,
    val temperature: Double,
    val humidity: Double,
    val co2: Int,
    val tvoc: Int
)
