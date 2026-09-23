package com.baniterio.api.logs;

import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.identidad.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guarda las filas del registro de eventos. Nunca deja que un fallo suyo llegue a quien lo llama. */
@Service
public class LogEventoService {

    private static final Logger log = LoggerFactory.getLogger(LogEventoService.class);

    private final LogEventoRepository repo;
    private final PenaPilotoService pena;
    private final UsuarioRepository usuarios;

    public LogEventoService(LogEventoRepository repo, PenaPilotoService pena, UsuarioRepository usuarios) {
        this.repo = repo;
        this.pena = pena;
        this.usuarios = usuarios;
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
}
