package com.baniterio.api.admin;

/**
 * Evento de dominio que publica {@link AdminService} cuando una solicitud de
 * ingreso queda resuelta (aprobada o rechazada). Lo escucha
 * {@link ManejadorCorreoSolicitud} tras confirmar la transacción para enviar el
 * correo, de modo que un fallo de correo no revierta la resolución.
 *
 * @param email         destinatario (el de la solicitud)
 * @param nombre        nombre del solicitante, para personalizar el correo
 * @param aprobada      {@code true} si se aprobó, {@code false} si se rechazó
 * @param cuentaCreada  al aprobar, si además se creó ya el usuario (traía contraseña)
 * @param motivoRechazo texto del rechazo (solo cuando {@code aprobada == false})
 */
public record SolicitudResueltaEvent(
        String email, String nombre, boolean aprobada, boolean cuentaCreada, String motivoRechazo) {
}
