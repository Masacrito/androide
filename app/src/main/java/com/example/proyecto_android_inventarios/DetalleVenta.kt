package com.example.proyecto_android_inventarios

data class DetalleVenta(
    val id: String,
    val nombreProducto: String,
    val cantidad: Int,
    val precioUnitario: Double,
    val subtotal: Double
)
