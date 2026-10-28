
package com.serverguard.iot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class DashboardActivity : AppCompatActivity() {

    // ==========================================
    // FIREBASE
    // ==========================================

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var referenciaSala: DatabaseReference

    private var listenerSala: ValueEventListener? = null

    private val databaseUrl =
        "https://serverguardiot-default-rtdb.firebaseio.com/"

    // ==========================================
    // INTERFAZ
    // ==========================================

    private lateinit var txtEstadoGeneral: TextView
    private lateinit var txtTemperatura: TextView
    private lateinit var txtHumedad: TextView
    private lateinit var txtRack: TextView
    private lateinit var txtVentilador: TextView
    private lateinit var txtAlerta: TextView
    private lateinit var txtUltimaActualizacion: TextView

    private lateinit var btnSimular: Button
    private lateinit var btnRack: Button
    private lateinit var btnHistorial: Button
    private lateinit var btnCerrarSesion: Button

    // ==========================================
    // SENSORES SIMULADOS
    // ==========================================

    private var temperatura = 24.5
    private var humedad = 48
    private var rackAbierto = false

    private var datosCargados = false
    private var guardando = false

    private val temperaturaMaxima = 28.0

    // ==========================================
    // NOTIFICACIONES
    // ==========================================

    private val canalAlertas = "serverguard_alertas"

    private val idTemperatura = 1001
    private val idRack = 1002

    private var estadoAnteriorTemperatura: Boolean? = null
    private var estadoAnteriorRack: Boolean? = null

    private val solicitarNotificaciones =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permitido ->

            if (!permitido) {
                Toast.makeText(
                    this,
                    "Las notificaciones están desactivadas",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // ==========================================
    // INICIO
    // ==========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            volverAlLogin()
            return
        }

        database = FirebaseDatabase
            .getInstance(databaseUrl)
            .reference

        referenciaSala = database
            .child("salas")
            .child("sala_principal")

        txtEstadoGeneral = findViewById(R.id.txtEstadoGeneral)
        txtTemperatura = findViewById(R.id.txtTemperatura)
        txtHumedad = findViewById(R.id.txtHumedad)
        txtRack = findViewById(R.id.txtRack)
        txtVentilador = findViewById(R.id.txtVentilador)
        txtAlerta = findViewById(R.id.txtAlerta)

        txtUltimaActualizacion =
            findViewById(R.id.txtUltimaActualizacion)

        btnSimular = findViewById(R.id.btnSimular)
        btnRack = findViewById(R.id.btnRack)
        btnHistorial = findViewById(R.id.btnHistorial)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        crearCanalNotificaciones()
        solicitarPermisoNotificaciones()

        actualizarBotones()

        btnSimular.setOnClickListener {
            generarLectura()
        }

        btnRack.setOnClickListener {
            cambiarRack()
        }

        btnHistorial.setOnClickListener {
            startActivity(
                Intent(this, HistorialActivity::class.java)
            )
        }

        btnCerrarSesion.setOnClickListener {
            auth.signOut()
            volverAlLogin()
        }
    }

    // ==========================================
    // PERMISO DE NOTIFICACIONES
    // ==========================================

    private fun solicitarPermisoNotificaciones() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permitido = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permitido) {
                solicitarNotificaciones.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    // ==========================================
    // CANAL DE ALERTAS
    // ==========================================

    private fun crearCanalNotificaciones() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val canal = NotificationChannel(
                canalAlertas,
                "Alertas de ServerGuard",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Alertas de temperatura y seguridad del rack"

                enableVibration(true)
            }

            val administrador = getSystemService(
                NotificationManager::class.java
            )

            administrador.createNotificationChannel(canal)
        }
    }

    // ==========================================
    // MOSTRAR UNA NOTIFICACIÓN
    // ==========================================

    private fun mostrarNotificacion(
        id: Int,
        titulo: String,
        mensaje: String
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permitido = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permitido) return
        }

        val intent = Intent(
            this,
            DashboardActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(
            this,
            canalAlertas
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(mensaje)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(id, notificacion)

        } catch (error: SecurityException) {

            Toast.makeText(
                this,
                "No hay permiso para mostrar notificaciones",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ==========================================
    // DETECTAR ALERTAS NUEVAS
    // ==========================================

    private fun comprobarAlertasNuevas() {

        val temperaturaAlta =
            temperatura > temperaturaMaxima

        // Evitar notificaciones duplicadas al abrir el panel
        if (estadoAnteriorTemperatura == null) {
            estadoAnteriorTemperatura = temperaturaAlta

        } else {

            if (temperaturaAlta &&
                estadoAnteriorTemperatura == false
            ) {

                val mensaje = String.format(
                    Locale.getDefault(),
                    "Temperatura de %.1f °C. " +
                            "Ventilador activado automáticamente.",
                    temperatura
                )

                mostrarNotificacion(
                    idTemperatura,
                    "ServerGuard: temperatura elevada",
                    mensaje
                )
            }

            estadoAnteriorTemperatura = temperaturaAlta
        }

        // Detectar apertura del rack
        if (estadoAnteriorRack == null) {
            estadoAnteriorRack = rackAbierto

        } else {

            if (rackAbierto &&
                estadoAnteriorRack == false
            ) {

                mostrarNotificacion(
                    idRack,
                    "ServerGuard: alerta de seguridad",
                    "Se ha detectado la apertura del rack."
                )
            }

            estadoAnteriorRack = rackAbierto
        }
    }

    // ==========================================
    // ESCUCHAR FIREBASE
    // ==========================================

    override fun onStart() {
        super.onStart()

        if (::referenciaSala.isInitialized) {
            escucharFirebase()
        }
    }

    private fun escucharFirebase() {

        if (listenerSala != null) return

        listenerSala = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                if (!snapshot.exists()) {

                    datosCargados = true
                    actualizarBotones()

                    txtEstadoGeneral.text =
                        "ESTADO: SIN LECTURAS"

                    txtAlerta.text =
                        "Presiona GENERAR NUEVA LECTURA."

                    return
                }

                val temp = snapshot.child("temperatura")
                    .getValue(Double::class.java)

                val hum = snapshot.child("humedad")
                    .getValue(Int::class.java)

                val rack = snapshot.child("rackAbierto")
                    .getValue(Boolean::class.java)

                val fecha = snapshot.child("ultimaActualizacion")
                    .getValue(Long::class.java)

                if (temp == null ||
                    hum == null ||
                    rack == null
                ) {
                    txtAlerta.text =
                        "Error: mediciones incompletas."
                    return
                }

                temperatura = temp
                humedad = hum
                rackAbierto = rack

                datosCargados = true

                actualizarPantalla()
                comprobarAlertasNuevas()
                actualizarBotones()

                if (fecha != null) {

                    val formato = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm:ss",
                        Locale.getDefault()
                    )

                    txtUltimaActualizacion.text =
                        "Última actualización: " +
                                formato.format(Date(fecha))
                }
            }

            override fun onCancelled(error: DatabaseError) {

                datosCargados = false
                actualizarBotones()

                txtEstadoGeneral.text = "ESTADO: ERROR"

                txtEstadoGeneral.setTextColor(
                    Color.parseColor("#F87171")
                )

                txtAlerta.text =
                    "Firebase: ${error.message}"
            }
        }

        referenciaSala.addValueEventListener(listenerSala!!)
    }

    // ==========================================
    // GENERAR NUEVA LECTURA
    // ==========================================

    private fun generarLectura() {

        guardarMedicion(
            Random.nextDouble(20.0, 36.0),
            Random.nextInt(35, 76),
            rackAbierto
        )
    }

    // ==========================================
    // CAMBIAR ESTADO DEL RACK
    // ==========================================

    private fun cambiarRack() {

        guardarMedicion(
            temperatura,
            humedad,
            !rackAbierto
        )
    }

    // ==========================================
    // GUARDAR MEDICIÓN E HISTORIAL
    // ==========================================

    private fun guardarMedicion(
        nuevaTemperatura: Double,
        nuevaHumedad: Int,
        nuevoRack: Boolean
    ) {

        if (!datosCargados || guardando) return

        guardando = true
        actualizarBotones()

        val ventilador =
            nuevaTemperatura > temperaturaMaxima

        val registro = hashMapOf<String, Any>(
            "temperatura" to nuevaTemperatura,
            "humedad" to nuevaHumedad,
            "rackAbierto" to nuevoRack,
            "ventiladorEncendido" to ventilador,
            "ultimaActualizacion" to ServerValue.TIMESTAMP
        )

        val idRegistro = database
            .child("historial")
            .child("sala_principal")
            .push()
            .key

        if (idRegistro == null) {
            guardando = false
            actualizarBotones()
            mostrarMensaje("Error al generar el registro")
            return
        }

        val actualizaciones = mapOf<String, Any>(
            "salas/sala_principal" to registro,
            "historial/sala_principal/$idRegistro" to registro
        )

        database.updateChildren(actualizaciones)
            .addOnSuccessListener {

                mostrarMensaje(
                    "Medición e historial guardados"
                )

            }
            .addOnFailureListener { error ->

                mostrarMensaje(
                    "Error de Firebase: ${error.localizedMessage}"
                )

            }
            .addOnCompleteListener {

                guardando = false
                actualizarBotones()
            }
    }

    // ==========================================
    // ACTUALIZAR DASHBOARD
    // ==========================================

    private fun actualizarPantalla() {

        txtTemperatura.text = String.format(
            Locale.US,
            "%.1f °C",
            temperatura
        )

        txtHumedad.text = "$humedad %"

        val ventilador =
            temperatura > temperaturaMaxima

        txtVentilador.text =
            if (ventilador) "ENCENDIDO" else "APAGADO"

        txtVentilador.setTextColor(
            Color.parseColor(
                if (ventilador) "#38BDF8" else "#94A3B8"
            )
        )

        txtRack.text =
            if (rackAbierto) "ABIERTO" else "CERRADO"

        txtRack.setTextColor(
            Color.parseColor(
                if (rackAbierto) "#F87171" else "#4ADE80"
            )
        )

        val alertas = mutableListOf<String>()

        if (ventilador) {
            alertas.add(
                "ALERTA: Temperatura superior a 28 °C. " +
                        "Ventilador activado."
            )
        }

        if (rackAbierto) {
            alertas.add(
                "ALERTA DE SEGURIDAD: Rack abierto."
            )
        }

        if (alertas.isEmpty()) {

            txtEstadoGeneral.text = "ESTADO: NORMAL"

            txtEstadoGeneral.setTextColor(
                Color.parseColor("#4ADE80")
            )

            txtAlerta.text =
                "Sistema funcionando normalmente."

            txtAlerta.setTextColor(
                Color.parseColor("#CBD5E1")
            )

        } else {

            txtEstadoGeneral.text = "ESTADO: ALERTA"

            txtEstadoGeneral.setTextColor(
                Color.parseColor("#F87171")
            )

            txtAlerta.text =
                alertas.joinToString("\n\n")

            txtAlerta.setTextColor(
                Color.parseColor("#FCA5A5")
            )
        }
    }

    // ==========================================
    // ACTIVAR O DESACTIVAR BOTONES
    // ==========================================

    private fun actualizarBotones() {

        val habilitar = datosCargados && !guardando

        btnSimular.isEnabled = habilitar
        btnRack.isEnabled = habilitar
    }

    // ==========================================
    // VOLVER AL LOGIN
    // ==========================================

    private fun volverAlLogin() {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    // ==========================================
    // MENSAJES
    // ==========================================

    private fun mostrarMensaje(mensaje: String) {

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_SHORT
        ).show()
    }

    // ==========================================
    // LIBERAR FIREBASE
    // ==========================================

    override fun onStop() {
        super.onStop()

        if (::referenciaSala.isInitialized) {
            listenerSala?.let {
                referenciaSala.removeEventListener(it)
            }
        }

        listenerSala = null

        // Al reabrir, reconocer el estado inicial
        // sin repetir notificaciones antiguas.
        estadoAnteriorTemperatura = null
        estadoAnteriorRack = null
    }
}
