package com.example.proyecto_android_inventarios

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityNuevaVentaBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NuevaVentaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevaVentaBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val productos = mutableListOf<Producto>()
    private val productosVenta = mutableListOf<ProductoVenta>()

    private var negocioId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNuevaVentaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""
        if (negocioId.isBlank()) {
            mostrarMensaje("No se recibio el negocio activo.")
            finish()
            return
        }

        configurarMetodoPago()
        configurarEventos()
        cargarProductos()
    }

    private fun configurarMetodoPago() {
        val metodos = listOf("Efectivo", "Tarjeta", "Transferencia")
        binding.spinnerMetodoPago.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            metodos
        )
    }

    private fun configurarEventos() {
        binding.buttonVolver.setOnClickListener { finish() }
        binding.buttonAgregarProducto.setOnClickListener { agregarProductoAVenta() }
        binding.buttonConfirmarVenta.setOnClickListener { confirmarVenta() }

        binding.spinnerProductos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mostrarDatosProductoSeleccionado()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun cargarProductos() {
        cambiarEstadoCarga(true)

        db.collection("negocios")
            .document(negocioId)
            .collection("productos")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { resultado ->
                cambiarEstadoCarga(false)
                productos.clear()

                for (documento in resultado.documents) {
                    productos.add(
                        Producto(
                            id = documento.id,
                            nombre = documento.getString("nombre") ?: "",
                            codigo = documento.getString("codigo") ?: "",
                            categoriaId = documento.getString("categoriaId") ?: "",
                            descripcion = documento.getString("descripcion") ?: "",
                            precio = documento.getDouble("precio") ?: 0.0,
                            stockActual = documento.getLong("stockActual")?.toInt() ?: 0,
                            stockMinimo = documento.getLong("stockMinimo")?.toInt() ?: 0,
                            estado = documento.getBoolean("estado") ?: false
                        )
                    )
                }

                productos.sortBy { it.nombre.lowercase() }
                cargarSpinnerProductos()
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudieron cargar los productos.")
            }
    }

    private fun cargarSpinnerProductos() {
        if (productos.isEmpty()) {
            binding.textProductoSeleccionado.text = "No hay productos activos."
            binding.buttonAgregarProducto.isEnabled = false
            return
        }

        binding.buttonAgregarProducto.isEnabled = true
        val nombres = productos.map { "${it.nombre} (${it.codigo})" }
        binding.spinnerProductos.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            nombres
        )
        mostrarDatosProductoSeleccionado()
    }

    private fun mostrarDatosProductoSeleccionado() {
        val producto = obtenerProductoSeleccionado() ?: return
        binding.textProductoSeleccionado.text =
            "Precio: $${formatoDinero(producto.precio)} | Stock: ${producto.stockActual}"
    }

    private fun agregarProductoAVenta() {
        val producto = obtenerProductoSeleccionado()
        if (producto == null) {
            mostrarMensaje("Selecciona un producto.")
            return
        }

        val cantidad = binding.editTextCantidad.text.toString().trim().toIntOrNull()
        if (cantidad == null || cantidad <= 0) {
            mostrarMensaje("La cantidad debe ser mayor a 0.")
            return
        }

        if (cantidad > producto.stockActual) {
            mostrarMensaje("La cantidad supera el stock disponible.")
            return
        }

        val existente = productosVenta.firstOrNull { it.producto.id == producto.id }
        val cantidadTotal = (existente?.cantidad ?: 0) + cantidad
        if (cantidadTotal > producto.stockActual) {
            mostrarMensaje("La cantidad total supera el stock disponible.")
            return
        }

        if (existente == null) {
            productosVenta.add(ProductoVenta(producto, cantidad))
        } else {
            existente.cantidad = cantidadTotal
        }

        binding.editTextCantidad.text?.clear()
        mostrarProductosVenta()
    }

    private fun mostrarProductosVenta() {
        binding.contenedorDetalleVenta.removeAllViews()

        if (productosVenta.isEmpty()) {
            binding.textDetalleVacio.visibility = View.VISIBLE
        } else {
            binding.textDetalleVacio.visibility = View.GONE
        }

        for (item in productosVenta) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(R.drawable.bg_card_dark)
            }

            val nombre = TextView(this).apply {
                text = item.producto.nombre
                textSize = 17f
                setTextColor(getColor(R.color.app_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            }

            val datos = TextView(this).apply {
                text = "Cantidad: ${item.cantidad} | Precio: $${formatoDinero(item.producto.precio)} | Subtotal: $${formatoDinero(item.subtotal)}"
                textSize = 14f
                setTextColor(getColor(R.color.app_text_secondary))
            }

            val quitar = MaterialButton(this).apply {
                text = "Quitar"
                isAllCaps = false
                setTextColor(getColor(R.color.app_accent))
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.app_accent_soft))
                setOnClickListener {
                    productosVenta.remove(item)
                    mostrarProductosVenta()
                }
            }

            fila.addView(nombre)
            fila.addView(datos)
            fila.addView(quitar)
            binding.contenedorDetalleVenta.addView(fila, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }

        binding.textTotalVenta.text = "Total: $${formatoDinero(calcularTotal())}"
    }

    private fun confirmarVenta() {
        if (productosVenta.isEmpty()) {
            mostrarMensaje("Agrega al menos un producto a la venta.")
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            mostrarMensaje("No hay una sesion activa.")
            return
        }

        cambiarEstadoCarga(true)

        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { usuarioDoc ->
                val nombreUsuario = usuarioDoc.getString("nombre")
                    ?: auth.currentUser?.displayName
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "Usuario"

                val negocioActivoId = usuarioDoc.getString("negocioActivoId")
                if (negocioActivoId != negocioId) {
                    cambiarEstadoCarga(false)
                    mostrarMensaje("El negocio activo no coincide con esta venta.")
                    return@addOnSuccessListener
                }

                registrarVenta(uid, nombreUsuario)
            }
            .addOnFailureListener { error ->
                cambiarEstadoCarga(false)
                mostrarMensaje(error.message ?: "No se pudo cargar el usuario.")
            }
    }

    private fun registrarVenta(uid: String, nombreUsuario: String) {
        val ventaRef = db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .document()

        val folio = generarFolio()
        val metodoPago = binding.spinnerMetodoPago.selectedItem.toString()
        val items = productosVenta.map { it.copy() }

        db.runTransaction { transaction ->
            val productosActualizados = mutableListOf<Pair<ProductoVenta, Int>>()

            // Primero se leen todos los productos y se valida stock antes de escribir.
            for (item in items) {
                val productoRef = db.collection("negocios")
                    .document(negocioId)
                    .collection("productos")
                    .document(item.producto.id)

                val productoDoc = transaction.get(productoRef)
                val estado = productoDoc.getBoolean("estado") ?: false
                val stockActual = productoDoc.getLong("stockActual")?.toInt() ?: 0

                if (!productoDoc.exists() || !estado) {
                    throw Exception("El producto ${item.producto.nombre} ya no esta activo.")
                }

                if (item.cantidad > stockActual) {
                    throw Exception("Stock insuficiente para ${item.producto.nombre}. Disponible: $stockActual.")
                }

                productosActualizados.add(item to stockActual)
            }

            val ventaData = hashMapOf<String, Any>(
                "folio" to folio,
                "fecha" to FieldValue.serverTimestamp(),
                "idUsuario" to uid,
                "nombreUsuario" to nombreUsuario,
                "total" to calcularTotal(items),
                "metodoPago" to metodoPago,
                "estado" to "completada"
            )

            transaction.set(ventaRef, ventaData)

            for ((item, stockAnterior) in productosActualizados) {
                val productoRef = db.collection("negocios")
                    .document(negocioId)
                    .collection("productos")
                    .document(item.producto.id)
                val stockNuevo = stockAnterior - item.cantidad

                val detalleRef = ventaRef.collection("detalle").document()
                val detalleData = hashMapOf<String, Any>(
                    "idProducto" to item.producto.id,
                    "nombreProducto" to item.producto.nombre,
                    "cantidad" to item.cantidad,
                    "precioUnitario" to item.producto.precio,
                    "subtotal" to item.subtotal
                )

                val movimientoRef = db.collection("negocios")
                    .document(negocioId)
                    .collection("movimientos")
                    .document()
                val movimientoData = hashMapOf<String, Any>(
                    "idProducto" to item.producto.id,
                    "nombreProducto" to item.producto.nombre,
                    "tipo" to "salida",
                    "motivo" to "venta",
                    "cantidad" to item.cantidad,
                    "stockAnterior" to stockAnterior,
                    "stockNuevo" to stockNuevo,
                    "idUsuario" to uid,
                    "nombreUsuario" to nombreUsuario,
                    "referencia" to ventaRef.id,
                    "fecha" to FieldValue.serverTimestamp(),
                    "observaciones" to "Venta $folio"
                )

                transaction.set(detalleRef, detalleData)
                transaction.update(productoRef, "stockActual", stockNuevo)
                transaction.set(movimientoRef, movimientoData)
            }
        }.addOnSuccessListener {
            cambiarEstadoCarga(false)
            productosVenta.clear()
            mostrarProductosVenta()
            mostrarMensaje("Venta registrada correctamente")
            cargarProductos()
        }.addOnFailureListener { error ->
            cambiarEstadoCarga(false)
            mostrarMensaje(error.message ?: "No se pudo registrar la venta.")
        }
    }

    private fun obtenerProductoSeleccionado(): Producto? {
        val position = binding.spinnerProductos.selectedItemPosition
        if (position < 0 || position >= productos.size) {
            return null
        }
        return productos[position]
    }

    private fun calcularTotal(): Double {
        return calcularTotal(productosVenta)
    }

    private fun calcularTotal(items: List<ProductoVenta>): Double {
        return items.sumOf { it.subtotal }
    }

    private fun generarFolio(): String {
        val formato = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        return "VTA-${formato.format(Date())}"
    }

    private fun formatoDinero(valor: Double): String {
        return String.format(Locale.getDefault(), "%.2f", valor)
    }

    private fun cambiarEstadoCarga(cargando: Boolean) {
        binding.progressNuevaVenta.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.buttonAgregarProducto.isEnabled = !cargando && productos.isNotEmpty()
        binding.buttonConfirmarVenta.isEnabled = !cargando
        binding.buttonVolver.isEnabled = !cargando
        binding.spinnerProductos.isEnabled = !cargando
        binding.spinnerMetodoPago.isEnabled = !cargando
        binding.editTextCantidad.isEnabled = !cargando
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
