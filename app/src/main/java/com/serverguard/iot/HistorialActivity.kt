package com.serverguard.iot

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historial heredado de Unidad 2. NO presenta la telemetría actual como
 * si fuese un registro histórico de Wokwi.
 */
class HistorialActivity : AppCompatActivity() {
    private val url = "https://serverguardiot-default-rtdb.firebaseio.com/"
    private lateinit var consulta: Query
    private lateinit var configuracion: DatabaseReference
    private lateinit var contenedor: LinearLayout
    private lateinit var txtEstado: TextView
    private var listener: ValueEventListener? = null
    private var listenerLimites: ValueEventListener? = null
    private var registros = listOf<DataSnapshot>()
    private var limiteMin = 18.0
    private var limiteMax = 28.0
    private var limitesLeidos = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_historial)
        findViewById<android.view.View>(R.id.historialRoot).let { panel ->
            ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(panel)
        }
        contenedor = findViewById(R.id.contenedorHistorial)
        txtEstado = findViewById(R.id.txtEstadoHistorial)
        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
        val db = FirebaseDatabase.getInstance(url)
        consulta = db.getReference("historial/sala_principal")
            .orderByKey().limitToLast(30)
        configuracion = db.getReference("serverguard/configuracion")
    }

    override fun onStart() {
        super.onStart()
        if (!::consulta.isInitialized) return
        listenerLimites = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                limiteMin = (snapshot.child("temperatura_minima").value as? Number)
                    ?.toDouble() ?: 18.0
                limiteMax = (snapshot.child("temperatura_maxima").value as? Number)
                    ?.toDouble() ?: 28.0
                limitesLeidos = true
                mostrar()
            }
            override fun onCancelled(error: DatabaseError) {
                limitesLeidos = false
                mostrar()
            }
        }
        configuracion.addValueEventListener(listenerLimites!!)
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                registros = snapshot.children.toList().asReversed()
                mostrar()
            }
            override fun onCancelled(error: DatabaseError) {
                txtEstado.text = "No fue posible leer el historial: ${error.message}"
                contenedor.removeAllViews()
            }
        }
        consulta.addValueEventListener(listener!!)
    }

    private fun mostrar() {
        if (!::contenedor.isInitialized) return
        contenedor.removeAllViews()
        txtEstado.text = if (registros.isEmpty())
            "Sin registros históricos de Unidad 2. Las lecturas actuales del ESP32 todavía no se guardan aquí."
        else "Historial anterior: ${registros.size} mediciones. " +
            if (limitesLeidos) "Alertas con límites actuales ($limiteMin–$limiteMax °C)."
            else "Límites no disponibles; se muestra solo la medición."

        registros.forEach { registro ->
            val temperatura = (registro.child("temperatura").value as? Number)?.toDouble()
                ?: return@forEach
            val humedad = (registro.child("humedad").value as? Number)?.toDouble()
            val rack = registro.child("rackAbierto").getValue(Boolean::class.java)
            val ventilador = registro.child("ventiladorEncendido").getValue(Boolean::class.java)
            val timestamp = (registro.child("ultimaActualizacion").value as? Number)?.toLong()
            val fecha = timestamp?.takeIf { it > 0L }?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
            } ?: "Fecha no disponible"
            val alerta = (limitesLeidos && (temperatura < limiteMin || temperatura > limiteMax)) ||
                rack == true

            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(Color.parseColor("#1E293B"))
            }
            val titulo = TextView(this).apply {
                text = fecha
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val lectura = TextView(this).apply {
                text = String.format(Locale.getDefault(),
                    "Temperatura: %.1f °C\nHumedad: %s\nRack: %s\nVentilador: %s",
                    temperatura,
                    humedad?.let { String.format(Locale.getDefault(), "%.1f %%", it) } ?: "Sin datos",
                    when (rack) { true -> "ABIERTO"; false -> "CERRADO"; null -> "SIN DATOS" },
                    when (ventilador) { true -> "ENCENDIDO"; false -> "APAGADO"; null -> "SIN DATOS" }
                )
                textSize = 15f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(10), 0, dp(10))
            }
            val estado = TextView(this).apply {
                text = when {
                    alerta -> "ALERTA según límites actuales / rack"
                    !limitesLeidos -> "Estado: sin evaluar temperatura"
                    else -> "Sin alerta según límites actuales"
                }
                setTextColor(Color.parseColor(if (alerta) "#FCA5A5" else "#4ADE80"))
            }
            tarjeta.addView(titulo)
            tarjeta.addView(lectura)
            tarjeta.addView(estado)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(12)
            contenedor.addView(tarjeta, params)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onStop() {
        super.onStop()
        listener?.let { consulta.removeEventListener(it) }
        listenerLimites?.let { configuracion.removeEventListener(it) }
        listener = null
        listenerLimites = null
    }
}
