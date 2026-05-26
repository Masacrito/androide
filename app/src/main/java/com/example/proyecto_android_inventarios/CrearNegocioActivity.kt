package com.example.proyecto_android_inventarios

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityCrearNegocioBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class CrearNegocioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrearNegocioBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrearNegocioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        binding.buttonCrearNegocio.setOnClickListener {
            crearNegocio()
        }

        binding.buttonCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun crearNegocio() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            mostrarMensaje("No hay una sesion activa.")
            return
        }

        val nombre = binding.editTextNombreNegocio.text.toString().trim()
        val descripcion = binding.editTextDescripcionNegocio.text.toString().trim()

        if (nombre.isEmpty()) {
            mostrarMensaje("El nombre del negocio es requerido.")
            return
        }

        cambiarEstadoCarga(true)

        val negocioRef = db.collection("negocios").document()
        val datosNegocio = hashMapOf<String, Any>(
            "nombre" to nombre,
            "descripcion" to descripcion,
            "dueñoUid" to uid,
            "estado" to true,
            "fechaRegistro" to FieldValue.serverTimestamp()
        )

        negocioRef.set(datosNegocio)
            .addOnSuccessListener {
                actualizarNegocioActivo(uid, negocioRef.id)
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo crear el negocio.")
            }
    }

    private fun actualizarNegocioActivo(uid: String, negocioId: String) {
        db.collection("usuarios")
            .document(uid)
            .update("negocioActivoId", negocioId)
            .addOnSuccessListener {
                cambiarEstadoCarga(false)
                abrirHomeNegocio(negocioId)
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo asignar el negocio al usuario.")
            }
    }

    private fun abrirHomeNegocio(negocioId: String) {
        val intent = Intent(this, HomeNegocioActivity::class.java)
        intent.putExtra(HomeNegocioActivity.EXTRA_NEGOCIO_ID, negocioId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun cerrarSesion() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun cambiarEstadoCarga(cargando: Boolean) {
        binding.buttonCrearNegocio.isEnabled = !cargando
        binding.buttonCerrarSesion.isEnabled = !cargando
        binding.editTextNombreNegocio.isEnabled = !cargando
        binding.editTextDescripcionNegocio.isEnabled = !cargando
        binding.progressCrearNegocio.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
