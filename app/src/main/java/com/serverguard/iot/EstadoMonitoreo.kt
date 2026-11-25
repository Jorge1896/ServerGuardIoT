package com.serverguard.iot

/**
 * Evaluación de lecturas mostradas en pantalla.
 * Una lectura guardada no implica que el ESP32 esté conectado.
 */
object EstadoMonitoreo {
    enum class Nivel { SIN_DATOS, NORMAL, ADVERTENCIA, CRITICO }

    data class Evaluacion(val nivel: Nivel, val mensaje: String)

    fun evaluar(
        temperatura: Double?,
        minima: Double?,
        maxima: Double?,
        rackAbierto: Boolean?,
        alarma: Boolean?,
        alertaDispositivo: Boolean?
    ): Evaluacion {
        if (temperatura == null && rackAbierto == null &&
            alarma == null && alertaDispositivo == null) {
            return Evaluacion(Nivel.SIN_DATOS, "Sin mediciones disponibles")
        }

        val razones = mutableListOf<String>()
        if (temperatura != null && minima != null && temperatura < minima) {
            razones.add("Temperatura bajo el mínimo")
        }
        if (temperatura != null && maxima != null && temperatura > maxima) {
            razones.add("Temperatura sobre el máximo")
        }
        if (rackAbierto == true) razones.add("Rack abierto")
        if (alarma == true) razones.add("Alarma activada")
        if (alertaDispositivo == true) razones.add("Alerta reportada por ESP32")

        return if (razones.isNotEmpty()) {
            Evaluacion(Nivel.CRITICO, razones.distinct().joinToString(" · "))
        } else if (temperatura == null || minima == null || maxima == null ||
            rackAbierto == null || alarma == null || alertaDispositivo == null) {
            Evaluacion(Nivel.ADVERTENCIA, "Lectura parcial: faltan datos para evaluar todo")
        } else {
            Evaluacion(Nivel.NORMAL, "Sin condiciones de alerta")
        }
    }

    fun heartbeatReciente(milisegundos: Long?, ahora: Long): Boolean =
        milisegundos != null && milisegundos > 0 &&
            milisegundos <= ahora + 60_000L &&
            ahora - milisegundos <= 15_000L
}
