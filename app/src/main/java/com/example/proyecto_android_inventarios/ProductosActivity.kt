package com.example.proyecto_android_inventarios

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityProductosBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.abs

class ProductosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductosBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val productos = mutableListOf<Producto>()
    private val categorias = mutableListOf<Categoria>()

    private var esAdmin: Boolean = false
    private var negocioId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""
        if (negocioId.isBlank()) {
            mostrarMensaje("No se recibio el negocio activo.")
            finish()
            return
        }

        esAdmin = intent.getBooleanExtra(EXTRA_ES_ADMIN, true)

        binding.buttonVolver.setOnClickListener { finish() }
        binding.buttonNuevoProducto.visibility = if (esAdmin) View.VISIBLE else View.GONE
        binding.buttonNuevoProducto.setOnClickListener { mostrarFormularioProducto(null) }

        cargarCategoriasYProductos()
    }

    private fun cargarCategoriasYProductos() {
        binding.progressProductos.visibility = View.VISIBLE

        db.collection("negocios")
            .document(negocioId)
            .collection("categorias")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { resultadoCategorias ->
                categorias.clear()

                for (documento in resultadoCategorias.documents) {
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
                cargarProductos()
            }
            .addOnFailureListener { error ->
                binding.progressProductos.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudieron cargar las categorias.")
            }
    }

    private fun cargarProductos() {
        // Mostramos activos e inactivos para que al desactivar no desaparezcan del listado.
        db.collection("negocios")
            .document(negocioId)
            .collection("productos")
            .get()
            .addOnSuccessListener { resultado ->
                binding.progressProductos.visibility = View.GONE
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

                productos.sortWith(compareBy<Producto> { !it.estado }.thenBy { it.nombre.lowercase() })
                mostrarProductosEnPantalla()
            }
            .addOnFailureListener { error ->
                binding.progressProductos.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudieron cargar los productos.")
            }
    }

    private fun mostrarProductosEnPantalla() {
        binding.contenedorProductos.removeAllViews()

        val productosVisibles = if (esAdmin) {
            productos
        } else {
            productos.filter { it.estado }
        }

        if (productosVisibles.isEmpty()) {
            binding.textListaVacia.visibility = View.VISIBLE
            return
        }

        binding.textListaVacia.visibility = View.GONE

        for (producto in productosVisibles) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = crearFondoProducto(producto)
            }

            val nombre = TextView(this).apply {
                text = producto.nombre
                textSize = 18f
                setTextColor(getColor(R.color.app_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            }

            val datos = TextView(this).apply {
                text = "Codigo: ${producto.codigo}\n" +
                        "Categoria: ${obtenerNombreCategoria(producto.categoriaId)}\n" +
                        "Precio: $${producto.precio}\n" +
                        "Stock: ${producto.stockActual} | Minimo: ${producto.stockMinimo}"
                setTextColor(getColor(R.color.app_text_secondary))
            }

            fila.addView(nombre)
            fila.addView(datos)

            if (!producto.estado) {
                fila.addView(
                    TextView(this).apply {
                        text = "Producto inactivo"
                        setTextColor(getColor(R.color.app_text_tertiary))
                        setTypeface(typeface, Typeface.BOLD)
                    }
                )
            }

            if (producto.estado && producto.stockActual <= producto.stockMinimo) {
                fila.addView(
                    TextView(this).apply {
                        text = "Stock bajo"
                        setTextColor(getColor(R.color.app_warning))
                        setTypeface(typeface, Typeface.BOLD)
                    }
                )
            }

            if (esAdmin) {
                fila.addView(
                    MaterialButton(this).apply {
                        text = "Opciones"
                        isAllCaps = false
                        setTextColor(getColor(R.color.app_text_primary))
                        backgroundTintList = ColorStateList.valueOf(getColor(R.color.app_surface_elevated))
                        setOnClickListener { mostrarOpcionesProducto(producto) }
                    }
                )
            }

            val margen = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 18)
            }

            binding.contenedorProductos.addView(fila, margen)
        }
    }

    private fun mostrarOpcionesProducto(producto: Producto) {
        val opcionesActivas = arrayOf(
            "Registrar entrada",
            "Registrar salida",
            "Ajustar stock",
            "Editar producto",
            "Desactivar producto"
        )

        val opcionesInactivas = arrayOf(
            "Editar producto",
            "Reactivar producto"
        )

        val opciones = if (producto.estado) opcionesActivas else opcionesInactivas

        AlertDialog.Builder(this)
            .setTitle(producto.nombre)
            .setItems(opciones) { _, posicion ->
                when (opciones[posicion]) {
                    "Registrar entrada" -> mostrarDialogoStock(producto, TipoAccionStock.ENTRADA)
                    "Registrar salida" -> mostrarDialogoStock(producto, TipoAccionStock.SALIDA)
                    "Ajustar stock" -> mostrarDialogoStock(producto, TipoAccionStock.AJUSTE)
                    "Editar producto" -> mostrarFormularioProducto(producto)
                    "Desactivar producto" -> confirmarDesactivarProducto(producto)
                    "Reactivar producto" -> cambiarEstadoProducto(producto, true)
                }
            }
            .show()
    }

    private fun crearFondoProducto(producto: Producto): GradientDrawable {
        val colorFondo = if (producto.stockActual <= producto.stockMinimo) {
            getColor(R.color.app_surface_elevated)
        } else {
            getColor(R.color.app_surface)
        }

        return GradientDrawable().apply {
            setColor(colorFondo)
            setStroke(1, getColor(R.color.app_border_strong))
            cornerRadius = 10f
        }
    }

    private fun mostrarFormularioProducto(producto: Producto?) {
        if (categorias.isEmpty()) {
            mostrarMensaje("Primero crea una categoria activa.")
            return
        }

        val formulario = layoutInflater.inflate(R.layout.dialog_producto, null)
        val editNombre = formulario.findViewById<TextInputEditText>(R.id.editTextNombreProducto)
        val editCodigo = formulario.findViewById<TextInputEditText>(R.id.editTextCodigoProducto)
        val spinnerCategoria = formulario.findViewById<Spinner>(R.id.spinnerCategoriaProducto)
        val editDescripcion = formulario.findViewById<TextInputEditText>(R.id.editTextDescripcionProducto)
        val editPrecio = formulario.findViewById<TextInputEditText>(R.id.editTextPrecioProducto)
        val editStockActual = formulario.findViewById<TextInputEditText>(R.id.editTextStockActualProducto)
        val editStockMinimo = formulario.findViewById<TextInputEditText>(R.id.editTextStockMinimoProducto)
        val switchEstado = formulario.findViewById<MaterialSwitch>(R.id.switchEstadoProducto)

        val nombresCategorias = categorias.map { it.nombre }
        spinnerCategoria.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            nombresCategorias
        )

        editNombre.setText(producto?.nombre ?: "")
        editCodigo.setText(producto?.codigo ?: "")
        editDescripcion.setText(producto?.descripcion ?: "")
        editPrecio.setText(producto?.precio?.toString() ?: "")
        editStockActual.setText(producto?.stockActual?.toString() ?: "")
        editStockMinimo.setText(producto?.stockMinimo?.toString() ?: "")
        switchEstado.isChecked = producto?.estado ?: true

        val categoriaSeleccionada = categorias.indexOfFirst { it.id == producto?.categoriaId }
        if (categoriaSeleccionada >= 0) {
            spinnerCategoria.setSelection(categoriaSeleccionada)
        }

        val titulo = if (producto == null) "Nuevo producto" else "Editar producto"

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(formulario)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        guardarProducto(
                            producto = producto,
                            nombre = editNombre.text.toString().trim(),
                            codigo = editCodigo.text.toString().trim(),
                            categoria = categorias[spinnerCategoria.selectedItemPosition],
                            descripcion = editDescripcion.text.toString().trim(),
                            precioTexto = editPrecio.text.toString().trim(),
                            stockActualTexto = editStockActual.text.toString().trim(),
                            stockMinimoTexto = editStockMinimo.text.toString().trim(),
                            estado = switchEstado.isChecked,
                            dialog = this
                        )
                    }
                }
                show()
            }
    }

    private fun guardarProducto(
        producto: Producto?,
        nombre: String,
        codigo: String,
        categoria: Categoria,
        descripcion: String,
        precioTexto: String,
        stockActualTexto: String,
        stockMinimoTexto: String,
        estado: Boolean,
        dialog: AlertDialog
    ) {
        if (nombre.isEmpty()) {
            mostrarMensaje("El nombre del producto es requerido.")
            return
        }

        if (codigo.isEmpty()) {
            mostrarMensaje("El codigo del producto es requerido.")
            return
        }

        val precio = precioTexto.toDoubleOrNull()
        if (precio == null || precio < 0) {
            mostrarMensaje("El precio debe ser un numero mayor o igual a 0.")
            return
        }

        val stockActual = stockActualTexto.toIntOrNull()
        if (stockActual == null || stockActual < 0) {
            mostrarMensaje("El stock actual debe ser un entero mayor o igual a 0.")
            return
        }

        val stockMinimo = stockMinimoTexto.toIntOrNull()
        if (stockMinimo == null || stockMinimo < 0) {
            mostrarMensaje("El stock minimo debe ser un entero mayor o igual a 0.")
            return
        }

        val datos = hashMapOf<String, Any>(
            "nombre" to nombre,
            "codigo" to codigo,
            "categoriaId" to categoria.id,
            "descripcion" to descripcion,
            "precio" to precio,
            "stockActual" to stockActual,
            "stockMinimo" to stockMinimo,
            "estado" to estado
        )

        val referencia = if (producto == null) {
            datos["fechaRegistro"] = FieldValue.serverTimestamp()
            datos["creadoPor"] = auth.currentUser?.uid ?: ""
            db.collection("negocios").document(negocioId).collection("productos").document()
        } else {
            db.collection("negocios").document(negocioId).collection("productos").document(producto.id)
        }

        referencia.set(datos, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                dialog.dismiss()
                mostrarMensaje("Producto guardado.")
                cargarCategoriasYProductos()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo guardar el producto.")
            }
    }

    private fun confirmarDesactivarProducto(producto: Producto) {
        AlertDialog.Builder(this)
            .setTitle("Desactivar producto")
            .setMessage("El producto ya no aparecera en las listas activas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Desactivar") { _, _ ->
                desactivarProducto(producto)
            }
            .show()
    }

    private fun desactivarProducto(producto: Producto) {
        cambiarEstadoProducto(producto, false)
    }

    private fun cambiarEstadoProducto(producto: Producto, estado: Boolean) {
        db.collection("negocios")
            .document(negocioId)
            .collection("productos")
            .document(producto.id)
            .update("estado", estado)
            .addOnSuccessListener {
                val mensaje = if (estado) "Producto reactivado." else "Producto desactivado."
                mostrarMensaje(mensaje)
                cargarProductos()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo actualizar el estado del producto.")
            }
    }

    private fun mostrarDialogoStock(producto: Producto, tipoAccion: TipoAccionStock) {
        val formulario = layoutInflater.inflate(R.layout.dialog_accion_stock, null)
        val textProducto = formulario.findViewById<TextView>(R.id.textProductoStock)
        val editCantidad = formulario.findViewById<TextInputEditText>(R.id.editTextCantidadStock)
        val spinnerMotivo = formulario.findViewById<Spinner>(R.id.spinnerMotivoStock)
        val editObservaciones = formulario.findViewById<TextInputEditText>(R.id.editTextObservacionesStock)

        textProducto.text = "${producto.nombre}\nStock actual: ${producto.stockActual}"

        val motivos = motivosParaAccion(tipoAccion)
        spinnerMotivo.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            motivos
        )

        val titulo = when (tipoAccion) {
            TipoAccionStock.ENTRADA -> "Registrar entrada"
            TipoAccionStock.SALIDA -> "Registrar salida"
            TipoAccionStock.AJUSTE -> "Ajustar stock"
        }

        editCantidad.hint = if (tipoAccion == TipoAccionStock.AJUSTE) {
            "Nuevo stock real"
        } else {
            "Cantidad"
        }

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(formulario)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        guardarAccionStock(
                            producto = producto,
                            tipoAccion = tipoAccion,
                            cantidadTexto = editCantidad.text.toString().trim(),
                            motivo = motivos[spinnerMotivo.selectedItemPosition],
                            observaciones = editObservaciones.text.toString().trim(),
                            dialog = this
                        )
                    }
                }
                show()
            }
    }

    private fun guardarAccionStock(
        producto: Producto,
        tipoAccion: TipoAccionStock,
        cantidadTexto: String,
        motivo: String,
        observaciones: String,
        dialog: AlertDialog
    ) {
        val cantidadIngresada = cantidadTexto.toIntOrNull()
        if (cantidadIngresada == null || cantidadIngresada < 0) {
            mostrarMensaje("Ingresa un numero valido.")
            return
        }

        if (tipoAccion != TipoAccionStock.AJUSTE && cantidadIngresada == 0) {
            mostrarMensaje("La cantidad debe ser mayor a 0.")
            return
        }

        if (tipoAccion == TipoAccionStock.AJUSTE && cantidadIngresada < 0) {
            mostrarMensaje("El nuevo stock debe ser mayor o igual a 0.")
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            mostrarMensaje("No hay una sesion activa.")
            return
        }

        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { usuarioDoc ->
                val nombreUsuario = usuarioDoc.getString("nombre")
                    ?: auth.currentUser?.displayName
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "Usuario"

                ejecutarTransaccionStock(
                    producto = producto,
                    tipoAccion = tipoAccion,
                    cantidadIngresada = cantidadIngresada,
                    motivo = motivo,
                    observaciones = observaciones,
                    uid = uid,
                    nombreUsuario = nombreUsuario,
                    dialog = dialog
                )
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo cargar el usuario.")
            }
    }

    private fun ejecutarTransaccionStock(
        producto: Producto,
        tipoAccion: TipoAccionStock,
        cantidadIngresada: Int,
        motivo: String,
        observaciones: String,
        uid: String,
        nombreUsuario: String,
        dialog: AlertDialog
    ) {
        val productoRef = db.collection("negocios")
            .document(negocioId)
            .collection("productos")
            .document(producto.id)

        val movimientoRef = db.collection("negocios")
            .document(negocioId)
            .collection("movimientos")
            .document()

        db.runTransaction { transaction ->
            val productoDoc = transaction.get(productoRef)
            val estado = productoDoc.getBoolean("estado") ?: false
            val stockAnterior = productoDoc.getLong("stockActual")?.toInt() ?: 0

            if (!productoDoc.exists() || !estado) {
                throw Exception("El producto ya no esta activo.")
            }

            val stockNuevo = when (tipoAccion) {
                TipoAccionStock.ENTRADA -> stockAnterior + cantidadIngresada
                TipoAccionStock.SALIDA -> {
                    if (cantidadIngresada > stockAnterior) {
                        throw Exception("La cantidad supera el stock disponible.")
                    }
                    stockAnterior - cantidadIngresada
                }
                TipoAccionStock.AJUSTE -> cantidadIngresada
            }

            val cantidadMovimiento = when (tipoAccion) {
                TipoAccionStock.AJUSTE -> abs(stockNuevo - stockAnterior)
                else -> cantidadIngresada
            }

            val movimientoData = hashMapOf<String, Any>(
                "idProducto" to producto.id,
                "nombreProducto" to producto.nombre,
                "tipo" to tipoMovimiento(tipoAccion),
                "motivo" to motivo,
                "cantidad" to cantidadMovimiento,
                "stockAnterior" to stockAnterior,
                "stockNuevo" to stockNuevo,
                "idUsuario" to uid,
                "nombreUsuario" to nombreUsuario,
                "referencia" to referenciaMovimiento(tipoAccion),
                "fecha" to FieldValue.serverTimestamp(),
                "observaciones" to observaciones
            )

            transaction.update(productoRef, "stockActual", stockNuevo)
            transaction.set(movimientoRef, movimientoData)
        }.addOnSuccessListener {
            dialog.dismiss()
            mostrarMensaje("Stock actualizado correctamente.")
            cargarProductos()
        }.addOnFailureListener { error ->
            mostrarMensaje(error.message ?: "No se pudo actualizar el stock.")
        }
    }

    private fun motivosParaAccion(tipoAccion: TipoAccionStock): List<String> {
        return when (tipoAccion) {
            TipoAccionStock.ENTRADA -> listOf(
                "compra",
                "devolucion",
                "inventario_inicial",
                "ajuste_manual",
                "otro"
            )
            TipoAccionStock.SALIDA -> listOf(
                "producto_dañado",
                "uso_interno",
                "perdida",
                "ajuste_manual",
                "otro"
            )
            TipoAccionStock.AJUSTE -> listOf(
                "ajuste_manual",
                "conteo_fisico",
                "correccion"
            )
        }
    }

    private fun tipoMovimiento(tipoAccion: TipoAccionStock): String {
        return when (tipoAccion) {
            TipoAccionStock.ENTRADA -> "entrada"
            TipoAccionStock.SALIDA -> "salida"
            TipoAccionStock.AJUSTE -> "ajuste"
        }
    }

    private fun referenciaMovimiento(tipoAccion: TipoAccionStock): String {
        return when (tipoAccion) {
            TipoAccionStock.ENTRADA -> "entrada_manual"
            TipoAccionStock.SALIDA -> "salida_manual"
            TipoAccionStock.AJUSTE -> "ajuste_stock"
        }
    }

    private fun obtenerNombreCategoria(categoriaId: String): String {
        return categorias.firstOrNull { it.id == categoriaId }?.nombre ?: categoriaId
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_ES_ADMIN = "extra_es_admin"
        const val EXTRA_NEGOCIO_ID = "extra_negocio_id"
    }

    private enum class TipoAccionStock {
        ENTRADA,
        SALIDA,
        AJUSTE
    }
}
