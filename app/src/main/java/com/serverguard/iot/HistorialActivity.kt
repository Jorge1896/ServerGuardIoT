
package com.serverguard.iot

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

class HistorialActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var referenciaHistorial: DatabaseReference
    private lateinit var consultaHistorial: Query

    private lateinit var contenedorHistorial: LinearLayout
    private lateinit var txtEstadoHistorial: TextView
    private lateinit var btnVolver: Button

    private var listenerHistorial: ValueEventListener? = null

    private val databaseUrl =
        "https://serverguardiot-default-rtdb.firebaseio.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            finish()
            return
        }

        contenedorHistorial =
            findViewById(R.id.contenedorHistorial)

        txtEstadoHistorial =
            findViewById(R.id.txtEstadoHistorial)

        btnVolver = findViewById(R.id.btnVolver)

        referenciaHistorial = FirebaseDatabase
            .getInstance(databaseUrl)
            .reference
            .child("historial")
            .child("sala_principal")

        // Consultar las últimas 30 lecturas
        consultaHistorial = referenciaHistorial
            .orderByKey()
            .limitToLast(30)

        btnVolver.setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        if (::consultaHistorial.isInitialized) {
            escucharHistorial()
        }
    }

    private fun escucharHistorial() {

        if (listenerHistorial != null) return

        listenerHistorial = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                contenedorHistorial.removeAllViews()

                if (!snapshot.exists()) {
                    txtEstadoHistorial.text =
                        "Todavía no hay mediciones guardadas."
                    return
                }

                val registros = snapshot.children.toList()
                    .asReversed()

                txtEstadoHistorial.text =
                    "Mostrando ${registros.size} mediciones recientes"

                for (registro in registros) {
                    agregarTarjeta(registro)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                txtEstadoHistorial.text =
                    "Error al consultar Firebase: ${error.message}"
            }
        }

        consultaHistorial.addValueEventListener(
            listenerHistorial!!
        )
    }

    private fun agregarTarjeta(registro: DataSnapshot) {

        val temperatura = registro.child("temperatura")
            .getValue(Double::class.java) ?: return

        val humedad = registro.child("humedad")
            .getValue(Int::class.java) ?: return

        val rackAbierto = registro.child("rackAbierto")
            .getValue(Boolean::class.java) ?: false

        val ventilador = registro.child("ventiladorEncendido")
            .getValue(Boolean::class.java) ?: false

        val timestamp = registro.child("ultimaActualizacion")
            .getValue(Long::class.java) ?: 0L

        val fecha = if (timestamp > 0L) {
            SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(Date(timestamp))
        } else {
            "Fecha no disponible"
        }

        val alerta = temperatura > 28.0 || rackAbierto

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

        val mediciones = TextView(this).apply {
            text = String.format(
                Locale.getDefault(),
                "Temperatura: %.1f °C\nHumedad: %d %%",
                temperatura,
                humedad
            )
            textSize = 15f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(10), 0, dp(10))
        }

        val estado = TextView(this).apply {
            text = "Rack: ${if (rackAbierto) "ABIERTO" else "CERRADO"}" +
                    "\nVentilador: ${if (ventilador) "ENCENDIDO" else "APAGADO"}" +
                    "\nEstado: ${if (alerta) "ALERTA" else "NORMAL"}"

            textSize = 14f
            setTextColor(
                Color.parseColor(
                    if (alerta) "#FCA5A5" else "#4ADE80"
                )
            )
        }

        tarjeta.addView(titulo)
        tarjeta.addView(mediciones)
        tarjeta.addView(estado)

        val parametros = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        parametros.bottomMargin = dp(12)

        contenedorHistorial.addView(tarjeta, parametros)
    }

    private fun dp(valor: Int): Int {
        return (
                valor * resources.displayMetrics.density
                ).toInt()
    }

    override fun onStop() {
        super.onStop()

        if (::consultaHistorial.isInitialized) {
            listenerHistorial?.let {
                consultaHistorial.removeEventListener(it)
            }
        }

        listenerHistorial = null
    }
}
