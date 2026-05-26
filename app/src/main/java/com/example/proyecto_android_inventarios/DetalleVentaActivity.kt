package com.example.proyecto_android_inventarios

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityDetalleVentaBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class DetalleVentaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleVentaBinding

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val detalles = mutableListOf<DetalleVenta>()

    private var negocioId: String = ""
    private var ventaId: String = ""
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleVentaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""
        ventaId = intent.getStringExtra(EXTRA_VENTA_ID) ?: ""

        if (negocioId.isBlank() || ventaId.isBlank()) {
            mostrarMensaje("No se recibio la venta correctamente.")
            finish()
            return
        }

        binding.buttonVolver.setOnClickListener { finish() }
        cargarVenta()
        cargarDetalle()
    }

    private fun cargarVenta() {
        binding.progressDetalleVenta.visibility = View.VISIBLE

        db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .document(ventaId)
            .get()
            .addOnSuccessListener { documento ->
                binding.progressDetalleVenta.visibility = View.GONE

                if (!documento.exists()) {
                    mostrarMensaje("La venta no existe.")
                    finish()
                    return@addOnSuccessListener
                }

                val fecha = documento.getTimestamp("fecha")?.toDate()
                val fechaTexto = if (fecha == null) "Sin fecha" else formatoFecha.format(fecha)
                val total = documento.getDouble("total") ?: 0.0

                binding.textFolio.text = documento.getString("folio") ?: "Venta"
                binding.textEncabezadoVenta.text =
                    "Fecha: $fechaTexto\n" +
                            "Metodo de pago: ${documento.getString("metodoPago") ?: ""}\n" +
                            "Total: $${formatoDinero(total)}\n" +
                            "Estado: ${documento.getString("estado") ?: ""}"
            }
            .addOnFailureListener { error ->
                binding.progressDetalleVenta.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudo cargar la venta.")
            }
    }

    private fun cargarDetalle() {
        db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .document(ventaId)
            .collection("detalle")
            .get()
            .addOnSuccessListener { resultado ->
                detalles.clear()

                for (documento in resultado.documents) {
                    detalles.add(
                        DetalleVenta(
                            id = documento.id,
                            nombreProducto = documento.getString("nombreProducto") ?: "",
                            cantidad = documento.getLong("cantidad")?.toInt() ?: 0,
                            precioUnitario = documento.getDouble("precioUnitario") ?: 0.0,
                            subtotal = documento.getDouble("subtotal") ?: 0.0
                        )
                    )
                }

                mostrarDetalleEnPantalla()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo cargar el detalle.")
            }
    }

    private fun mostrarDetalleEnPantalla() {
        binding.contenedorDetalle.removeAllViews()

        if (detalles.isEmpty()) {
            binding.textDetalleVacio.visibility = View.VISIBLE
            return
        }

        binding.textDetalleVacio.visibility = View.GONE

        for (detalle in detalles) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(R.drawable.bg_card_dark)
            }

            val nombre = TextView(this).apply {
                text = detalle.nombreProducto
                textSize = 17f
                setTextColor(getColor(R.color.app_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            }

            val datos = TextView(this).apply {
                text = "Cantidad: ${detalle.cantidad}\n" +
                        "Precio unitario: $${formatoDinero(detalle.precioUnitario)}\n" +
                        "Subtotal: $${formatoDinero(detalle.subtotal)}"
                textSize = 14f
                setTextColor(getColor(R.color.app_text_secondary))
            }

            fila.addView(nombre)
            fila.addView(datos)
            binding.contenedorDetalle.addView(fila, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
    }

    private fun formatoDinero(valor: Double): String {
        return String.format(Locale.getDefault(), "%.2f", valor)
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun dp(valor: Int): Int {
        return (valor * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_NEGOCIO_ID = "extra_negocio_id"
        const val EXTRA_VENTA_ID = "extra_venta_id"
    }
}
