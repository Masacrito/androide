package com.example.proyecto_android_inventarios

import com.google.firebase.Timestamp

data class MovimientoInventario(
    val id: String,
    val nombreProducto: String,
    val tipo: String,
    val motivo: String,
    val cantidad: Int,
    val stockAnterior: Int,
    val stockNuevo: Int,
    val nombreUsuario: String,
    val fecha: Timestamp?,
    val observaciones: String
)
