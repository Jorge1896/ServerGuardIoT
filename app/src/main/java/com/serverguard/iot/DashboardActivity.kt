package com.serverguard.iot

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class DashboardActivity : AppCompatActivity() {
    private val databaseUrl = "https://serverguardiot-default-rtdb.firebaseio.com/"
    private lateinit var auth: FirebaseAuth
    private lateinit var raiz: DatabaseReference
    private lateinit var telemetria: DatabaseReference
    private lateinit var configuracion: DatabaseReference
    private lateinit var conexionFirebase: DatabaseReference
    private var ultimaConexionEsp32: Long? = null
    private var declaradoEsp32: String? = null
    private var temperaturaActual: Double? = null
    private var rackActual: Boolean? = null
    private var alarmaActual: Boolean? = null
    private var alertaActual: Boolean? = null
    private var limiteMinimo: Double? = null
    private var limiteMaximo: Double? = null
    private var hayTelemetria = false
    private lateinit var txtFechaMedicion: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refrescar = object : Runnable {
        override fun run() {
            estadoEsp32()
            actualizarAlerta()
            handler.postDelayed(this, 5000L)
        }
    }

    private var telemetriaListener: ValueEventListener? = null
    private var configuracionListener: ValueEventListener? = null
    private var conexionListener: ValueEventListener? = null

    private lateinit var txtFirebase: TextView
    private lateinit var txtEsp32: TextView
    private lateinit var txtTemperatura: TextView
    private lateinit var txtHumedad: TextView
    private lateinit var txtRack: TextView
    private lateinit var txtVentilador: TextView
    private lateinit var txtOtros: TextView
    private lateinit var txtAlerta: TextView
    private lateinit var txtLimites: TextView
    private lateinit var editMin: EditText
    private lateinit var editMax: EditText
    private lateinit var btnGuardar: Button
    private var conectadoFirebase = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            irAlLogin()
            return
        }
        setContentView(R.layout.activity_dashboard)
        val panel = findViewById<android.view.View>(R.id.dashboardRoot)
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(b.left, b.top + 12, b.right, b.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(panel)
        raiz = FirebaseDatabase.getInstance(databaseUrl).reference
        telemetria = raiz.child("serverguard").child("telemetria")
        configuracion = raiz.child("serverguard").child("configuracion")
        conexionFirebase = FirebaseDatabase.getInstance(databaseUrl)
            .getReference(".info/connected")

        txtFirebase = findViewById(R.id.txtFirebase)
        txtEsp32 = findViewById(R.id.txtEsp32)
        txtTemperatura = findViewById(R.id.txtTemperatura)
        txtHumedad = findViewById(R.id.txtHumedad)
        txtRack = findViewById(R.id.txtRack)
        txtVentilador = findViewById(R.id.txtVentilador)
        txtOtros = findViewById(R.id.txtOtros)
        txtAlerta = findViewById(R.id.txtAlerta)
        txtFechaMedicion = findViewById(R.id.txtFechaMedicion)
        txtLimites = findViewById(R.id.txtLimites)
        editMin = findViewById(R.id.editMin)
        editMax = findViewById(R.id.editMax)
        btnGuardar = findViewById(R.id.btnGuardar)

        btnGuardar.setOnClickListener { guardarLimites() }
        findViewById<Button>(R.id.btnControlRemoto).setOnClickListener {
            startActivity(Intent(this, ControlRemotoActivity::class.java))
        }
        findViewById<Button>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            auth.signOut()
            irAlLogin()
        }
    }

    private fun estadoEsp32() {
        val t = ultimaConexionEsp32
        val reciente = EstadoMonitoreo.heartbeatReciente(t, System.currentTimeMillis())
        txtEsp32.text = when {
            reciente -> "ESP32: activo (heartbeat reciente)"
            t != null -> "ESP32: sin comunicación reciente"
            else -> "ESP32: ${declaradoEsp32 ?: "sin verificar"} (sin heartbeat)"
        }
        txtEsp32.setTextColor(Color.parseColor(if (reciente) "#4ADE80" else "#FBBF24"))
    }

    private fun actualizarAlerta() {
        if (!hayTelemetria) {
            txtAlerta.text = "Esperando telemetría del ESP32"
            txtAlerta.setTextColor(Color.parseColor("#FBBF24"))
            return
        }
        val evaluacion = EstadoMonitoreo.evaluar(
            temperaturaActual, limiteMinimo, limiteMaximo,
            rackActual, alarmaActual, alertaActual
        )
        val reciente = EstadoMonitoreo.heartbeatReciente(
            ultimaConexionEsp32, System.currentTimeMillis()
        )
        val prefijo = if (reciente) "" else "Datos sin conexión ESP32 verificada. "
        txtAlerta.text = prefijo + evaluacion.mensaje
        val color = when (evaluacion.nivel) {
            EstadoMonitoreo.Nivel.CRITICO -> "#F87171"
            EstadoMonitoreo.Nivel.NORMAL -> if (reciente) "#4ADE80" else "#FBBF24"
            else -> "#FBBF24"
        }
        txtAlerta.setTextColor(Color.parseColor(color))
    }

    private fun guardarLimites() {
        val minima = editMin.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val maxima = editMax.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (minima == null || maxima == null || minima < -40.0 ||
            minima > 80.0 || maxima < -40.0 || maxima > 80.0 || minima >= maxima
        ) {
            Toast.makeText(this, "Usa límites válidos (-40 a 80 °C) y mínima menor a máxima",
                Toast.LENGTH_LONG).show()
            return
        }
        btnGuardar.isEnabled = false
        configuracion.updateChildren(
            mapOf("temperatura_minima" to minima, "temperatura_maxima" to maxima)
        ).addOnSuccessListener {
            Toast.makeText(this, "Límites guardados; ESP32 debe leerlos", Toast.LENGTH_LONG).show()
        }.addOnFailureListener { error ->
            Toast.makeText(this, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }.addOnCompleteListener { btnGuardar.isEnabled = conectadoFirebase }
    }

    override fun onStart() {
        super.onStart()
        if (!::telemetria.isInitialized) return
        conexionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                conectadoFirebase = snapshot.getValue(Boolean::class.java) == true
                txtFirebase.text = if (conectadoFirebase) "Firebase: conectado"
                                   else "Firebase: desconectado"
                txtFirebase.setTextColor(Color.parseColor(
                    if (conectadoFirebase) "#4ADE80" else "#F87171"))
                btnGuardar.isEnabled = conectadoFirebase
            }
            override fun onCancelled(error: DatabaseError) {
                conectadoFirebase = false
                txtFirebase.text = "Firebase: error de conexión"
                btnGuardar.isEnabled = conectadoFirebase
            }
        }
        conexionFirebase.addValueEventListener(conexionListener!!)
        handler.removeCallbacks(refrescar)
        handler.post(refrescar)

        telemetriaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    hayTelemetria = false
                    txtEsp32.text = "ESP32: sin telemetría"
                    txtFechaMedicion.text = "Última lectura: sin datos"
                    actualizarAlerta()
                    return
                }
                val t = snapshot.child("temperatura").value.let { it as? Number }?.toDouble()
                val h = snapshot.child("humedad").value.let { it as? Number }?.toDouble()
                val rack = snapshot.child("rack_abierto").getValue(Boolean::class.java)
                val fan = snapshot.child("ventilador").getValue(Boolean::class.java)
                val led = snapshot.child("led").getValue(Boolean::class.java)
                val alarma = snapshot.child("alarma").getValue(Boolean::class.java)
                val alerta = snapshot.child("alerta_activa").getValue(Boolean::class.java)
                val declarado = snapshot.child("estado_conexion").getValue(String::class.java)
                hayTelemetria = true
                temperaturaActual = t
                rackActual = rack
                alarmaActual = alarma
                alertaActual = alerta
                // El ESP32 aún no manda heartbeat. No interpretar una etiqueta antigua
                // como prueba de que Wokwi sigue conectado.
                declaradoEsp32 = declarado
                ultimaConexionEsp32 = (snapshot.child("ultima_conexion").value as? Number)?.toLong()
                estadoEsp32()
                txtFechaMedicion.text = ultimaConexionEsp32?.let { marca ->
                    "Última telemetría declarada: " +
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            .format(Date(marca))
                } ?: "Última telemetría: sin marca de tiempo del dispositivo"
                txtTemperatura.text = "Temperatura: " + (t?.let {
                    String.format(Locale.getDefault(), "%.1f °C", it) } ?: "--")
                txtHumedad.text = "Humedad: " + (h?.let {
                    String.format(Locale.getDefault(), "%.1f %%", it) } ?: "--")
                txtRack.text = "Rack: " + estado(rack, "ABIERTO", "CERRADO")
                txtVentilador.text = "Ventilador: " + estado(fan, "ENCENDIDO", "APAGADO")
                txtOtros.text = "LED: " + estado(led, "ON", "OFF") +
                    " | Alarma: " + estado(alarma, "ACTIVA", "INACTIVA")
                actualizarAlerta()
            }
            override fun onCancelled(error: DatabaseError) {
                hayTelemetria = false
                txtEsp32.text = "ESP32: no se pudo leer telemetría"
                txtAlerta.text = "Firebase: ${error.message}"
            }
        }
        telemetria.addValueEventListener(telemetriaListener!!)

        configuracionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val min = snapshot.child("temperatura_minima")
                    .value.let { it as? Number }?.toDouble() ?: 18.0
                val max = snapshot.child("temperatura_maxima")
                    .value.let { it as? Number }?.toDouble() ?: 28.0
                limiteMinimo = min
                limiteMaximo = max
                txtLimites.text = "Límites guardados: $min °C / $max °C"
                actualizarAlerta()
                // No sobrescribir la edición del usuario mientras escribe.
                if (!editMin.hasFocus()) editMin.setText(min.toString())
                if (!editMax.hasFocus()) editMax.setText(max.toString())
            }
            override fun onCancelled(error: DatabaseError) {
                limiteMinimo = null
                limiteMaximo = null
                txtLimites.text = "Error al consultar límites: ${error.message}"
                actualizarAlerta()
            }
        }
        configuracion.addValueEventListener(configuracionListener!!)
    }

    private fun estado(valor: Boolean?, positivo: String, negativo: String): String =
        when (valor) { true -> positivo; false -> negativo; null -> "SIN DATOS" }

    private fun irAlLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(refrescar)
        if (::conexionFirebase.isInitialized) {
            conexionListener?.let { conexionFirebase.removeEventListener(it) }
            telemetriaListener?.let { telemetria.removeEventListener(it) }
            configuracionListener?.let { configuracion.removeEventListener(it) }
        }
        conexionListener = null
        telemetriaListener = null
        configuracionListener = null
    }
}
