package com.baniterio.api.admin;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baniterio.api.admin.dto.AprobarResponse;
import com.baniterio.api.admin.dto.MiembroResumen;
import com.baniterio.api.admin.dto.SolicitudResumen;
import com.baniterio.api.auth.RegistroConflictoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoArea;
import com.baniterio.api.identidad.PermisoAreaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.SolicitudIngreso;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Lógica del panel de administración relacionada con las solicitudes de ingreso:
 * listarlas, aprobarlas (autorizando el teléfono y, si la solicitud traía
 * contraseña, creando ya el usuario) y rechazarlas con un motivo.
 *
 * <p>{@link #aprobarSolicitud} y {@link #rechazarSolicitud} son
 * {@code @Transactional}: todos los cambios (usuario, membresía, teléfono,
 * estado de la solicitud) se confirman juntos o no se confirma ninguno. El
 * correo se manda DESPUÉS del commit, vía {@link SolicitudResueltaEvent} y
 * {@link ManejadorCorreoSolicitud}, para que un fallo de envío no revierta la
 * resolución.
 *
 * <p>El segundo bloque (gestión de miembros: {@link #cambiarRol},
 * {@link #cambiarActivo}, {@link #reemplazarAreas}) aplica las salvaguardas
 * contra bloqueo: nadie se degrada ni se desactiva a sí mismo, solo el
 * superadmin se toca a sí mismo, y nunca se queda la peña sin admin activo.
 */
@Service
public class AdminService {

    /** Peña piloto. Con el alcance de una sola peña, se resuelve por slug. */
    private static final String SLUG_PENA = "baniterio";

    /**
     * Texto de rechazo cuando quien administra no escribe un motivo propio
     * (spec §2). Neutro y sin acusaciones: la persona puede hablar con la peña.
     */
    static final String MOTIVO_RECHAZO_POR_DEFECTO =
            "No hemos podido confirmar tu vinculación con la peña, así que de momento no "
            + "podemos darte acceso. Si crees que es un error, habla con alguien de la peña.";

    private final SolicitudIngresoRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final TelefonoAutorizadoRepository telefonos;
    private final PenaRepository penas;
    private final PermisoAreaRepository permisos;
    private final ServicioPermisos servicioPermisos;
    private final ApplicationEventPublisher publisher;
    private final List<ContadorPendientes> contadores;

    public AdminService(SolicitudIngresoRepository solicitudes, UsuarioRepository usuarios,
                        MembresiaRepository membresias, TelefonoAutorizadoRepository telefonos,
                        PenaRepository penas, PermisoAreaRepository permisos,
                        ServicioPermisos servicioPermisos,
                        ApplicationEventPublisher publisher,
                        List<ContadorPendientes> contadores) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.telefonos = telefonos;
        this.penas = penas;
        this.permisos = permisos;
        this.servicioPermisos = servicioPermisos;
        this.publisher = publisher;
        this.contadores = contadores;
    }

    /** Id de la peña piloto. Si falta la siembra (V6), es un fallo de arranque legítimo (500). */
    private Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }

    /**
     * ¿El objetivo es el superadmin y quien actúa es otra persona? Solo el propio
     * superadmin puede cambiarse el rol o el estado; ningún otro admin puede.
     */
    private boolean esSuperadminAjeno(Usuario objetivo, Long adminId, Long miembroId) {
        return objetivo.isEsSuperadmin() && !adminId.equals(miembroId);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResumen> listarSolicitudes(EstadoSolicitud estado) {
        Long penaId = penaId();
        return solicitudes.findByPenaIdAndEstado(penaId, estado).stream()
                .sorted(Comparator.comparing(SolicitudIngreso::getCreatedAt))
                .map(s -> new SolicitudResumen(s.getId(), s.getNombre(), s.getApellidos(),
                        s.getTelefono(), s.getEmail(), s.getMotivo(), s.getRelacion(), s.getConocidos(),
                        s.getPasswordHash() != null, s.getEstado().name(), s.getCreatedAt()))
                .toList();
    }

    /**
     * Cuántas cosas sin atender tiene el usuario en cada área del panel a la que
     * puede acceder. Solo aparecen las áreas con al menos un pendiente. Mapa
     * vacío si el usuario no tiene ninguna área o no hay nada que atender.
     */
    @Transactional(readOnly = true)
    public Map<AreaProtegida, Long> pendientesPorArea(Long usuarioId) {
        Set<AreaProtegida> mias = servicioPermisos.areasDe(usuarioId);
        Map<AreaProtegida, Long> resultado = new EnumMap<>(AreaProtegida.class);
        for (ContadorPendientes contador : contadores) {
            if (!mias.contains(contador.area())) {
                continue;
            }
            long n = contador.contar();
            if (n > 0) {
                resultado.put(contador.area(), n);
            }
        }
        return resultado;
    }

    @Transactional
    public AprobarResponse aprobarSolicitud(Long solicitudId, Long adminId) {
        // No filtramos "no existe" vs "ya resuelta": ambos devuelven 409.
        SolicitudIngreso sol = solicitudes.findById(solicitudId)
                .orElseThrow(SolicitudYaResueltaException::new);
        if (sol.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudYaResueltaException();
        }
        if (usuarios.existsByTelefono(sol.getTelefono()) || usuarios.existsByEmail(sol.getEmail())) {
            throw new RegistroConflictoException();
        }

        Usuario admin = usuarios.findById(adminId).orElseThrow();
        Pena pena = sol.getPena();

        // Reutiliza la fila de telefono_autorizado si ya existe (columna UNIQUE).
        TelefonoAutorizado tel = telefonos.findByTelefono(sol.getTelefono())
                .orElseGet(() -> TelefonoAutorizado.builder()
                        .telefono(sol.getTelefono())
                        .pena(pena)
                        .usado(false)
                        .build());
        tel.setAutorizadoPor(admin);

        boolean cuentaCreada;
        if (sol.getPasswordHash() != null) {
            Usuario usuario = usuarios.save(Usuario.builder()
                    .telefono(sol.getTelefono())
                    .email(sol.getEmail())
                    .passwordHash(sol.getPasswordHash())
                    .nombre(sol.getNombre())
                    .apellidos(sol.getApellidos())
                    .mote(null)
                    .esSuperadmin(false)
                    .activo(true)
                    .build());
            membresias.save(Membresia.builder()
                    .usuario(usuario)
                    .pena(pena)
                    .rol(RolMembresia.MIEMBRO)
                    .activa(true)
                    .build());
            tel.setUsado(true);
            cuentaCreada = true;
        } else {
            tel.setUsado(false);
            cuentaCreada = false;
        }
        telefonos.save(tel);

        sol.setEstado(EstadoSolicitud.APROBADA);
        sol.setResueltaPor(admin);
        sol.setResueltaAt(Instant.now());
        // RGPD / minimización: el usuario ya se creó con el hash (arriba); una vez
        // resuelta la solicitud, el hash no debe quedarse ahí.
        sol.setPasswordHash(null);

        publisher.publishEvent(new SolicitudResueltaEvent(
                sol.getEmail(), sol.getNombre(), true, cuentaCreada, null));
        return new AprobarResponse(cuentaCreada ? "CUENTA_CREADA" : "TELEFONO_AUTORIZADO");
    }

    @Transactional
    public void rechazarSolicitud(Long solicitudId, Long adminId, String motivo) {
        SolicitudIngreso sol = solicitudes.findById(solicitudId)
                .orElseThrow(SolicitudYaResueltaException::new);
        if (sol.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudYaResueltaException();
        }
        String texto = StringUtils.hasText(motivo) ? motivo : MOTIVO_RECHAZO_POR_DEFECTO;

        sol.setEstado(EstadoSolicitud.RECHAZADA);
        sol.setMotivoRechazo(texto);
        sol.setResueltaPor(usuarios.findById(adminId).orElseThrow());
        sol.setResueltaAt(Instant.now());
        // RGPD / minimización: al rechazar no se crea usuario, así que el hash
        // que trajera la solicitud se descarta sin más.
        sol.setPasswordHash(null);

        publisher.publishEvent(new SolicitudResueltaEvent(
                sol.getEmail(), sol.getNombre(), false, false, texto));
    }

    // --- Gestión de miembros (área ADMIN_PERMISOS) ---

    /** Todos los miembros de la peña piloto, ordenados por apellidos y nombre. */
    @Transactional(readOnly = true)
    public List<MiembroResumen> listarMiembros() {
        Long penaId = penaId();
        return membresias.findByPenaId(penaId).stream()
                .map(m -> {
                    Usuario u = m.getUsuario();
                    List<String> areas = permisos.findByUsuarioId(u.getId()).stream()
                            .map(p -> p.getArea().name())
                            .sorted()
                            .toList();
                    return new MiembroResumen(u.getId(), u.getNombre(), u.getApellidos(), u.getMote(),
                            u.getTelefono(), m.getRol().name(), u.isActivo(), u.isEsSuperadmin(), areas);
                })
                .sorted(Comparator.comparing(MiembroResumen::apellidos)
                        .thenComparing(MiembroResumen::nombre))
                .toList();
    }

    /**
     * Cambia el rol de un miembro. Salvaguardas: ascender a {@code ADMIN} solo lo
     * puede hacer un administrador de verdad (tener el área {@code ADMIN_PERMISOS}
     * concedida NO basta para acuñar admins) → 403 {@code SIN_PERMISO}. Y (todas →
     * 409): solo el propio superadmin se toca a sí mismo; no puedes degradarte tú;
     * y no puedes dejar la peña sin ningún admin activo. Un {@code id} sin
     * membresía en la peña → 404 {@code MIEMBRO_NO_ENCONTRADO}.
     */
    @Transactional
    public void cambiarRol(Long miembroId, Long adminId, RolMembresia nuevoRol) {
        Long penaId = penaId();
        Membresia m = membresias.findByUsuarioIdAndPenaId(miembroId, penaId)
                .orElseThrow(MiembroNoEncontradoException::new);
        Usuario objetivo = m.getUsuario();

        if (nuevoRol == RolMembresia.ADMIN && !servicioPermisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        if (esSuperadminAjeno(objetivo, adminId, miembroId)) {
            throw new SoloSuperadminException();
        }
        if (adminId.equals(miembroId) && nuevoRol == RolMembresia.MIEMBRO) {
            throw new AutoModificacionException("NO_TE_PUEDES_DEGRADAR");
        }
        if (nuevoRol == RolMembresia.MIEMBRO && esUltimoAdminActivo(miembroId, penaId)) {
            throw new UltimoAdminException();
        }

        m.setRol(nuevoRol);
        membresias.save(m);
    }

    /**
     * Activa o desactiva a un miembro (usuario y membresía a la vez). Salvaguardas
     * (todas → 409): no puedes desactivarte tú; solo el propio superadmin se toca
     * a sí mismo; y no puedes desactivar al último admin activo. Un {@code id} sin
     * membresía en la peña → 404 {@code MIEMBRO_NO_ENCONTRADO}.
     *
     * <p>Al desactivar se borran también sus filas de {@code permiso_area}: como
     * un usuario desactivado no tiene ningún acceso ({@link ServicioPermisos}),
     * dejar ahí las concesiones solo serviría para restaurar en silencio un
     * acceso admin obsoleto si más tarde se le reactiva.
     */
    @Transactional
    public void cambiarActivo(Long miembroId, Long adminId, boolean activo) {
        Long penaId = penaId();
        Membresia m = membresias.findByUsuarioIdAndPenaId(miembroId, penaId)
                .orElseThrow(MiembroNoEncontradoException::new);
        Usuario objetivo = m.getUsuario();

        if (!activo && adminId.equals(miembroId)) {
            throw new AutoModificacionException("NO_TE_PUEDES_DESACTIVAR");
        }
        if (esSuperadminAjeno(objetivo, adminId, miembroId)) {
            throw new SoloSuperadminException();
        }
        if (!activo && m.getRol() == RolMembresia.ADMIN && esUltimoAdminActivo(miembroId, penaId)) {
            throw new UltimoAdminException();
        }

        objetivo.setActivo(activo);
        m.setActiva(activo);
        if (!activo) {
            permisos.deleteByUsuarioId(miembroId);
        }
        usuarios.save(objetivo);
        membresias.save(m);
    }

    /**
     * Reemplaza el conjunto completo de áreas concedidas a un miembro por el que
     * se pasa (distinct). El {@code flush} tras el borrado evita que Hibernate
     * ordene los INSERT antes del DELETE y choque con {@code uk_permiso_area}.
     *
     * <p>El objetivo se resuelve por su membresía en la peña (como
     * {@link #cambiarRol} / {@link #cambiarActivo}): así no se pueden conceder
     * áreas a un usuario sin membresía, que sería un admin invisible en
     * {@code GET /miembros}. Un {@code id} sin membresía → 404
     * {@code MIEMBRO_NO_ENCONTRADO}.
     */
    @Transactional
    public void reemplazarAreas(Long miembroId, Long adminId, List<AreaProtegida> areas) {
        Long penaId = penaId();
        Membresia m = membresias.findByUsuarioIdAndPenaId(miembroId, penaId)
                .orElseThrow(MiembroNoEncontradoException::new);
        Usuario objetivo = m.getUsuario();
        Usuario admin = usuarios.findById(adminId).orElseThrow();

        permisos.deleteByUsuarioId(miembroId);
        permisos.flush();
        for (AreaProtegida area : new LinkedHashSet<>(areas)) {
            permisos.save(PermisoArea.builder()
                    .usuario(objetivo)
                    .area(area)
                    .concedidoPor(admin)
                    .build());
        }
    }

    /**
     * Entre las membresías {@code ADMIN} de la peña con usuario y membresía
     * activos, ¿queda solo una y es la de {@code usuarioId}?
     */
    private boolean esUltimoAdminActivo(Long usuarioId, Long penaId) {
        List<Membresia> adminsActivos = membresias.findByPenaIdAndRol(penaId, RolMembresia.ADMIN).stream()
                .filter(mm -> mm.isActiva() && mm.getUsuario().isActivo())
                .toList();
        return adminsActivos.size() == 1
                && adminsActivos.get(0).getUsuario().getId().equals(usuarioId);
    }
}
