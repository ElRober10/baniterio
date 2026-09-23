package com.baniterio.app.data

import com.baniterio.app.data.dto.AprobarResponse
import com.baniterio.app.data.dto.MiembroResumen
import com.baniterio.app.data.dto.PaginaLogsDto
import com.baniterio.app.data.dto.SolicitudEventoResumen
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

    /**
     * Cuántas cosas sin atender tiene el usuario en cada área del panel a la que
     * puede acceder. La clave es el área (`ADMIN_SOLICITUDES`, …); solo vienen las
     * áreas con al menos un pendiente. Mapa vacío si no hay nada o no tiene áreas.
     */
    suspend fun pendientesPorArea(): ResultadoAdmin<Map<String, Int>>

    /**
     * Solicitudes de la sección Eventos (crear un evento / borrar uno propio) que
     * un miembro normal ha pedido y un administrador de verdad tiene que resolver.
     */
    suspend fun solicitudesEvento(estado: String = "PENDIENTE"): ResultadoAdmin<List<SolicitudEventoResumen>>
    suspend fun aprobarSolicitudEvento(id: Long): ResultadoAdmin<Unit>
    suspend fun rechazarSolicitudEvento(id: Long, motivo: String?): ResultadoAdmin<Unit>

    /** Registro de eventos y errores (`GET /api/v1/logs`, ver spec 2026-09-23). Solo administrador. */
    suspend fun logs(usuarioId: Long? = null, pagina: Int = 0, tamano: Int = 50): ResultadoAdmin<PaginaLogsDto>
}
