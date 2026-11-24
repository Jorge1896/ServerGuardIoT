package com.serverguard.iot

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Cliente preparado para el protocolo v2 de comandos con IDs únicos.
 *
 * No está conectado aún a ControlRemotoActivity: el protocolo v1 permanece activo
 * hasta que el equipo habilite las reglas Firebase y el firmware ESP32 v2.
 *
 * Contrato completo: docs/PROTOCOLO_V2.md
 */
class ComandosV2Repository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://serverguardiot-default-rtdb.firebaseio.com/"),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    data class Respuesta(
        val id: String,
        val comando: String,
        val resultado: String
    )

    private val solicitudes: DatabaseReference =
        db.getReference("serverguard/v2/solicitudes")
    private val respuestas: DatabaseReference =
        db.getReference("serverguard/v2/respuestas")

    private val permitidos = setOf(
        "DOOR_OPEN", "DOOR_CLOSE", "FAN_ON", "FAN_OFF",
        "LED_ON", "LED_OFF", "ALARM_ON", "ALARM_OFF"
    )

    /**
     * Crea una solicitud inmutable en /solicitudes/{id}.
     * [onResult] retorna el ID publicado o un error. La publicación no
     * significa que el ESP32 haya ejecutado el comando.
     */
    fun enviar(
        comando: String,
        onResult: (id: String?, error: String?) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(null, "Debes iniciar sesión")
            return
        }
        if (comando !in permitidos) {
            onResult(null, "Comando no permitido")
            return
        }
        val solicitud = solicitudes.push()
        val id = solicitud.key
        if (id == null) {
            onResult(null, "No se pudo generar el identificador")
            return
        }
        val datos = mapOf(
            "comando" to comando,
            "creado_por" to uid,
            "creado_en" to ServerValue.TIMESTAMP
        )
        solicitud.setValue(datos).addOnSuccessListener {
            onResult(id, null)
        }.addOnFailureListener { e ->
            onResult(null, e.localizedMessage ?: "Error al publicar")
        }
    }

    /**
     * Solo observa la respuesta asociada al ID indicado.
     * Devuelve un manejador que debe cerrarse al salir de la pantalla.
     */
    fun escucharRespuesta(
        id: String,
        onResult: (respuesta: Respuesta?, error: String?) -> Unit
    ): AutoCloseable {
        require(id.isNotBlank() && !id.contains('/')) { "ID inválido" }
        val referencia = respuestas.child(id)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val comando = snapshot.child("comando").getValue(String::class.java)
                val resultado = snapshot.child("resultado").getValue(String::class.java)
                if (comando in permitidos && resultado in setOf(
                        "EJECUTADO", "RECHAZADO", "ERROR"
                    )
                ) {
                    onResult(Respuesta(id, comando!!, resultado!!), null)
                } else {
                    onResult(null, "Formato de respuesta inválido")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null, error.message)
            }
        }
        referencia.addValueEventListener(listener)
        return AutoCloseable { referencia.removeEventListener(listener) }
    }
}
