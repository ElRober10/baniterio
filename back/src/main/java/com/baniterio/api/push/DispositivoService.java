package com.baniterio.api.push;

import java.util.Collection;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, baja y poda de {@link Dispositivo}. Un token identifica una instalación
 * de la app: si al registrarlo ya existe, se reasigna a quien lo registra ahora
 * (otra persona ha iniciado sesión en ese móvil).
 */
@Service
public class DispositivoService {

    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;

    public DispositivoService(DispositivoRepository dispositivos, UsuarioRepository usuarios) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
    }

    @Transactional
    public void registrar(Long usuarioId, String token, PlataformaDispositivo plataforma) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("usuario " + usuarioId + " no existe"));
        Dispositivo d = dispositivos.findByToken(token).orElseGet(Dispositivo::new);
        d.setUsuario(usuario);
        d.setToken(token);
        d.setPlataforma(plataforma);
        dispositivos.save(d);
    }

    @Transactional
    public void darDeBaja(Long usuarioId, String token) {
        dispositivos.findByToken(token)
                .filter(d -> d.getUsuario().getId().equals(usuarioId))
                .ifPresent(d -> dispositivos.deleteByToken(token));
    }

    /** Borra tokens que el proveedor de push ha marcado como muertos. */
    @Transactional
    public void podar(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            dispositivos.deleteByTokenIn(tokens);
        }
    }
}
