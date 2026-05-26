package com.example.proyecto_android_inventarios

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.proyecto_android_inventarios.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        binding.buttonLogin.setOnClickListener { iniciarSesionCorreo() }
        binding.buttonRegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.buttonGoogle.setOnClickListener { iniciarSesionGoogle() }
    }

    override fun onStart() {
        super.onStart()

        // Si Firebase ya tiene una sesion activa, aplicamos el mismo flujo comun.
        if (auth.currentUser != null) {
            cambiarEstadoCarga(true)
            AuthFlow.verificarUsuarioEnFirestore(this) {
                cambiarEstadoCarga(false)
            }
        }
    }

    private fun iniciarSesionCorreo() {
        val correo = binding.editTextCorreo.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if (correo.isEmpty() || password.isEmpty()) {
            mostrarMensaje("Escribe correo y contrasena.")
            return
        }

        cambiarEstadoCarga(true)

        auth.signInWithEmailAndPassword(correo, password)
            .addOnSuccessListener {
                AuthFlow.verificarUsuarioEnFirestore(this) {
                    cambiarEstadoCarga(false)
                }
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo iniciar sesion.")
            }
    }

    private fun iniciarSesionGoogle() {
        val webClientId = obtenerWebClientId()
        if (webClientId.isNullOrBlank()) {
            mostrarMensaje("Activa Google en Firebase y descarga el google-services.json actualizado.")
            return
        }

        cambiarEstadoCarga(true)

        val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )

                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    autenticarFirebaseConGoogle(googleCredential.idToken)
                } else {
                    cambiarEstadoCarga(false)
                    mostrarMensaje("No se recibio una credencial valida de Google.")
                }
            } catch (error: GoogleIdTokenParsingException) {
                cambiarEstadoCarga(false)
                mostrarMensaje("No se pudo leer la credencial de Google.")
            } catch (error: NoCredentialException) {
                cambiarEstadoCarga(false)
                mostrarMensaje("No hay cuenta Google disponible en este dispositivo.")
            } catch (error: Exception) {
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo iniciar sesion con Google.")
            }
        }
    }

    private fun obtenerWebClientId(): String? {
        val resourceId = resources.getIdentifier(
            "default_web_client_id",
            "string",
            packageName
        )

        if (resourceId == 0) {
            return null
        }

        return getString(resourceId)
    }

    private fun autenticarFirebaseConGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(firebaseCredential)
            .addOnSuccessListener {
                AuthFlow.verificarUsuarioEnFirestore(this) {
                    cambiarEstadoCarga(false)
                }
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo autenticar con Firebase.")
            }
    }

    private fun cambiarEstadoCarga(cargando: Boolean) {
        binding.buttonLogin.isEnabled = !cargando
        binding.buttonRegistro.isEnabled = !cargando
        binding.buttonGoogle.isEnabled = !cargando
        binding.editTextCorreo.isEnabled = !cargando
        binding.editTextPassword.isEnabled = !cargando
        binding.progressLogin.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
