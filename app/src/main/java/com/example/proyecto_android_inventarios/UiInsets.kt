package com.example.proyecto_android_inventarios

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Evita que la UI quede debajo de la barra de estado o de navegacion.
 * Es importante con targetSdk reciente, donde Android usa mas edge-to-edge.
 */
fun AppCompatActivity.aplicarEspacioBarrasSistema() {
    val content = findViewById<ViewGroup>(android.R.id.content)
    val root = content.getChildAt(0) ?: return

    val left = root.paddingLeft
    val top = root.paddingTop
    val right = root.paddingRight
    val bottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(left, top + bars.top, right, bottom + bars.bottom)
        insets
    }

    ViewCompat.requestApplyInsets(root)
}
