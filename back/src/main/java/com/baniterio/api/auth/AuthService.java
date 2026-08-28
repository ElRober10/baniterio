package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.LoginRequest;
import com.baniterio.api.auth.dto.LoginResponse;
import com.baniterio.api.auth.dto.RegistroRequest;
import com.baniterio.api.auth.dto.UsuarioResponse;
import com.baniterio.api.config.AppProperties;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final TelefonoAutorizadoRepository telefonosAutorizados;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String telefonoFundador;

    public AuthService(TelefonoAutorizadoRepository telefonosAutorizados, UsuarioRepository usuarios,
                       MembresiaRepository membresias, PasswordEncoder passwordEncoder, AppProperties props,
                       JwtService jwtService) {
        this.telefonosAutorizados = telefonosAutorizados;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        return new LoginResponse(token, UsuarioResponse.de(usuario));
    }
}
