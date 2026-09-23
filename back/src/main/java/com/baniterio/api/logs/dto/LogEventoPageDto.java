package com.baniterio.api.logs.dto;

import java.util.List;

public record LogEventoPageDto(List<LogEventoDto> contenido, long total, int pagina, int tamano) {
}
