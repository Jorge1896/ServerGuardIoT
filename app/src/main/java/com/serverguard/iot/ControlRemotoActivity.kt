package com.serverguard.iot

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class ControlRemotoActivity : AppCompatActivity() {
    private val url = "https://serverguardiot-default-rtdb.firebaseio.com/"
    private lateinit var comando: DatabaseReference
    private lateinit var confirmacion: DatabaseReference
    private lateinit var conexion: DatabaseReference
    private val botones = mutableListOf<Button>()
    private lateinit var txtEstado: TextView
    private lateinit var txtAviso: TextView
    private lateinit var txtConfirmacion: TextView
    private lateinit var txtConexion: TextView
    private var listenerComando: ValueEventListener? = null
    private var listenerConfirmacion: ValueEventListener? = null
    private var listenerConexion: ValueEventListener? = null
    private var conectado = false
    private var comandoPendiente = true
    private var enviando = false
    private var recibidoPrimerEstado = false
    private var pendienteDesde = 0L
    private var valorPendiente: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val verificarEspera = object : Runnable {
        override fun run() {
            actualizarAviso()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_control_remoto)
        val pantalla = findViewById<android.view.View>(R.id.controlRoot)
        ViewCompat.setOnApplyWindowInsetsListener(pantalla) { vista, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(b.left, b.top + (12 * resources.displayMetrics.density).toInt(),
                b.right, b.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(pantalla)

        val db = FirebaseDatabase.getInstance(url)
        comando = db.getReference("serverguard/comando")
        confirmacion = db.getReference("serverguard/confirmacion")
        conexion = db.getReference(".info/connected")
        txtEstado = findViewById(R.id.txtEstadoControl)
        txtAviso = findViewById(R.id.txtAvisoControl)
        txtConfirmacion = findViewById(R.id.txtConfirmacionControl)
        txtConexion = findViewById(R.id.txtConexionControl)

        enlazar(R.id.btnAbrir, "DOOR_OPEN")
        enlazar(R.id.btnCerrar, "DOOR_CLOSE")
        enlazar(R.id.btnFanOn, "FAN_ON")
        enlazar(R.id.btnFanOff, "FAN_OFF")
        enlazar(R.id.btnLedOn, "LED_ON")
        enlazar(R.id.btnLedOff, "LED_OFF")
        enlazar(R.id.btnAlarmOn, "ALARM_ON")
        enlazar(R.id.btnAlarmOff, "ALARM_OFF")
        findViewById<Button>(R.id.btnVolverDashboard).setOnClickListener { finish() }
        actualizarBotones()
    }

    private fun enlazar(id: Int, valor: String) {
        val boton = findViewById<Button>(id)
        botones.add(boton)
        boton.setOnClickListener { enviar(valor) }
    }

    private fun actualizarBotones() {
        val habilitado = conectado && recibidoPrimerEstado && !comandoPendiente && !enviando
        botones.forEach {
            it.isEnabled = habilitado
            // Evita que el tema vuelva ilegible el texto de los botones deshabilitados.
            it.alpha = if (habilitado) 1f else 0.72f
            it.setTextColor(Color.WHITE)
        }
    }

    private fun actualizarAviso() {
        txtAviso.text = when {
            !conectado -> "Sin conexión con Firebase. No se pueden enviar órdenes."
            !recibidoPrimerEstado -> "Consultando el comando actual..."
            enviando -> "Guardando orden en Firebase..."
            comandoPendiente -> {
                val transcurrido = if (pendienteDesde > 0L)
                    (System.currentTimeMillis() - pendienteDesde) / 1000L else 0L
                if (transcurrido >= 30L)
                    "Sin respuesta desde hace al menos 30 s. Comprueba Wokwi antes de reintentar."
                else "Esperando a que Wokwi procese la orden. No se enviarán otras órdenes."
            }
            else -> "Listo para enviar órdenes. La ejecución requiere confirmación del ESP32."
        }
        txtAviso.setTextColor(
            Color.parseColor(if (comandoPendiente || !conectado) "#FBBF24" else "#CBD5E1")
        )
    }

    private fun enviar(valor: String) {
        if (!conectado || comandoPendiente || enviando || !recibidoPrimerEstado) return
        enviando = true
        actualizarBotones()
        actualizarAviso()
        comando.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val actual = data.getValue(String::class.java)
                return if (actual == null || actual == "NONE") {
                    data.value = valor
                    Transaction.success(data)
                } else Transaction.abort()
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                enviando = false
                txtEstado.text = when {
                    error != null -> "No se envió la orden: ${error.message}"
                    committed -> "Orden enviada: $valor (ejecución NO confirmada)"
                    else -> "Ya existe una orden pendiente."
                }
                actualizarBotones()
                actualizarAviso()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!::comando.isInitialized) return
        recibidoPrimerEstado = false
        comandoPendiente = true
        actualizarBotones()
        listenerConexion = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                conectado = snapshot.getValue(Boolean::class.java) == true
                txtConexion.text = if (conectado) "Firebase: conectado" else "Firebase: desconectado"
                txtConexion.setTextColor(Color.parseColor(if (conectado) "#4ADE80" else "#F87171"))
                actualizarBotones()
                actualizarAviso()
            }
            override fun onCancelled(error: DatabaseError) {
                conectado = false
                txtConexion.text = "Error de conexión: ${error.message}"
                actualizarBotones()
                actualizarAviso()
            }
        }
        conexion.addValueEventListener(listenerConexion!!)

        listenerComando = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val actual = snapshot.getValue(String::class.java)
                recibidoPrimerEstado = true
                comandoPendiente = actual != null && actual != "NONE"
                if (comandoPendiente) {
                    if (valorPendiente != actual) pendienteDesde = System.currentTimeMillis()
                    valorPendiente = actual
                    txtEstado.text = "Comando pendiente: ${actual ?: "desconocido"}"
                } else {
                    valorPendiente = null
                    pendienteDesde = 0L
                    txtEstado.text = "Comando: ninguno pendiente"
                }
                actualizarBotones()
                actualizarAviso()
            }
            override fun onCancelled(error: DatabaseError) {
                recibidoPrimerEstado = false
                comandoPendiente = true
                txtEstado.text = "No se pudo leer el comando: ${error.message}"
                actualizarBotones()
                actualizarAviso()
            }
        }
        comando.addValueEventListener(listenerComando!!)

        listenerConfirmacion = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val c = snapshot.child("comando").getValue(String::class.java)
                val r = snapshot.child("resultado").getValue(String::class.java)
                txtConfirmacion.text = if (c != null && r != null)
                    "Última confirmación publicada: $c / $r"
                else "Confirmación: todavía no disponible"
            }
            override fun onCancelled(error: DatabaseError) {
                txtConfirmacion.text = "Error al leer confirmación: ${error.message}"
            }
        }
        confirmacion.addValueEventListener(listenerConfirmacion!!)
        handler.removeCallbacks(verificarEspera)
        handler.post(verificarEspera)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(verificarEspera)
        if (::comando.isInitialized) {
            listenerComando?.let { comando.removeEventListener(it) }
            listenerConfirmacion?.let { confirmacion.removeEventListener(it) }
            listenerConexion?.let { conexion.removeEventListener(it) }
        }
        listenerComando = null
        listenerConfirmacion = null
        listenerConexion = null
    }
}
