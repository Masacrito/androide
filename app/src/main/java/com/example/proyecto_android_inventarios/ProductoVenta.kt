package com.example.proyecto_android_inventarios

data class ProductoVenta(
    val producto: Producto,
    var cantidad: Int
) {
    val subtotal: Double
        get() = producto.precio * cantidad
}
