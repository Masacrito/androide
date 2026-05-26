package com.example.proyecto_android_inventarios

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object AuthFlow {

    fun verificarUsuarioEnFirestore(
        activity: Activity,
        nombreRegistro: String? = null,
        onError: (() -> Unit)? = null
    ) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val user = auth.currentUser

        if (user == null) {
            onError?.invoke()
            Toast.makeText(activity, "No hay usuario autenticado.", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("usuarios")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val estado = document.getBoolean("estado") ?: false
                    val negocioActivoId = document.getString("negocioActivoId")

                    if (!estado) {
                        auth.signOut()
                        onError?.invoke()
                        Toast.makeText(activity, "Usuario inactivo", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (negocioActivoId.isNullOrBlank()) {
                        abrirCrearNegocio(activity)
                    } else {
                        abrirHomeNegocio(activity, negocioActivoId)
                    }
                } else {
                    crearUsuarioFirestore(activity, user, nombreRegistro, onError)
                }
            }
            .addOnFailureListener { error ->
                onError?.invoke()
                Toast.makeText(
                    activity,
                    error.message ?: "No se pudo consultar el usuario.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun crearUsuarioFirestore(
        activity: Activity,
        user: FirebaseUser,
        nombreRegistro: String?,
        onError: (() -> Unit)?
    ) {
        val db = FirebaseFirestore.getInstance()
        val nombre = obtenerNombreInicial(user, nombreRegistro)
        val correo = user.email ?: ""

        val data = hashMapOf<String, Any?>(
            "uid" to user.uid,
            "nombre" to nombre,
            "correo" to correo,
            "estado" to true,
            "rolGlobal" to "usuario",
            "negocioActivoId" to null,
            "fechaRegistro" to FieldValue.serverTimestamp()
        )

        db.collection("usuarios")
            .document(user.uid)
            .set(data)
            .addOnSuccessListener {
                abrirCrearNegocio(activity)
            }
            .addOnFailureListener { error ->
                FirebaseAuth.getInstance().signOut()
                onError?.invoke()
                Toast.makeText(
                    activity,
                    error.message ?: "Error al crear perfil",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun obtenerNombreInicial(user: FirebaseUser, nombreRegistro: String?): String {
        if (!nombreRegistro.isNullOrBlank()) {
            return nombreRegistro
        }

        val displayName = user.displayName
        if (!displayName.isNullOrBlank()) {
            return displayName
        }

        val correo = user.email
        if (!correo.isNullOrBlank()) {
            return correo.substringBefore("@")
        }

        return "Usuario"
    }

    private fun abrirCrearNegocio(activity: Activity) {
        val intent = Intent(activity, CrearNegocioActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }

    private fun abrirHomeNegocio(activity: Activity, negocioId: String) {
        val intent = Intent(activity, HomeNegocioActivity::class.java)
        intent.putExtra(HomeNegocioActivity.EXTRA_NEGOCIO_ID, negocioId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }
}
