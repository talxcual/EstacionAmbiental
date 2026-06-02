package com.ktasoporte.estacionambiental.models

data class TelemetryData(
    val temperatura: Double = 0.0,
    val humedad: Double = 0.0,
    val co2: Int = 0,
    val tvoc: Int = 0,
    val bridge_ts: Long = 0L
)
