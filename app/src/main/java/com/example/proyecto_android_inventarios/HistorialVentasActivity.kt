package com.example.proyecto_android_inventarios

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityHistorialVentasBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class HistorialVentasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialVentasBinding

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val ventas = mutableListOf<Venta>()

    private var negocioId: String = ""
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialVentasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""
        if (negocioId.isBlank()) {
            mostrarMensaje("No se recibio el negocio activo.")
            finish()
            return
        }

        binding.buttonVolver.setOnClickListener { finish() }
        cargarVentas()
    }

    override fun onResume() {
        super.onResume()
        if (negocioId.isNotBlank()) {
            cargarVentas()
        }
    }

    private fun cargarVentas() {
        binding.progressHistorial.visibility = View.VISIBLE

        db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { resultado ->
                binding.progressHistorial.visibility = View.GONE
                ventas.clear()

                for (documento in resultado.documents) {
                    ventas.add(
                        Venta(
                            id = documento.id,
                            folio = documento.getString("folio") ?: "",
                            fecha = documento.getTimestamp("fecha"),
                            total = documento.getDouble("total") ?: 0.0,
                            metodoPago = documento.getString("metodoPago") ?: "",
                            estado = documento.getString("estado") ?: ""
                        )
                    )
                }

                mostrarVentasEnPantalla()
            }
            .addOnFailureListener { error ->
                binding.progressHistorial.visibility = View.GONE
                mostrarMensaje(error.message ?: "No se pudieron cargar las ventas.")
            }
    }

    private fun mostrarVentasEnPantalla() {
        binding.contenedorVentas.removeAllViews()

        if (ventas.isEmpty()) {
            binding.textListaVacia.visibility = View.VISIBLE
            return
        }

        binding.textListaVacia.visibility = View.GONE

        for (venta in ventas) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 18, 0, 18)
                setOnClickListener { abrirDetalleVenta(venta.id) }
            }

            val folio = TextView(this).apply {
                text = venta.folio
                textSize = 18f
                setTextColor(getColor(R.color.app_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            }

            val datos = TextView(this).apply {
                text = "Fecha: ${formatearFecha(venta)}\n" +
                        "Total: $${formatoDinero(venta.total)}\n" +
                        "Pago: ${venta.metodoPago}\n" +
                        "Estado: ${venta.estado}"
                setTextColor(getColor(R.color.app_text_secondary))
            }

            fila.addView(folio)
            fila.addView(datos)
            fila.setBackgroundResource(R.drawable.bg_card_dark)
            binding.contenedorVentas.addView(fila)
        }
    }

    private fun abrirDetalleVenta(idVenta: String) {
        val intent = Intent(this, DetalleVentaActivity::class.java)
        intent.putExtra(DetalleVentaActivity.EXTRA_NEGOCIO_ID, negocioId)
        intent.putExtra(DetalleVentaActivity.EXTRA_VENTA_ID, idVenta)
        startActivity(intent)
    }

    private fun formatearFecha(venta: Venta): String {
        val fecha = venta.fecha?.toDate()
        return if (fecha == null) "Sin fecha" else formatoFecha.format(fecha)
    }

    private fun formatoDinero(valor: Double): String {
        return String.format(Locale.getDefault(), "%.2f", valor)
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_NEGOCIO_ID = "extra_negocio_id"
    }
}
