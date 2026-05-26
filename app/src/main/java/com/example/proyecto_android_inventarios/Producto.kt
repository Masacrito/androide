package com.example.proyecto_android_inventarios

data class Producto(
    val id: String,
    val nombre: String,
    val codigo: String,
    val categoriaId: String,
    val descripcion: String,
    val precio: Double,
    val stockActual: Int,
    val stockMinimo: Int,
    val estado: Boolean
)
