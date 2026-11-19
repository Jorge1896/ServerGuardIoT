package com.serverguard.iot

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

class DashboardActivity : AppCompatActivity() {
    private val databaseUrl = "https://serverguardiot-default-rtdb.firebaseio.com/"
    private lateinit var auth: FirebaseAuth
    private lateinit var raiz: DatabaseReference
    private lateinit var telemetria: DatabaseReference
    private lateinit var configuracion: DatabaseReference
    private lateinit var comando: DatabaseReference
    private lateinit var conexionFirebase: DatabaseReference

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
    private lateinit var txtComando: TextView
    private lateinit var txtLimites: TextView
    private lateinit var editMin: EditText
    private lateinit var editMax: EditText
    private lateinit var btnGuardar: Button
    private val botonesComando = mutableListOf<Button>()
    private var conectadoFirebase = false
    private var envioEnCurso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            irAlLogin()
            return
        }
        setContentView(R.layout.activity_dashboard)
        raiz = FirebaseDatabase.getInstance(databaseUrl).reference
        telemetria = raiz.child("serverguard").child("telemetria")
        configuracion = raiz.child("serverguard").child("configuracion")
        comando = raiz.child("serverguard").child("comando")
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
        txtComando = findViewById(R.id.txtComando)
        txtLimites = findViewById(R.id.txtLimites)
        editMin = findViewById(R.id.editMin)
        editMax = findViewById(R.id.editMax)
        btnGuardar = findViewById(R.id.btnGuardar)

        asignarComando(R.id.btnAbrir, "DOOR_OPEN")
        asignarComando(R.id.btnCerrar, "DOOR_CLOSE")
        asignarComando(R.id.btnFanOn, "FAN_ON")
        asignarComando(R.id.btnFanOff, "FAN_OFF")
        asignarComando(R.id.btnLedOn, "LED_ON")
        asignarComando(R.id.btnLedOff, "LED_OFF")
        asignarComando(R.id.btnAlarmOn, "ALARM_ON")
        asignarComando(R.id.btnAlarmOff, "ALARM_OFF")
        actualizarBotones()

        btnGuardar.setOnClickListener { guardarLimites() }
        findViewById<Button>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            auth.signOut()
            irAlLogin()
        }
    }

    private fun asignarComando(id: Int, valor: String) {
        val boton = findViewById<Button>(id)
        botonesComando.add(boton)
        boton.setOnClickListener { enviarComando(valor) }
    }

    private fun actualizarBotones() {
        botonesComando.forEach { it.isEnabled = conectadoFirebase && !envioEnCurso }
        btnGuardar.isEnabled = conectadoFirebase && !envioEnCurso
    }

    // El firmware de Wokwi lee /serverguard/comando como un string.
    // La escritura NO prueba que el dispositivo haya ejecutado la orden.
    private fun enviarComando(valor: String) {
        if (!conectadoFirebase || envioEnCurso) return
        envioEnCurso = true
        actualizarBotones()
        txtComando.text = "Enviando: $valor"
        // Evitar sobrescribir un comando todavía pendiente.
        comando.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(data: com.google.firebase.database.MutableData):
                com.google.firebase.database.Transaction.Result {
                val actual = data.getValue(String::class.java)
                return if (actual == null || actual == "NONE") {
                    data.value = valor
                    com.google.firebase.database.Transaction.success(data)
                } else {
                    com.google.firebase.database.Transaction.abort()
                }
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                envioEnCurso = false
                actualizarBotones()
                txtComando.text = when {
                    error != null -> "Error al enviar: ${error.message}"
                    committed -> "Comando $valor enviado; ejecución NO confirmada"
                    else -> "Existe un comando pendiente; intenta después"
                }
            }
        })
    }

    private fun guardarLimites() {
        val minima = editMin.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val maxima = editMax.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (minima == null || maxima == null || minima < -40.0 ||
            maxima > 80.0 || minima >= maxima
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
        }.addOnCompleteListener { actualizarBotones() }
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
                actualizarBotones()
            }
            override fun onCancelled(error: DatabaseError) {
                conectadoFirebase = false
                txtFirebase.text = "Firebase: error de conexión"
                actualizarBotones()
            }
        }
        conexionFirebase.addValueEventListener(conexionListener!!)

        telemetriaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    txtEsp32.text = "ESP32: sin telemetría"
                    txtAlerta.text = "Esperando datos de Wokwi"
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
                // El ESP32 aún no manda heartbeat. No interpretar una etiqueta antigua
                // como prueba de que Wokwi sigue conectado.
                txtEsp32.text = "ESP32 (último estado reportado): ${declarado ?: "desconocido"} (sin verificación en vivo)"
                txtTemperatura.text = "Temperatura: " + (t?.let {
                    String.format(Locale.getDefault(), "%.1f °C", it) } ?: "--")
                txtHumedad.text = "Humedad: " + (h?.let {
                    String.format(Locale.getDefault(), "%.1f %%", it) } ?: "--")
                txtRack.text = "Rack: " + estado(rack, "ABIERTO", "CERRADO")
                txtVentilador.text = "Ventilador: " + estado(fan, "ENCENDIDO", "APAGADO")
                txtOtros.text = "LED: " + estado(led, "ON", "OFF") +
                    " | Alarma: " + estado(alarma, "ACTIVA", "INACTIVA")
                txtAlerta.text = if (alerta == true) "ALERTA REPORTADA POR ESP32"
                    else "Sin alerta reportada"
                txtAlerta.setTextColor(Color.parseColor(
                    if (alerta == true) "#F87171" else "#4ADE80"))
            }
            override fun onCancelled(error: DatabaseError) {
                txtEsp32.text = "ESP32: acceso a telemetría denegado"
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
                txtLimites.text = "Límites guardados: $min °C / $max °C"
                // No sobrescribir la edición del usuario mientras escribe.
                if (!editMin.hasFocus()) editMin.setText(min.toString())
                if (!editMax.hasFocus()) editMax.setText(max.toString())
            }
            override fun onCancelled(error: DatabaseError) {
                txtLimites.text = "Error al consultar límites: ${error.message}"
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
