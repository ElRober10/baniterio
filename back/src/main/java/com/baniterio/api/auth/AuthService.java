package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.LoginRequest;
import com.baniterio.api.auth.dto.LoginResponse;
import com.baniterio.api.auth.dto.RegistroRequest;
import com.baniterio.api.auth.dto.SolicitudIngresoRequest;
import com.baniterio.api.auth.dto.UsuarioResponse;
import com.baniterio.api.config.AppProperties;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.SolicitudIngreso;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Lógica de negocio de registro y login. Aquí vive el "qué reglas se aplican",
 * separado del "cómo se recibe por HTTP" ({@link AuthController}).
 *
 * <p>{@link #registrar} es {@code @Transactional}: crea el usuario, crea su
 * membresía en la peña y marca el teléfono como usado en la MISMA transacción,
 * de modo que si algo falla a mitad, no queda nada a medias.
 *
 * <p>Reglas clave:
 * <ul>
 *   <li>Solo se registra quien tenga su teléfono en {@code telefono_autorizado}
 *       y sin usar (puerta de entrada de la peña).
 *   <li>El teléfono del fundador (de {@code app.identidad.telefono-fundador})
 *       entra como superadmin y con rol {@code ADMIN}.
 *   <li>En login, un usuario inactivo se rechaza con el MISMO error que una
 *       contraseña mala (no se filtra que la cuenta existe).
 * </ul>
 */
@Service
public class AuthService {

    private final TelefonoAutorizadoRepository telefonosAutorizados;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SolicitudIngresoRepository solicitudes;
    private final PenaPilotoService pena;
    private final ServicioPermisos servicioPermisos;
    private final ApplicationEventPublisher eventos;
    private final String telefonoFundador;

    public AuthService(TelefonoAutorizadoRepository telefonosAutorizados, UsuarioRepository usuarios,
                       MembresiaRepository membresias, PasswordEncoder passwordEncoder, AppProperties props,
                       JwtService jwtService, SolicitudIngresoRepository solicitudes, PenaPilotoService pena,
                       ServicioPermisos servicioPermisos, ApplicationEventPublisher eventos) {
        this.telefonosAutorizados = telefonosAutorizados;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.solicitudes = solicitudes;
        this.pena = pena;
        this.servicioPermisos = servicioPermisos;
        this.eventos = eventos;
        this.telefonoFundador = props.identidad().telefonoFundador();
    }

    @Transactional
    public Usuario registrar(RegistroRequest req) {
        TelefonoAutorizado autorizado = telefonosAutorizados
                .findByTelefonoAndUsadoFalse(req.telefono())
                .orElseThrow(TelefonoNoAutorizadoException::new);

        if (usuarios.existsByTelefono(req.telefono()) || usuarios.existsByEmail(req.email())) {
            throw new RegistroConflictoException();
        }

        boolean esFundador = req.telefono().equals(telefonoFundador);

        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(req.telefono())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .nombre(req.nombre())
                .apellidos(req.apellidos())
                .mote(StringUtils.hasText(req.mote()) ? req.mote() : null)
                .esSuperadmin(esFundador)
                .activo(true)
                .build());

        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(autorizado.getPena())
                .rol(esFundador ? RolMembresia.ADMIN : RolMembresia.MIEMBRO)
                .activa(true)
                .build());

        autorizado.setUsado(true);
        telefonosAutorizados.save(autorizado);

        return usuario;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        // Un usuario desactivado se rechaza con el MISMO error que unas credenciales malas:
        // no se filtra que la cuenta existe pero está deshabilitada.
        Usuario usuario = usuarios.findByTelefono(req.telefono())
                .filter(Usuario::isActivo)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        String token = jwtService.generar(usuario.getId(), usuario.isEsSuperadmin());
        UsuarioResponse dto = UsuarioResponse.de(usuario,
                servicioPermisos.rolDe(usuario.getId()),
                servicioPermisos.areasDe(usuario.getId()));
        return new LoginResponse(token, dto);
    }

    /**
     * Registra una solicitud de acceso de alguien cuyo teléfono NO está en la
     * lista de la peña. Queda {@code PENDIENTE} hasta que un admin la revise
     * (panel de admin: pendiente).
     */
    @Transactional
    public SolicitudIngreso solicitarIngreso(SolicitudIngresoRequest req) {
        if (telefonosAutorizados.existsByTelefonoAndUsadoFalse(req.telefono())) {
            throw new TelefonoYaAutorizadoException();
        }
        if (solicitudes.existsByTelefonoAndEstado(req.telefono(), EstadoSolicitud.PENDIENTE)) {
            throw new SolicitudYaPendienteException();
        }

        Pena penaSolicitud = pena.entidad();

        SolicitudIngreso solicitud = SolicitudIngreso.builder()
                .pena(penaSolicitud)
                .telefono(req.telefono())
                .email(req.email())
                .nombre(req.nombre())
                .apellidos(req.apellidos())
                .motivo(req.motivo())
                .relacion(req.relacion())
                .conocidos(req.conocidos())
                .estado(EstadoSolicitud.PENDIENTE)
                .build();

        if (StringUtils.hasText(req.password())) {
            solicitud.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        SolicitudIngreso guardada = solicitudes.save(solicitud);
        eventos.publishEvent(new AvisoPushEvent(
                new Audiencia.Administradores(),
                "Nueva solicitud de acceso",
                req.nombre() + " " + req.apellidos() + " quiere entrar en la peña"));
        return guardada;
    }
}
