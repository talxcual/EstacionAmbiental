package com.ktasoporte.estacionambiental.models

data class PomodoroCommand(
    val comando: String = "STOP",
    val minutos: Int = 0
)
