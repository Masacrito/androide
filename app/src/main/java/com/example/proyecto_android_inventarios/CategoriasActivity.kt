package com.example.proyecto_android_inventarios

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityCategoriasBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class CategoriasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriasBinding

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val categorias = mutableListOf<Categoria>()
    private var negocioId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""
        if (negocioId.isBlank()) {
            mostrarMensaje("No se recibio el negocio activo.")
            finish()
            return
        }

        binding.buttonVolver.setOnClickListener { finish() }
        binding.buttonNuevaCategoria.setOnClickListener { mostrarFormularioCategoria(null) }

        cargarCategorias()
    }

    private fun cargarCategorias() {
        binding.progressCategorias.visibility = View.VISIBLE

        // Por defecto solo mostramos categorias activas.
        db.collection("negocios")
            .document(negocioId)
            .collection("categorias")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { resultado ->
                binding.progressCategorias.visibility = View.GONE
                categorias.clear()

                for (documento in resultado.documents) {
                    categorias.add(
                        Categoria(
                            id = documento.id,
                            nombre = documento.getString("nombre") ?: "",
                            descripcion = documento.getString("descripcion") ?: "",
                            estado = documento.getBoolean("estado") ?: false
                        )
                    )
                }

                categorias.sortBy { it.nombre.lowercase() }
                mostrarCategoriasEnPantalla()
            }
            .addOnFailureListener { error ->
                binding.progressCategorias.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudieron cargar las categorias.")
            }
    }

    private fun mostrarCategoriasEnPantalla() {
        binding.contenedorCategorias.removeAllViews()

        if (categorias.isEmpty()) {
            binding.textListaVacia.visibility = View.VISIBLE
            return
        }

        binding.textListaVacia.visibility = View.GONE

        for (categoria in categorias) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(R.drawable.bg_card_dark)
            }

            val nombre = TextView(this).apply {
                text = categoria.nombre
                textSize = 17f
                setTextColor(getColor(R.color.app_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            }

            val descripcion = TextView(this).apply {
                text = if (categoria.descripcion.isBlank()) {
                    "Sin descripcion"
                } else {
                    categoria.descripcion
                }
                textSize = 14f
                setTextColor(getColor(R.color.app_text_secondary))
            }

            val botones = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }

            val editar = MaterialButton(this).apply {
                text = "Editar"
                isAllCaps = false
                setTextColor(getColor(R.color.app_text_primary))
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.app_surface_elevated))
                setOnClickListener { mostrarFormularioCategoria(categoria) }
            }

            val desactivar = MaterialButton(this).apply {
                text = "Desactivar"
                isAllCaps = false
                setTextColor(getColor(R.color.app_accent))
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.app_accent_soft))
                setOnClickListener { confirmarDesactivarCategoria(categoria) }
            }

            botones.addView(editar)
            botones.addView(desactivar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) })
            fila.addView(nombre)
            fila.addView(descripcion)
            fila.addView(botones)

            binding.contenedorCategorias.addView(fila, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
    }

    private fun mostrarFormularioCategoria(categoria: Categoria?) {
        val formulario = layoutInflater.inflate(R.layout.dialog_categoria, null)
        val editNombre = formulario.findViewById<TextInputEditText>(R.id.editTextNombreCategoria)
        val editDescripcion = formulario.findViewById<TextInputEditText>(R.id.editTextDescripcionCategoria)
        val switchEstado = formulario.findViewById<MaterialSwitch>(R.id.switchEstadoCategoria)

        editNombre.setText(categoria?.nombre ?: "")
        editDescripcion.setText(categoria?.descripcion ?: "")
        switchEstado.isChecked = categoria?.estado ?: true

        val titulo = if (categoria == null) "Nueva categoria" else "Editar categoria"

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(formulario)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        guardarCategoria(
                            categoria = categoria,
                            nombre = editNombre.text.toString().trim(),
                            descripcion = editDescripcion.text.toString().trim(),
                            estado = switchEstado.isChecked,
                            dialog = this
                        )
                    }
                }
                show()
            }
    }

    private fun guardarCategoria(
        categoria: Categoria?,
        nombre: String,
        descripcion: String,
        estado: Boolean,
        dialog: AlertDialog
    ) {
        if (nombre.isEmpty()) {
            mostrarMensaje("El nombre de la categoria es requerido.")
            return
        }

        val datos = hashMapOf(
            "nombre" to nombre,
            "descripcion" to descripcion,
            "estado" to estado
        )

        val referencia = if (categoria == null) {
            db.collection("negocios").document(negocioId).collection("categorias").document()
        } else {
            db.collection("negocios").document(negocioId).collection("categorias").document(categoria.id)
        }

        referencia.set(datos)
            .addOnSuccessListener {
                dialog.dismiss()
                mostrarMensaje("Categoria guardada.")
                cargarCategorias()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo guardar la categoria.")
            }
    }

    private fun confirmarDesactivarCategoria(categoria: Categoria) {
        AlertDialog.Builder(this)
            .setTitle("Desactivar categoria")
            .setMessage("La categoria ya no aparecera en las listas activas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Desactivar") { _, _ ->
                desactivarCategoria(categoria)
            }
            .show()
    }

    private fun desactivarCategoria(categoria: Categoria) {
        db.collection("negocios")
            .document(negocioId)
            .collection("categorias")
            .document(categoria.id)
            .update("estado", false)
            .addOnSuccessListener {
                mostrarMensaje("Categoria desactivada.")
                cargarCategorias()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo desactivar la categoria.")
            }
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun dp(valor: Int): Int {
        return (valor * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_NEGOCIO_ID = "extra_negocio_id"
    }
}