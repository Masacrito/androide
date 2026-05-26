package com.example.proyecto_android_inventarios

import com.google.firebase.Timestamp

data class Venta(
    val id: String,
    val folio: String,
    val fecha: Timestamp?,
    val total: Double,
    val metodoPago: String,
    val estado: String
)
