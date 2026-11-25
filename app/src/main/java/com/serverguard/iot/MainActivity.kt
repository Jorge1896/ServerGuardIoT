package com.serverguard.iot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private val databaseUrl = "https://serverguardiot-default-rtdb.firebaseio.com/"
    private lateinit var auth: FirebaseAuth
    private lateinit var editCorreo: EditText
    private lateinit var editContrasena: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegistrar: Button
    private lateinit var btnRecuperar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textEstado: TextView
    private var comprobando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_main)
        editCorreo = findViewById(R.id.editCorreo)
        editContrasena = findViewById(R.id.editContrasena)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        btnRecuperar = findViewById(R.id.btnRecuperar)
        progressBar = findViewById(R.id.progressBar)
        textEstado = findViewById(R.id.textEstado)
        btnLogin.setOnClickListener { iniciarSesion() }
        btnRegistrar.setOnClickListener { registrarUsuario() }
        btnRecuperar.setOnClickListener { recuperarContrasena() }
        if (auth.currentUser != null) comprobarAcceso()
    }

    private fun validarCorreo(): String? {
        val correo = editCorreo.text.toString().trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            editCorreo.error = "Ingresa un correo electrónico válido"
            editCorreo.requestFocus()
            return null
        }
        return correo
    }

    private fun credenciales(esRegistro: Boolean): Pair<String, String>? {
        val correo = validarCorreo() ?: return null
        val clave = editContrasena.text.toString()
        if (clave.isEmpty() || (esRegistro && clave.length < 8)) {
            editContrasena.error = if (esRegistro)
                "Para registrarte utiliza al menos 8 caracteres"
            else "Ingresa la contraseña"
            editContrasena.requestFocus()
            return null
        }
        return correo to clave
    }

    private fun registrarUsuario() {
        val datos = credenciales(true) ?: return
        mostrarCarga(true)
        textEstado.text = "Registrando cuenta..."
        auth.createUserWithEmailAndPassword(datos.first, datos.second)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    textEstado.text =
                        "Cuenta creada. El administrador debe autorizar tu UID antes de acceder."
                    comprobarAcceso()
                } else {
                    mostrarCarga(false)
                    textEstado.text = "No fue posible crear la cuenta: " +
                        (tarea.exception?.localizedMessage ?: "error desconocido")
                }
            }
    }

    private fun iniciarSesion() {
        val datos = credenciales(false) ?: return
        mostrarCarga(true)
        textEstado.text = "Verificando credenciales..."
        auth.signInWithEmailAndPassword(datos.first, datos.second)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) comprobarAcceso()
                else {
                    mostrarCarga(false)
                    textEstado.text =
                        "No se pudo iniciar sesión. Revisa tus credenciales y tu conexión."
                }
            }
    }

    /**
     * /autorizados no es legible por clientes según las reglas vigentes.
     * Intentamos leer una ruta protegida por el mismo UID para comprobar acceso.
     * La protección efectiva siempre la aplican las reglas de Firebase.
     */
    private fun comprobarAcceso() {
        if (comprobando) return
        if (auth.currentUser == null) {
            mostrarCarga(false)
            return
        }
        comprobando = true
        mostrarCarga(true)
        textEstado.text = "Comprobando acceso autorizado..."
        FirebaseDatabase.getInstance(databaseUrl)
            .getReference("serverguard/configuracion").get()
            .addOnCompleteListener(this) { tarea ->
                comprobando = false
                mostrarCarga(false)
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                if (tarea.isSuccessful) {
                    abrirDashboard()
                } else {
                    val mensaje = tarea.exception?.localizedMessage.orEmpty()
                    if (mensaje.contains("permission_denied", true) ||
                        mensaje.contains("permission denied", true)) {
                        auth.signOut()
                        textEstado.text =
                            "Acceso denegado: tu cuenta no está autorizada. Solicita al administrador que registre tu UID."
                    } else {
                        textEstado.text =
                            "No se pudo verificar el acceso. Revisa Internet y vuelve a iniciar sesión."
                    }
                }
            }
    }

    private fun recuperarContrasena() {
        val correo = validarCorreo() ?: return
        mostrarCarga(true)
        textEstado.text = "Solicitando recuperación..."
        auth.sendPasswordResetEmail(correo).addOnCompleteListener(this) {
            mostrarCarga(false)
            textEstado.text = if (it.isSuccessful)
                "Si corresponde, revisa tu correo para restablecer la contraseña."
            else "No se pudo solicitar la recuperación. Comprueba el correo y la conexión."
        }
    }

    private fun mostrarCarga(cargando: Boolean) {
        progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !cargando
        btnRegistrar.isEnabled = !cargando
        btnRecuperar.isEnabled = !cargando
    }

    private fun abrirDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
