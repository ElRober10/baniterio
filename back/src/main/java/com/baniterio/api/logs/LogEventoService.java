package com.baniterio.api.logs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.logs.dto.LogEventoDto;
import com.baniterio.api.logs.dto.LogEventoPageDto;
import com.baniterio.api.identidad.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guarda las filas del registro de eventos. Nunca deja que un fallo suyo llegue a quien lo llama. */
@Service
public class LogEventoService {

    private static final Logger log = LoggerFactory.getLogger(LogEventoService.class);

    private final LogEventoRepository repo;
    private final PenaPilotoService pena;
    private final UsuarioRepository usuarios;
    private final ServicioPermisos permisos;

    public LogEventoService(LogEventoRepository repo, PenaPilotoService pena, UsuarioRepository usuarios,
            ServicioPermisos permisos) {
        this.repo = repo;
        this.pena = pena;
        this.usuarios = usuarios;
        this.permisos = permisos;
    }

    /** Lo llama {@link LogEventoFilter} tras cada petición de escritura de la API. */
    @Transactional
    public void registrarAccion(Long usuarioId, String metodo, String ruta, int estado, String codigoError) {
        try {
            LogEvento fila = LogEvento.builder()
                    .pena(pena.entidad())
                    .usuario(usuarioId == null ? null : usuarios.getReferenceById(usuarioId))
                    .origen(OrigenLog.BACKEND)
                    .metodo(metodo)
                    .ruta(ruta)
                    .estado(estado)
                    .codigoError(codigoError)
                    .build();
            repo.save(fila);
        } catch (Exception e) {
            log.warn("no se pudo registrar el log de {} {}", metodo, ruta, e);
        }
    }

    /** Lo llama LogEventoController ante un error de móvil/web que nunca llegó a golpear el backend. */
    @Transactional
    public void registrarCliente(Long usuarioId, String origenTexto, String pantalla, String mensaje) {
        try {
            OrigenLog origen = "WEB".equalsIgnoreCase(origenTexto) ? OrigenLog.WEB : OrigenLog.MOBILE;
            LogEvento fila = LogEvento.builder()
                    .pena(pena.entidad())
                    .usuario(usuarioId == null ? null : usuarios.getReferenceById(usuarioId))
                    .origen(origen)
                    .ruta(pantalla)
                    .mensaje(mensaje)
                    .build();
            repo.save(fila);
        } catch (Exception e) {
            log.warn("no se pudo registrar el log de cliente ({}): {}", pantalla, mensaje, e);
        }
    }

    /** Consulta paginada para la pantalla de admin. Solo administradores de verdad. */
    @Transactional(readOnly = true)
    public LogEventoPageDto listar(Long usuarioIdSolicitante, Long filtroUsuarioId, String origenTexto,
            LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (usuarioIdSolicitante == null || !permisos.esAdministrador(usuarioIdSolicitante)) {
            throw new SinPermisoException();
        }
        // Un origen que no reconoce, o pagina/tamano fuera de rango, se acotan en
        // vez de reventar con un 500: es una herramienta de depuración para un
        // admin, no una API pública que deba rechazar la entrada con un 400.
        OrigenLog origen = null;
        if (origenTexto != null) {
            try {
                origen = OrigenLog.valueOf(origenTexto.toUpperCase());
            } catch (IllegalArgumentException ignorado) {
                origen = null;
            }
        }
        Instant desdeInstant = desde == null ? null : desde.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hastaInstant = hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 200);

        Page<LogEvento> resultado = repo.buscar(pena.id(), filtroUsuarioId, origen, desdeInstant, hastaInstant,
                PageRequest.of(paginaSegura, tamanoSeguro));
        List<LogEventoDto> contenido = resultado.getContent().stream().map(this::aDto).toList();
        return new LogEventoPageDto(contenido, resultado.getTotalElements(), paginaSegura, tamanoSeguro);
    }

    private LogEventoDto aDto(LogEvento l) {
        return new LogEventoDto(l.getId(), l.getOrigen().name(),
                l.getUsuario() == null ? null : l.getUsuario().getId(),
                l.getUsuario() == null ? null
                        : l.getUsuario().getNombre() + " " + l.getUsuario().getApellidos(),
                l.getMetodo(), l.getRuta(), l.getEstado(), l.getCodigoError(), l.getMensaje(),
                l.getCreadoEn());
    }
}
