package com.example.proyecto_android_inventarios

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityReportesBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReportesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportesBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var negocioId: String = ""
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""
        binding.buttonVolver.setOnClickListener { finish() }

        if (negocioId.isBlank()) {
            cargarNegocioActivo()
        } else {
            cargarReportes()
        }
    }

    private fun cargarNegocioActivo() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            mostrarMensaje("No hay una sesion activa.")
            finish()
            return
        }

        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->
                negocioId = documento.getString("negocioActivoId") ?: ""
                if (negocioId.isBlank()) {
                    mostrarMensaje("Primero crea un negocio.")
                    finish()
                } else {
                    cargarReportes()
                }
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo cargar el usuario.")
            }
    }

    private fun cargarReportes() {
        cargarVentasHoy()
        cargarVentasGenerales()
        cargarStockBajo()
        cargarUltimosMovimientos()
    }

    private fun cargarVentasHoy() {
        val (inicioDia, finDia) = rangoDiaActual()

        db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .whereGreaterThanOrEqualTo("fecha", inicioDia)
            .whereLessThan("fecha", finDia)
            .get()
            .addOnSuccessListener { resultado ->
                val total = resultado.documents.sumOf { it.getDouble("total") ?: 0.0 }
                binding.textTotalHoy.text = "$${formatoDinero(total)}"
                binding.textVentasHoy.text = resultado.size().toString()
            }
            .addOnFailureListener {
                binding.textTotalHoy.text = "$0.00"
                binding.textVentasHoy.text = "0"
            }
    }

    private fun cargarVentasGenerales() {
        db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .get()
            .addOnSuccessListener { resultado ->
                val total = resultado.documents.sumOf { it.getDouble("total") ?: 0.0 }
                binding.textTotalGeneral.text = "$${formatoDinero(total)}"
                binding.textVentasGeneral.text = resultado.size().toString()
            }
            .addOnFailureListener {
                binding.textTotalGeneral.text = "$0.00"
                binding.textVentasGeneral.text = "0"
            }
    }

    private fun cargarStockBajo() {
        db.collection("negocios")
            .document(negocioId)
            .collection("productos")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { resultado ->
                binding.contenedorStockBajo.removeAllViews()

                val productosBajos = resultado.documents.filter { documento ->
                    val stockActual = documento.getLong("stockActual")?.toInt() ?: 0
                    val stockMinimo = documento.getLong("stockMinimo")?.toInt() ?: 0
                    stockActual <= stockMinimo
                }

                if (productosBajos.isEmpty()) {
                    binding.contenedorStockBajo.addView(crearTextoVacio("No hay productos con stock bajo."))
                    return@addOnSuccessListener
                }

                for (documento in productosBajos) {
                    val nombre = documento.getString("nombre") ?: ""
                    val codigo = documento.getString("codigo") ?: ""
                    val stockActual = documento.getLong("stockActual")?.toInt() ?: 0
                    val stockMinimo = documento.getLong("stockMinimo")?.toInt() ?: 0
                    binding.contenedorStockBajo.addView(
                        crearFila(
                            titulo = nombre,
                            detalle = "Codigo: $codigo · Stock: $stockActual · Minimo: $stockMinimo"
                        )
                    )
                }
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo cargar stock bajo.")
            }
    }

    private fun cargarUltimosMovimientos() {
        db.collection("negocios")
            .document(negocioId)
            .collection("movimientos")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { resultado ->
                binding.contenedorMovimientos.removeAllViews()

                if (resultado.isEmpty) {
                    binding.contenedorMovimientos.addView(crearTextoVacio("Aun no hay movimientos."))
                    return@addOnSuccessListener
                }

                for (documento in resultado.documents) {
                    val nombreProducto = documento.getString("nombreProducto") ?: ""
                    val tipo = documento.getString("tipo") ?: ""
                    val motivo = documento.getString("motivo") ?: ""
                    val cantidad = documento.getLong("cantidad")?.toInt() ?: 0
                    val stockAnterior = documento.getLong("stockAnterior")?.toInt() ?: 0
                    val stockNuevo = documento.getLong("stockNuevo")?.toInt() ?: 0
                    val fecha = documento.getTimestamp("fecha")?.toDate()?.let { formatoFecha.format(it) } ?: "Sin fecha"

                    binding.contenedorMovimientos.addView(
                        crearFila(
                            titulo = nombreProducto,
                            detalle = "$tipo · $motivo · Cantidad: $cantidad\nStock: $stockAnterior a $stockNuevo · $fecha"
                        )
                    )
                }
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudieron cargar movimientos.")
            }
    }

    private fun crearFila(titulo: String, detalle: String): LinearLayout {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
        }

        val textTitulo = TextView(this).apply {
            text = titulo
            setTextColor(getColor(R.color.app_text_primary))
            textSize = 14f
            setTypeface(typeface, Typeface.NORMAL)
        }

        val textDetalle = TextView(this).apply {
            text = detalle
            setTextColor(getColor(R.color.app_text_secondary))
            textSize = 13f
        }

        fila.addView(textTitulo)
        fila.addView(textDetalle)
        fila.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 10)
        }

        return fila
    }

    private fun crearTextoVacio(texto: String): TextView {
        return TextView(this).apply {
            this.text = texto
            setTextColor(getColor(R.color.app_text_secondary))
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }
    }

    private fun rangoDiaActual(): Pair<Timestamp, Timestamp> {
        val inicio = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val fin = inicio.clone() as Calendar
        fin.add(Calendar.DAY_OF_MONTH, 1)

        return Timestamp(inicio.time) to Timestamp(fin.time)
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
