package com.baniterio.app.data

import com.baniterio.app.data.dto.AprobarResponse
import com.baniterio.app.data.dto.MiembroResumen
import com.baniterio.app.data.dto.SolicitudResumen

/**
 * Acceso a los endpoints de administración de la peña (rutas bajo `/api/v1/admin/`).
 *
 * Todas las llamadas requieren un usuario autenticado con rol de administrador:
 * la implementación adjunta la cabecera `Authorization: Bearer <token>` leyendo
 * el token de [SesionHolder] (rellenado por [AuthRepositoryImpl] al hacer login).
 *
 * Cada método devuelve un [ResultadoAdmin]: `Exito` con el dato, o `Error` con un
 * [CodigoErrorAdmin] ya traducido del `codigo` que manda el backend (o
 * `SIN_CONEXION` ante fallos de red / timeout / serialización). Nunca lanza por
 * errores HTTP esperados; sí relanza `CancellationException`.
 */
interface AdminRepository {
    suspend fun solicitudes(estado: String = "PENDIENTE"): ResultadoAdmin<List<SolicitudResumen>>
    suspend fun aprobar(id: Long): ResultadoAdmin<AprobarResponse>
    suspend fun rechazar(id: Long, motivo: String?): ResultadoAdmin<Unit>
    suspend fun miembros(): ResultadoAdmin<List<MiembroResumen>>
    suspend fun cambiarRol(id: Long, rol: String): ResultadoAdmin<Unit>
    suspend fun cambiarActivo(id: Long, activo: Boolean): ResultadoAdmin<Unit>
    suspend fun cambiarAreas(id: Long, areas: List<String>): ResultadoAdmin<Unit>
}
