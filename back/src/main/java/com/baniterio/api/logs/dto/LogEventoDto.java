package com.baniterio.api.logs.dto;

import java.time.Instant;

/** Fila del listado `GET /api/v1/logs`. `usuarioNombre` viene ya resuelto: nadie más hace el join. */
public record LogEventoDto(long id, String origen, Long usuarioId, String usuarioNombre, String metodo,
        String ruta, Integer estado, String codigoError, String mensaje, Instant creadoEn) {
}
