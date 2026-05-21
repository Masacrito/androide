package com.example.proyecto_android_inventarios

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLogin.setOnClickListener {
            iniciarSesion()
        }
    }

    override fun onStart() {
        super.onStart()

        // Si Firebase ya tiene una sesion activa, revisamos el rol en Firestore.
        auth.currentUser?.uid?.let { uid ->
            cambiarEstadoCarga(true)
            consultarUsuarioYRedirigir(uid)
        }
    }

    private fun iniciarSesion() {
        val correo = binding.editTextCorreo.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if (correo.isEmpty() || password.isEmpty()) {
            mostrarMensaje("Escribe correo y contrasena.")
            return
        }

        cambiarEstadoCarga(true)

        auth.signInWithEmailAndPassword(correo, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid

                if (uid == null) {
                    cambiarEstadoCarga(false)
                    mostrarMensaje("No se pudo obtener el UID del usuario.")
                    return@addOnSuccessListener
                }

                consultarUsuarioYRedirigir(uid)
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo iniciar sesion.")
            }
    }

    private fun consultarUsuarioYRedirigir(uid: String) {
        // El documento debe existir en usuarios/{uid}, usando el UID real de Authentication.
        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->
                cambiarEstadoCarga(false)

                if (!documento.exists()) {
                    auth.signOut()
                    mostrarMensaje("No existe el perfil del usuario en Firestore.")
                    return@addOnSuccessListener
                }

                val activo = documento.getBoolean("estado") ?: false
                if (!activo) {
                    auth.signOut()
                    mostrarMensaje("El usuario esta inactivo.")
                    return@addOnSuccessListener
                }

                when (documento.getString("rolId")) {
                    "admin" -> abrirHome(HomeAdminActivity::class.java)
                    "usuario" -> abrirHome(HomeUsuarioActivity::class.java)
                    else -> {
                        auth.signOut()
                        mostrarMensaje("El rol del usuario no es valido.")
                    }
                }
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo consultar el usuario.")
            }
    }

    private fun abrirHome(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun cambiarEstadoCarga(cargando: Boolean) {
        binding.buttonLogin.isEnabled = !cargando
        binding.editTextCorreo.isEnabled = !cargando
        binding.editTextPassword.isEnabled = !cargando
        binding.progressLogin.visibility = if (cargando) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
