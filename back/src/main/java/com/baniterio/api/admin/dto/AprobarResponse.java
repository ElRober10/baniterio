package com.baniterio.api.admin.dto;

/**
 * Respuesta de {@code POST /api/v1/admin/solicitudes/{id}/aprobar}.
 * {@code resultado} vale {@code "CUENTA_CREADA"} (la solicitud traía contraseña y
 * se creó el usuario) o {@code "TELEFONO_AUTORIZADO"} (solo se autorizó el
 * teléfono; el solicitante aún debe completar el registro).
 */
public record AprobarResponse(String resultado) {
}
