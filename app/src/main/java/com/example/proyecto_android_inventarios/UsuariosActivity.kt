package com.example.proyecto_android_inventarios

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityUsuariosBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UsuariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsuariosBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val usuarios = mutableListOf<Usuario>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        binding.buttonVolver.setOnClickListener { finish() }

        validarAdminActual()
    }

    private fun validarAdminActual() {
        val uidActual = auth.currentUser?.uid

        if (uidActual == null) {
            mostrarMensaje("No hay una sesion activa.")
            finish()
            return
        }

        binding.progressUsuarios.visibility = View.VISIBLE

        // Pantalla reservada para un posible admin global del sistema.
        db.collection("usuarios")
            .document(uidActual)
            .get()
            .addOnSuccessListener { documento ->
                val esAdmin = documento.getString("rolGlobal") == "adminGlobal"
                val activo = documento.getBoolean("estado") == true

                if (!esAdmin || !activo) {
                    binding.progressUsuarios.visibility = View.GONE
                    mostrarMensaje("Solo un adminGlobal activo puede entrar a usuarios.")
                    finish()
                    return@addOnSuccessListener
                }

                cargarUsuarios()
            }
            .addOnFailureListener { error ->
                binding.progressUsuarios.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudo validar el administrador.")
                finish()
            }
    }

    private fun cargarUsuarios() {
        db.collection("usuarios")
            .get()
            .addOnSuccessListener { resultado ->
                binding.progressUsuarios.visibility = View.GONE
                usuarios.clear()

                for (documento in resultado.documents) {
                    usuarios.add(
                        Usuario(
                            uid = documento.getString("uid") ?: documento.id,
                            nombre = documento.getString("nombre") ?: "",
                            correo = documento.getString("correo") ?: "",
                            estado = documento.getBoolean("estado") ?: false,
                            rolGlobal = documento.getString("rolGlobal") ?: "usuario",
                            negocioActivoId = documento.getString("negocioActivoId")
                        )
                    )
                }

                usuarios.sortBy { it.nombre.lowercase() }
                mostrarUsuariosEnPantalla()
            }
            .addOnFailureListener { error ->
                binding.progressUsuarios.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudieron cargar los usuarios.")
            }
    }

    private fun mostrarUsuariosEnPantalla() {
        binding.contenedorUsuarios.removeAllViews()

        if (usuarios.isEmpty()) {
            binding.textListaVacia.visibility = View.VISIBLE
            return
        }

        binding.textListaVacia.visibility = View.GONE

        for (usuario in usuarios) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 18, 0, 18)
            }

            val nombre = TextView(this).apply {
                text = usuario.nombre
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            }

            val datos = TextView(this).apply {
                val estadoTexto = if (usuario.estado) "Activo" else "Inactivo"
                text = "${usuario.correo}\nRol global: ${usuario.rolGlobal}\nEstado: $estadoTexto"
            }

            val editar = MaterialButton(this).apply {
                text = "Editar"
                setOnClickListener { mostrarFormularioUsuario(usuario) }
            }

            fila.addView(nombre)
            fila.addView(datos)
            fila.addView(editar)
            binding.contenedorUsuarios.addView(fila)
        }
    }

    private fun mostrarFormularioUsuario(usuario: Usuario) {
        val formulario = layoutInflater.inflate(R.layout.dialog_usuario, null)
        val editNombre = formulario.findViewById<TextInputEditText>(R.id.editTextNombreUsuario)
        val textCorreo = formulario.findViewById<TextView>(R.id.textCorreoUsuario)
        val spinnerRol = formulario.findViewById<Spinner>(R.id.spinnerRolUsuario)
        val switchEstado = formulario.findViewById<MaterialSwitch>(R.id.switchEstadoUsuario)

        val roles = listOf("adminGlobal", "usuario")
        spinnerRol.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles
        )

        editNombre.setText(usuario.nombre)
        textCorreo.text = usuario.correo
        switchEstado.isChecked = usuario.estado

        val indiceRol = roles.indexOf(usuario.rolGlobal)
        if (indiceRol >= 0) {
            spinnerRol.setSelection(indiceRol)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar usuario")
            .setView(formulario)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        guardarUsuario(
                            usuario = usuario,
                            nombre = editNombre.text.toString().trim(),
                            rolId = roles[spinnerRol.selectedItemPosition],
                            estado = switchEstado.isChecked,
                            dialog = this
                        )
                    }
                }
                show()
            }
    }

    private fun guardarUsuario(
        usuario: Usuario,
        nombre: String,
        rolId: String,
        estado: Boolean,
        dialog: AlertDialog
    ) {
        if (nombre.isEmpty()) {
            mostrarMensaje("El nombre del usuario es requerido.")
            return
        }

        if (rolId.isEmpty()) {
            mostrarMensaje("El rol del usuario es requerido.")
            return
        }

        val uidActual = auth.currentUser?.uid
        if (usuario.uid == uidActual && !estado) {
            mostrarMensaje("No puedes desactivar tu propio usuario administrador.")
            return
        }

        if (usuario.uid == uidActual && rolId != "adminGlobal") {
            mostrarMensaje("No puedes quitarte el rol adminGlobal a ti mismo.")
            return
        }

        val datos = mapOf(
            "nombre" to nombre,
            "rolGlobal" to rolId,
            "estado" to estado
        )

        // No se modifica correo, contrasena ni se borra la cuenta de Authentication.
        db.collection("usuarios")
            .document(usuario.uid)
            .update(datos)
            .addOnSuccessListener {
                dialog.dismiss()
                mostrarMensaje("Usuario actualizado.")
                cargarUsuarios()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo actualizar el usuario.")
            }
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
