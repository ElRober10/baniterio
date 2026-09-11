package com.baniterio.api.push;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduce una {@link Audiencia} a la lista de {@code usuario.id} que deben
 * recibir el aviso. Peña piloto: se resuelve por slug, igual que
 * {@code ServicioPermisos}.
 */
@Service
public class ResolutorAudiencia {

    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final PenaPilotoService pena;
    private final AsistenciaEventoRepository asistencias;

    public ResolutorAudiencia(MembresiaRepository membresias, UsuarioRepository usuarios,
                              PenaPilotoService pena, AsistenciaEventoRepository asistencias) {
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.pena = pena;
        this.asistencias = asistencias;
    }

    @Transactional(readOnly = true)
    public List<Long> resolver(Audiencia audiencia) {
        // En Java 17 el filtrado por tipo de una interfaz sellada se hace con
        // una cadena de instanceof; el pattern matching aún es preview.
        if (audiencia instanceof Audiencia.UsuarioUnico u) {
            return List.of(u.usuarioId());
        }
        if (audiencia instanceof Audiencia.TodaLaPena) {
            return activas().map(m -> m.getUsuario().getId()).distinct().toList();
        }
        if (audiencia instanceof Audiencia.Administradores) {
            return administradores();
        }
        if (audiencia instanceof Audiencia.SinRespuestaEvento s) {
            Set<Long> conRespuesta = asistencias.idsUsuariosConRespuesta(s.eventoId());
            return activas().map(m -> m.getUsuario().getId()).distinct()
                    .filter(id -> !conRespuesta.contains(id))
                    .toList();
        }
        throw new IllegalArgumentException("Audiencia no soportada: " + audiencia);
    }

    /**
     * Unión de administradores con membresía activa y superadministradores.
     * Un superadmin sin membresía activa también entra (red de seguridad, igual
     * que {@code ServicioPermisos.esAdministrador}): por eso se recorre además
     * {@code usuarios.findAll()} y no solo las membresías.
     */
    private List<Long> administradores() {
        Stream<Long> desdeMembresias = activas()
                .filter(m -> m.getRol() == RolMembresia.ADMIN || m.getUsuario().isEsSuperadmin())
                .map(m -> m.getUsuario().getId());
        Stream<Long> superadmins = usuarios.findAll().stream()
                .filter(Usuario::isEsSuperadmin)
                .map(Usuario::getId);
        return Stream.concat(desdeMembresias, superadmins).distinct().toList();
    }

    private Stream<Membresia> activas() {
        return membresias.findByPenaIdAndActivaTrue(pena.id()).stream();
    }
}
