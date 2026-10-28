
package com.serverguard.iot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var editCorreo: EditText
    private lateinit var editContrasena: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegistrar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textEstado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si existe una sesión activa, abrir el panel
        if (auth.currentUser != null) {
            abrirDashboard()
            return
        }

        setContentView(R.layout.activity_main)

        editCorreo = findViewById(R.id.editCorreo)
        editContrasena = findViewById(R.id.editContrasena)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        progressBar = findViewById(R.id.progressBar)
        textEstado = findViewById(R.id.textEstado)

        btnLogin.setOnClickListener {
            iniciarSesion()
        }

        btnRegistrar.setOnClickListener {
            registrarUsuario()
        }
    }

    private fun validarCampos(): Pair<String, String>? {

        val correo = editCorreo.text.toString().trim()
        val contrasena = editContrasena.text.toString()

        if (correo.isEmpty()) {
            editCorreo.error = "Ingresa tu correo"
            editCorreo.requestFocus()
            return null
        }

        if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(correo).matches()) {
            editCorreo.error = "Correo electrónico inválido"
            editCorreo.requestFocus()
            return null
        }

        if (contrasena.isEmpty()) {
            editContrasena.error = "Ingresa tu contraseña"
            editContrasena.requestFocus()
            return null
        }

        if (contrasena.length < 6) {
            editContrasena.error =
                "La contraseña debe tener al menos 6 caracteres"
            editContrasena.requestFocus()
            return null
        }

        return Pair(correo, contrasena)
    }

    private fun registrarUsuario() {

        val datos = validarCampos() ?: return

        mostrarCarga(true)
        textEstado.text = "Registrando usuario..."

        auth.createUserWithEmailAndPassword(
            datos.first,
            datos.second
        ).addOnCompleteListener(this) { tarea ->

            mostrarCarga(false)

            if (tarea.isSuccessful) {

                Toast.makeText(
                    this,
                    "Cuenta creada correctamente",
                    Toast.LENGTH_SHORT
                ).show()

                abrirDashboard()

            } else {

                val error = tarea.exception?.localizedMessage
                    ?: "Error desconocido"

                textEstado.text = "Error al registrar: $error"
            }
        }
    }

    private fun iniciarSesion() {

        val datos = validarCampos() ?: return

        mostrarCarga(true)
        textEstado.text = "Iniciando sesión..."

        auth.signInWithEmailAndPassword(
            datos.first,
            datos.second
        ).addOnCompleteListener(this) { tarea ->

            mostrarCarga(false)

            if (tarea.isSuccessful) {

                Toast.makeText(
                    this,
                    "Inicio de sesión exitoso",
                    Toast.LENGTH_SHORT
                ).show()

                abrirDashboard()

            } else {

                val error = tarea.exception?.localizedMessage
                    ?: "Credenciales incorrectas"

                textEstado.text =
                    "Error al iniciar sesión: $error"
            }
        }
    }

    private fun mostrarCarga(cargando: Boolean) {

        progressBar.visibility =
            if (cargando) View.VISIBLE else View.GONE

        btnLogin.isEnabled = !cargando
        btnRegistrar.isEnabled = !cargando
    }

    private fun abrirDashboard() {

        val intent = Intent(
            this,
            DashboardActivity::class.java
        )

        startActivity(intent)
        finish()
    }
}
