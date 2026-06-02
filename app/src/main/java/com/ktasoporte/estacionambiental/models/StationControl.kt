package com.ktasoporte.estacionambiental.models

data class StationControl(
    val alarmaActiva: Boolean = false,
    val configuracionPomodoro: Int = 0,
    val extractorAutomatico: Boolean = false,
    val extractorManual: Boolean = false
)
