package com.example.proyecto_android_inventarios

data class Usuario(
    val uid: String,
    val nombre: String,
    val correo: String,
    val estado: Boolean,
    val rolGlobal: String,
    val negocioActivoId: String?
)
