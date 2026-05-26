package com.example.proyecto_android_inventarios

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityHomeNegocioBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeNegocioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeNegocioBinding

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var negocioId: String = ""
    private var nombreUsuario: String = "Usuario"
    private val formatoFecha = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeNegocioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        negocioId = intent.getStringExtra(EXTRA_NEGOCIO_ID) ?: ""

        binding.buttonNuevaVenta.setOnClickListener { abrirNuevaVenta() }
        binding.buttonPrimeraVenta.setOnClickListener { abrirNuevaVenta() }
        binding.buttonHistorialVentas.setOnClickListener { abrirHistorialVentas() }
        binding.buttonMovimientos.setOnClickListener { abrirMovimientos() }
        binding.buttonCategorias.setOnClickListener { abrirCategorias() }
        binding.buttonProductos.setOnClickListener { abrirProductos() }
        binding.buttonReportes.setOnClickListener { abrirReportes() }
        binding.buttonCerrarSesion.setOnClickListener { cerrarSesion() }

        cargarUsuarioActual()
    }

    override fun onResume() {
        super.onResume()
        if (negocioId.isNotBlank()) {
            cargarMetricasDashboard()
        }
    }

    private fun cargarUsuarioActual() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            cerrarSesion()
            return
        }

        db.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->
                nombreUsuario = documento.getString("nombre")
                    ?: auth.currentUser?.displayName
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "Usuario"

                if (negocioId.isBlank()) {
                    negocioId = documento.getString("negocioActivoId") ?: ""
                }

                if (negocioId.isBlank()) {
                    abrirCrearNegocio()
                    return@addOnSuccessListener
                }

                configurarHeaderUsuario()
                cargarDatosNegocio()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo cargar el usuario.")
            }
    }

    private fun configurarHeaderUsuario() {
        binding.textSaludo.text = "Hola, $nombreUsuario"
        binding.textAvatar.text = nombreUsuario.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    private fun cargarDatosNegocio() {
        db.collection("negocios")
            .document(negocioId)
            .get()
            .addOnSuccessListener { documento ->
                binding.textNombreNegocio.text = documento.getString("nombre") ?: "Mi negocio"
                cargarMetricasDashboard()
            }
            .addOnFailureListener { error ->
                mostrarMensaje(error.message ?: "No se pudo cargar el negocio.")
            }
    }

    private fun cargarMetricasDashboard() {
        cargarVentasHoy()
        cargarProductosStockBajo()
        cargarVentasRecientes()
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
                binding.textVendidoHoy.text = "$${formatoDinero(total)}"
                binding.textVentasHoy.text = resultado.size().toString()
            }
            .addOnFailureListener {
                binding.textVendidoHoy.text = "$0.00"
                binding.textVentasHoy.text = "0"
            }
    }

    private fun cargarProductosStockBajo() {
        db.collection("negocios")
            .document(negocioId)
            .collection("productos")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { resultado ->
                val stockBajo = resultado.documents.count { documento ->
                    val stockActual = documento.getLong("stockActual")?.toInt() ?: 0
                    val stockMinimo = documento.getLong("stockMinimo")?.toInt() ?: 0
                    stockActual <= stockMinimo
                }
                binding.textStockBajo.text = stockBajo.toString()
            }
            .addOnFailureListener {
                binding.textStockBajo.text = "0"
            }
    }

    private fun cargarVentasRecientes() {
        db.collection("negocios")
            .document(negocioId)
            .collection("ventas")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { resultado ->
                binding.contenedorVentasRecientes.removeAllViews()

                if (resultado.isEmpty) {
                    binding.layoutVentasVacias.visibility = android.view.View.VISIBLE
                    return@addOnSuccessListener
                }

                binding.layoutVentasVacias.visibility = android.view.View.GONE

                for (documento in resultado.documents) {
                    val venta = Venta(
                        id = documento.id,
                        folio = documento.getString("folio") ?: "",
                        fecha = documento.getTimestamp("fecha"),
                        total = documento.getDouble("total") ?: 0.0,
                        metodoPago = documento.getString("metodoPago") ?: "",
                        estado = documento.getString("estado") ?: ""
                    )
                    binding.contenedorVentasRecientes.addView(crearVistaVentaReciente(venta))
                }
            }
            .addOnFailureListener {
                binding.contenedorVentasRecientes.removeAllViews()
                binding.layoutVentasVacias.visibility = android.view.View.VISIBLE
            }
    }

    private fun crearVistaVentaReciente(venta: Venta): LinearLayout {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
            setOnClickListener { abrirDetalleVenta(venta.id) }
        }

        val titulo = TextView(this).apply {
            text = venta.folio
            setTextColor(getColor(R.color.app_text_primary))
            textSize = 14f
            setTypeface(typeface, Typeface.NORMAL)
        }

        val datos = TextView(this).apply {
            text = "${formatearFecha(venta.fecha)} - ${venta.metodoPago} - $${formatoDinero(venta.total)}"
            setTextColor(getColor(R.color.app_text_secondary))
            textSize = 13f
        }

        fila.addView(titulo)
        fila.addView(datos)

        fila.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 10)
        }

        return fila
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

    private fun formatearFecha(timestamp: Timestamp?): String {
        return timestamp?.toDate()?.let { formatoFecha.format(it) } ?: "Sin fecha"
    }

    private fun formatoDinero(valor: Double): String {
        return String.format(Locale.getDefault(), "%.2f", valor)
    }

    private fun abrirCategorias() {
        val intent = Intent(this, CategoriasActivity::class.java)
        intent.putExtra(CategoriasActivity.EXTRA_NEGOCIO_ID, negocioId)
        startActivity(intent)
    }

    private fun abrirProductos() {
        val intent = Intent(this, ProductosActivity::class.java)
        intent.putExtra(ProductosActivity.EXTRA_NEGOCIO_ID, negocioId)
        intent.putExtra(ProductosActivity.EXTRA_ES_ADMIN, true)
        startActivity(intent)
    }

    private fun abrirNuevaVenta() {
        val intent = Intent(this, NuevaVentaActivity::class.java)
        intent.putExtra(NuevaVentaActivity.EXTRA_NEGOCIO_ID, negocioId)
        startActivity(intent)
    }

    private fun abrirHistorialVentas() {
        val intent = Intent(this, HistorialVentasActivity::class.java)
        intent.putExtra(HistorialVentasActivity.EXTRA_NEGOCIO_ID, negocioId)
        startActivity(intent)
    }

    private fun abrirDetalleVenta(idVenta: String) {
        val intent = Intent(this, DetalleVentaActivity::class.java)
        intent.putExtra(DetalleVentaActivity.EXTRA_NEGOCIO_ID, negocioId)
        intent.putExtra(DetalleVentaActivity.EXTRA_VENTA_ID, idVenta)
        startActivity(intent)
    }

    private fun abrirMovimientos() {
        startActivity(Intent(this, MovimientosActivity::class.java))
    }

    private fun abrirReportes() {
        val intent = Intent(this, ReportesActivity::class.java)
        intent.putExtra(ReportesActivity.EXTRA_NEGOCIO_ID, negocioId)
        startActivity(intent)
    }

    private fun abrirCrearNegocio() {
        val intent = Intent(this, CrearNegocioActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun cerrarSesion() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_NEGOCIO_ID = "extra_negocio_id"
    }
}
