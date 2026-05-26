package com.example.proyecto_android_inventarios

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        binding.buttonCrearCuenta.setOnClickListener { registrarUsuario() }
        binding.buttonVolverLogin.setOnClickListener { finish() }
    }

    private fun registrarUsuario() {
        val nombre = binding.editTextNombre.text.toString().trim()
        val correo = binding.editTextCorreo.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()
        val confirmar = binding.editTextConfirmarPassword.text.toString().trim()

        if (nombre.isEmpty()) {
            mostrarMensaje("El nombre es requerido.")
            return
        }

        if (correo.isEmpty()) {
            mostrarMensaje("El correo es requerido.")
            return
        }

        if (password.length < 6) {
            mostrarMensaje("La contrasena debe tener minimo 6 caracteres.")
            return
        }

        if (password != confirmar) {
            mostrarMensaje("Las contrasenas no coinciden.")
            return
        }

        cambiarEstadoCarga(true)

        auth.createUserWithEmailAndPassword(correo, password)
            .addOnSuccessListener {
                actualizarDisplayNameYContinuar(nombre)
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo crear la cuenta.")
            }
    }

    private fun actualizarDisplayNameYContinuar(nombre: String) {
        val user = auth.currentUser

        if (user == null) {
            cambiarEstadoCarga(false)
            mostrarMensaje("No se pudo obtener el usuario registrado.")
            return
        }

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(nombre)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener {
                // Aunque falle displayName, usamos el nombre capturado para Firestore.
                AuthFlow.verificarUsuarioEnFirestore(this, nombreRegistro = nombre) {
                    cambiarEstadoCarga(false)
                }
            }
    }

    private fun cambiarEstadoCarga(cargando: Boolean) {
        binding.buttonCrearCuenta.isEnabled = !cargando
        binding.buttonVolverLogin.isEnabled = !cargando
        binding.editTextNombre.isEnabled = !cargando
        binding.editTextCorreo.isEnabled = !cargando
        binding.editTextPassword.isEnabled = !cargando
        binding.editTextConfirmarPassword.isEnabled = !cargando
        binding.progressRegistro.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
