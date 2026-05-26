package com.example.proyecto_android_inventarios

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.proyecto_android_inventarios.databinding.ActivityHomeUsuarioBinding
import com.google.firebase.auth.FirebaseAuth

class HomeUsuarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeUsuarioBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeUsuarioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacioBarrasSistema()

        binding.textCorreo.text = auth.currentUser?.email ?: "Sin correo"

        binding.buttonProductos.setOnClickListener {
            val intent = Intent(this, ProductosActivity::class.java)
            intent.putExtra(ProductosActivity.EXTRA_ES_ADMIN, false)
            startActivity(intent)
        }

        binding.buttonCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun cerrarSesion() {
        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
