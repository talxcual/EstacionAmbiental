package com.ktasoporte.estacionambiental.models

data class AlarmCommand(
    val hora: Int = 7,
    val minuto: Int = 0,
    val activa: Boolean = false
)
