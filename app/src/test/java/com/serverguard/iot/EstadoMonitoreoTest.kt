package com.serverguard.iot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoMonitoreoTest {
    @Test fun temperaturaAltaGeneraAlerta() {
        val evaluacion = EstadoMonitoreo.evaluar(30.0, 18.0, 28.0, false, false, false)
        assertEquals(EstadoMonitoreo.Nivel.CRITICO, evaluacion.nivel)
        assertTrue(evaluacion.mensaje.contains("máximo"))
    }

    @Test fun temperaturaBajaGeneraAlerta() {
        val evaluacion = EstadoMonitoreo.evaluar(15.0, 18.0, 28.0, false, false, false)
        assertEquals(EstadoMonitoreo.Nivel.CRITICO, evaluacion.nivel)
    }

    @Test fun rackAbiertoGeneraAlerta() {
        val evaluacion = EstadoMonitoreo.evaluar(24.0, 18.0, 28.0, true, false, false)
        assertEquals(EstadoMonitoreo.Nivel.CRITICO, evaluacion.nivel)
    }

    @Test fun lecturaNormal() {
        val evaluacion = EstadoMonitoreo.evaluar(24.0, 18.0, 28.0, false, false, false)
        assertEquals(EstadoMonitoreo.Nivel.NORMAL, evaluacion.nivel)
    }

    @Test fun noInventarAlertaConDatosFaltantes() {
        assertEquals(EstadoMonitoreo.Nivel.SIN_DATOS,
            EstadoMonitoreo.evaluar(null, null, null, null, null, null).nivel)
        assertEquals(EstadoMonitoreo.Nivel.ADVERTENCIA,
            EstadoMonitoreo.evaluar(24.0, null, null, false, false, false).nivel)
    }

    @Test fun heartbeatAntiguoNoEsConexionActiva() {
        assertTrue(EstadoMonitoreo.heartbeatReciente(98_000, 100_000))
        assertFalse(EstadoMonitoreo.heartbeatReciente(70_000, 100_000))
        assertFalse(EstadoMonitoreo.heartbeatReciente(null, 100_000))
    }
}
