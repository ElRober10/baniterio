package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Mi perfil (`GET /api/v1/perfil`, siempre 200). `imagenTipo` = "FOTO" | "AVATAR" | null. */
@Serializable
data class PerfilResponse(
    val usuarioId: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val sobreMi: String? = null,
    val imagenTipo: String? = null,
    val imagenRef: String? = null,
    val imagenUrl: String? = null,
    val completado: Boolean,
    val pareja: ParejaEnPerfil? = null,
    val hijos: List<HijoEnPerfil> = emptyList(),
    val vinculoPendiente: VinculoPendiente? = null,
)

/** El vínculo de pareja donde soy el solicitante (o el ACEPTADO donde soy la pareja). */
@Serializable
data class ParejaEnPerfil(
    val vinculoId: Long,
    val nombre: String,
    val telefono: String,
    /** SIN_CUENTA | PENDIENTE | ACEPTADO | RECHAZADO */
    val estado: String,
)

@Serializable
data class HijoEnPerfil(
    val id: Long,
    val nombre: String,
    val mayorDeEdad: Boolean,
    val telefono: String? = null,
    val visible: Boolean,
    val registrado: Boolean,
)

/** Otra persona declaró que somos pareja y espera mi confirmación. */
@Serializable
data class VinculoPendiente(val vinculoId: Long, val solicitanteNombre: String)

/** Cuerpo de `PUT /api/v1/perfil`: todo el estado del editor de una vez. */
@Serializable
data class GuardarPerfilRequest(
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val sobreMi: String? = null,
    /** "FOTO" | "AVATAR" (enum ImagenPerfil en el backend). */
    val imagenTipo: String,
    val imagenRef: String,
    val tienePareja: Boolean,
    val parejaNombre: String? = null,
    val parejaTelefono: String? = null,
    val hijos: List<HijoRequest> = emptyList(),
)

/** `id` nulo = alta; `id` presente = edición de una fila existente. */
@Serializable
data class HijoRequest(
    val id: Long? = null,
    val nombre: String,
    val mayorDeEdad: Boolean,
    val telefono: String? = null,
    val visible: Boolean,
)

@Serializable
data class AvatarResumen(val id: String, val genero: String)

@Serializable
data class SubirFotoResponse(val imagenRef: String)

/** Una tarjeta de la sección Miembros. Solo datos públicos: nunca teléfono ni email. */
@Serializable
data class TarjetaMiembroResponse(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val sobreMi: String? = null,
    val imagenUrl: String? = null,
    val parejaNombre: String? = null,
    val hijos: List<String> = emptyList(),
)
