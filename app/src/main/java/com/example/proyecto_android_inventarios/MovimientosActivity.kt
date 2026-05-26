package com.example.proyecto_android_inventarios

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityMovimientosBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class MovimientosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovimientosBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val movimientos = mutableListOf<MovimientoInventario>()

    private var negocioId: String = ""
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovimientosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        binding.buttonVolver.setOnClickListener { finish() }
        configurarFiltroTipo()
        configurarBusqueda()
        cargarNegocioActivo()
    }

    private fun configurarFiltroTipo() {
        val tipos = listOf("Todos", "Entrada", "Salida", "Ajuste")
        binding.spinnerTipoMovimiento.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            tipos
        )

        binding.spinnerTipoMovimiento.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mostrarMovimientosFiltrados()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun configurarBusqueda() {
        binding.editTextBuscarProducto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                mostrarMovimientosFiltrados()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun cargarNegocioActivo() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            mostrarMensaje("No hay una sesion activa.")
            finish()
            return
        }

        binding.progressMovimientos.visibility = View.VISIBLE

        // Se vuelve a leer negocioActivoId para asegurar que la pantalla use el negocio actual.
        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->
                negocioId = documento.getString("negocioActivoId") ?: ""

                if (negocioId.isBlank()) {
                    binding.progressMovimientos.visibility = View.GONE
                    abrirCrearNegocio()
                    return@addOnSuccessListener
                }

                cargarMovimientos()
            }
            .addOnFailureListener { error ->
                binding.progressMovimientos.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudo cargar el usuario.")
            }
    }

    private fun cargarMovimientos() {
        db.collection("negocios")
            .document(negocioId)
            .collection("movimientos")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { resultado ->
                binding.progressMovimientos.visibility = View.GONE
                movimientos.clear()

                for (documento in resultado.documents) {
                    movimientos.add(
                        MovimientoInventario(
                            id = documento.id,
                            nombreProducto = documento.getString("nombreProducto") ?: "",
                            tipo = documento.getString("tipo") ?: "",
                            motivo = documento.getString("motivo") ?: "",
                            cantidad = documento.getLong("cantidad")?.toInt() ?: 0,
                            stockAnterior = documento.getLong("stockAnterior")?.toInt() ?: 0,
                            stockNuevo = documento.getLong("stockNuevo")?.toInt() ?: 0,
                            nombreUsuario = documento.getString("nombreUsuario") ?: "",
                            fecha = documento.getTimestamp("fecha"),
                            observaciones = documento.getString("observaciones") ?: ""
                        )
                    )
                }

                mostrarMovimientosFiltrados()
            }
            .addOnFailureListener { error ->
                binding.progressMovimientos.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudieron cargar los movimientos.")
            }
    }

    private fun mostrarMovimientosFiltrados() {
        binding.contenedorMovimientos.removeAllViews()

        val tipoSeleccionado = binding.spinnerTipoMovimiento.selectedItem?.toString()?.lowercase() ?: "todos"
        val busqueda = binding.editTextBuscarProducto.text.toString().trim().lowercase()

        val filtrados = movimientos.filter { movimiento ->
            val coincideTipo = tipoSeleccionado == "todos" || movimiento.tipo.lowercase() == tipoSeleccionado
            val coincideProducto = busqueda.isEmpty() || movimiento.nombreProducto.lowercase().contains(busqueda)
            coincideTipo && coincideProducto
        }

        if (filtrados.isEmpty()) {
            binding.textListaVacia.visibility = View.VISIBLE
            binding.textListaVacia.text = "Aun no hay movimientos registrados."
            return
        }

        binding.textListaVacia.visibility = View.GONE

        for (movimiento in filtrados) {
            binding.contenedorMovimientos.addView(crearVistaMovimiento(movimiento))
        }
    }

    private fun crearVistaMovimiento(movimiento: MovimientoInventario): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = crearFondoMovimiento(movimiento.tipo)
        }

        val titulo = TextView(this).apply {
            text = movimiento.nombreProducto
            textSize = 18f
            setTextColor(getColor(R.color.app_text_primary))
            setTypeface(typeface, Typeface.BOLD)
        }

        val tipoTexto = TextView(this).apply {
            text = "${etiquetaTipo(movimiento.tipo)} | Motivo: ${formatearMotivo(movimiento.motivo)}"
            setTextColor(colorTipo(movimiento.tipo))
            setTypeface(typeface, Typeface.BOLD)
        }

        val datos = TextView(this).apply {
            text = "Cantidad: ${movimiento.cantidad}\n" +
                    "Stock anterior: ${movimiento.stockAnterior} | Stock nuevo: ${movimiento.stockNuevo}\n" +
                    "Fecha: ${formatearFecha(movimiento)}\n" +
                    "Usuario: ${movimiento.nombreUsuario}\n" +
                    "Observaciones: ${movimiento.observaciones.ifBlank { "Sin observaciones" }}"
            setTextColor(getColor(R.color.app_text_secondary))
        }

        fila.addView(titulo)
        fila.addView(tipoTexto)
        fila.addView(datos)

        return fila.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 18)
            }
        }
    }

    private fun crearFondoMovimiento(tipo: String): GradientDrawable {
        val color = when (tipo.lowercase()) {
            "entrada" -> getColor(R.color.app_surface)
            "salida" -> getColor(R.color.app_surface_elevated)
            "ajuste" -> getColor(R.color.app_surface)
            else -> getColor(R.color.app_surface)
        }

        return GradientDrawable().apply {
            setColor(color)
            setStroke(1, getColor(R.color.app_border_strong))
            cornerRadius = 10f
        }
    }

    private fun etiquetaTipo(tipo: String): String {
        return when (tipo.lowercase()) {
            "entrada" -> "Entrada de stock"
            "salida" -> "Salida de stock"
            "ajuste" -> "Ajuste de inventario"
            else -> tipo.ifBlank { "Movimiento" }
        }
    }

    private fun colorTipo(tipo: String): Int {
        return when (tipo.lowercase()) {
            "entrada" -> getColor(R.color.app_success)
            "salida" -> getColor(R.color.app_accent)
            "ajuste" -> getColor(R.color.app_warning)
            else -> getColor(R.color.app_text_primary)
        }
    }

    private fun formatearMotivo(motivo: String): String {
        return when (motivo) {
            "venta" -> "Venta"
            "compra" -> "Compra"
            "producto_dañado" -> "Producto dañado"
            "uso_interno" -> "Uso interno"
            "perdida" -> "Perdida"
            "ajuste_manual" -> "Ajuste manual"
            else -> motivo.ifBlank { "Sin motivo" }
        }
    }

    private fun formatearFecha(movimiento: MovimientoInventario): String {
        val fecha = movimiento.fecha?.toDate()
        return if (fecha == null) "Sin fecha" else formatoFecha.format(fecha)
    }

    private fun abrirCrearNegocio() {
        startActivity(android.content.Intent(this, CrearNegocioActivity::class.java))
        finish()
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
