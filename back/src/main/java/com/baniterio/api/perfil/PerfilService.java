package com.baniterio.api.perfil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Hijo;
import com.baniterio.api.identidad.HijoRepository;
import com.baniterio.api.identidad.ImagenPerfil;
import com.baniterio.api.identidad.Perfil;
import com.baniterio.api.identidad.PerfilRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoPareja;
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.media.AlmacenImagenes;
import com.baniterio.api.media.CatalogoAvatares;
import com.baniterio.api.media.ProcesadorImagen;
import com.baniterio.api.perfil.dto.GuardarPerfilRequest;
import com.baniterio.api.perfil.dto.PerfilResponse;
import com.baniterio.api.perfil.dto.PerfilResponse.HijoEnPerfil;
import com.baniterio.api.perfil.dto.PerfilResponse.ParejaEnPerfil;
import com.baniterio.api.perfil.dto.PerfilResponse.VinculoPendiente;
import com.baniterio.api.perfil.dto.SubirFotoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Lógica del editor de perfil del miembro. Todo se hace dentro de una
 * transacción (la app corre con {@code open-in-view: false}): las entidades se
 * mapean a {@link PerfilResponse} aquí, nunca se devuelven al controlador.
 *
 * <p>{@code guardar} manda todo el estado del editor de una vez y delega la
 * reconciliación de pareja e hijos en {@link VinculoParejaService} (Task 7) y
 * {@link HijosReconciliador} (Task 8), que hoy son stubs.
 */
@Service
public class PerfilService {

    /** {@code {uuid}.jpg} — la forma que devuelve {@link AlmacenImagenes#guardarFoto}. */
    private static final Pattern REF_FOTO = Pattern.compile("^[0-9a-fA-F-]{36}\\.jpg$");

    private static final int LADO_FOTO = 512;

    private final UsuarioRepository usuarios;
    private final PerfilRepository perfiles;
    private final VinculoParejaRepository vinculos;
    private final HijoRepository hijos;
    private final CatalogoAvatares catalogo;
    private final AlmacenImagenes almacen;
    private final VinculoParejaService vinculoParejaService;
    private final HijosReconciliador hijosReconciliador;

    public PerfilService(UsuarioRepository usuarios, PerfilRepository perfiles,
            VinculoParejaRepository vinculos, HijoRepository hijos, CatalogoAvatares catalogo,
            AlmacenImagenes almacen, VinculoParejaService vinculoParejaService,
            HijosReconciliador hijosReconciliador) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.vinculos = vinculos;
        this.hijos = hijos;
        this.catalogo = catalogo;
        this.almacen = almacen;
        this.vinculoParejaService = vinculoParejaService;
        this.hijosReconciliador = hijosReconciliador;
    }

    /**
     * Mi perfil. Siempre devuelve algo: si aún no hay fila en {@code perfil}, la
     * forma vacía con {@code completado=false} y los datos de {@code usuario}.
     * Antes de leer, deja que la Task 7 promueva un vínculo {@code SIN_CUENTA} a
     * {@code PENDIENTE} si procede.
     */
    @Transactional
    public PerfilResponse miPerfil(Long usuarioId) {
        vinculoParejaService.reconciliarAlEntrar(usuarioId);

        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        Optional<Perfil> perfil = perfiles.findByUsuarioId(usuarioId);

        ParejaEnPerfil pareja = vinculos
                .findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .map(v -> new ParejaEnPerfil(v.getId(), v.getParejaNombre(), v.getParejaTelefono(),
                        v.getEstado().name()))
                // El lado que acepta no es el solicitante: un vínculo ACEPTADO donde
                // soy la pareja registrada también sale como "mi pareja" (con los
                // datos del solicitante, que es la persona real).
                .or(() -> vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                        .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO)
                        .map(v -> new ParejaEnPerfil(v.getId(), v.getSolicitante().getNombre(),
                                v.getSolicitante().getTelefono(), v.getEstado().name())))
                .orElse(null);

        VinculoPendiente pendiente = vinculos
                .findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(v -> v.getEstado() == EstadoVinculo.PENDIENTE)
                .map(v -> new VinculoPendiente(v.getId(), v.getSolicitante().getNombre()))
                .orElse(null);

        return new PerfilResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellidos(),
                usuario.getMote(),
                perfil.map(Perfil::getSobreMi).orElse(null),
                perfil.map(p -> p.getImagenTipo().name()).orElse(null),
                perfil.map(Perfil::getImagenRef).orElse(null),
                perfil.map(this::imagenUrl).orElse(null),
                perfil.map(Perfil::isCompletado).orElse(false),
                pareja,
                misHijos(usuarioId),
                pendiente);
    }

    /**
     * Guarda todo el editor: valida imagen y pareja, actualiza {@code usuario},
     * hace <i>upsert</i> de {@code perfil} con {@code completado=true}, y delega
     * pareja e hijos. Si se cambia una {@code FOTO} por otra imagen, se borra el
     * fichero anterior. Devuelve el perfil ya recargado.
     */
    @Transactional
    public PerfilResponse guardar(Long usuarioId, GuardarPerfilRequest req) {
        if (req.tienePareja() == null) {
            // Defensa en profundidad: @NotNull ya lo corta antes con 400 VALIDACION.
            throw new IllegalArgumentException("tienePareja es obligatorio");
        }
        validarImagen(req.imagenTipo(), req.imagenRef());

        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        if (StringUtils.hasText(req.nombre())) {
            usuario.setNombre(req.nombre().trim());
        }
        if (StringUtils.hasText(req.apellidos())) {
            usuario.setApellidos(req.apellidos().trim());
        }
        usuario.setMote(StringUtils.hasText(req.mote()) ? req.mote().trim() : null);
        usuarios.save(usuario);

        Perfil perfil = perfiles.findByUsuarioId(usuarioId).orElseGet(() -> {
            Perfil nuevo = new Perfil();
            nuevo.setUsuario(usuario);
            return nuevo;
        });

        boolean cambioDeFoto = perfil.getId() != null
                && perfil.getImagenTipo() == ImagenPerfil.FOTO
                && !(req.imagenTipo() == ImagenPerfil.FOTO && req.imagenRef().equals(perfil.getImagenRef()));
        String fotoABorrar = cambioDeFoto ? perfil.getImagenRef() : null;

        perfil.setSobreMi(StringUtils.hasText(req.sobreMi()) ? req.sobreMi().trim() : null);
        perfil.setImagenTipo(req.imagenTipo());
        perfil.setImagenRef(req.imagenRef());
        perfil.setCompletado(true);
        perfiles.save(perfil);

        if (fotoABorrar != null) {
            // El borrado del fichero anterior espera a que la transacción confirme:
            // la reconciliación de pareja/hijos de más abajo puede lanzar (p. ej. 409)
            // y revertir, y en ese caso el fichero NO debe haberse borrado ya.
            borrarFotoTrasCommit(fotoABorrar);
        }

        vinculoParejaService.aplicarDesdePerfil(usuarioId, req.tienePareja(),
                req.parejaNombre(), req.parejaTelefono());
        hijosReconciliador.aplicar(usuarioId, req.hijos() == null ? List.of() : req.hijos());

        return miPerfil(usuarioId);
    }

    /** {@code POST /perfil/pareja/aceptar}: confirmo un vínculo {@code PENDIENTE} dirigido a mí. */
    public void aceptarPareja(Long usuarioId) {
        vinculoParejaService.aceptar(usuarioId);
    }

    /** {@code POST /perfil/pareja/rechazar}: rechazo un vínculo {@code PENDIENTE} dirigido a mí. */
    public void rechazarPareja(Long usuarioId) {
        vinculoParejaService.rechazar(usuarioId);
    }

    /** {@code DELETE /perfil/pareja}: deshago mi vínculo vivo (como solicitante o como pareja). */
    public void romperPareja(Long usuarioId) {
        vinculoParejaService.romper(usuarioId);
    }

    /**
     * Procesa y guarda una foto subida. No toca {@code perfil}: el cliente hace
     * el {@code PUT /perfil} después con la {@code imagenRef} devuelta. Una subida
     * que no se confirme deja un fichero suelto (coste bajo, se puede podar).
     */
    @Transactional
    public SubirFotoResponse subirFoto(Long usuarioId, byte[] contenido, String contentType) {
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw new ImagenNoSoportadaException();
        }
        byte[] jpeg = ProcesadorImagen.aJpegCuadrado(contenido, LADO_FOTO);
        return new SubirFotoResponse(almacen.guardarFoto(jpeg));
    }

    /**
     * Registra el borrado del fichero de foto para después del commit. Si la
     * transacción revierte, el callback no se ejecuta y el fichero se conserva.
     */
    private void borrarFotoTrasCommit(String ref) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                almacen.borrarFoto(ref);
            }
        });
    }

    private void validarImagen(ImagenPerfil tipo, String ref) {
        if (tipo == ImagenPerfil.AVATAR) {
            if (!catalogo.existe(ref)) {
                throw new AvatarInexistenteException();
            }
        } else {
            if (!REF_FOTO.matcher(ref).matches() || !almacen.existeFoto(ref)) {
                throw new ImagenRefInvalidaException();
            }
        }
    }

    private String imagenUrl(Perfil perfil) {
        return switch (perfil.getImagenTipo()) {
            case AVATAR -> "/api/v1/media/avatares/" + perfil.getImagenRef() + ".png";
            case FOTO -> "/api/v1/media/fotos/" + perfil.getImagenRef();
        };
    }

    /**
     * Los hijos que este usuario ve/edita: los que él creó y —si tiene un vínculo
     * {@code ACEPTADO}— también los que cuelgan de ese vínculo (los de su pareja).
     * Sin duplicar por id.
     */
    private List<HijoEnPerfil> misHijos(Long usuarioId) {
        Map<Long, Hijo> porId = new LinkedHashMap<>();
        for (Hijo h : hijos.findByCreadorId(usuarioId)) {
            porId.put(h.getId(), h);
        }
        vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO)
                .ifPresent(v -> agregarHijosDelVinculo(v, porId));
        vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO)
                .ifPresent(v -> agregarHijosDelVinculo(v, porId));

        List<HijoEnPerfil> resultado = new ArrayList<>();
        for (Hijo h : porId.values()) {
            resultado.add(new HijoEnPerfil(h.getId(), h.getNombre(), h.isMayorDeEdad(),
                    h.getTelefono(), h.isVisible(), h.getUsuario() != null));
        }
        return resultado;
    }

    private void agregarHijosDelVinculo(VinculoPareja vinculo, Map<Long, Hijo> porId) {
        for (Hijo h : hijos.findByVinculoParejaId(vinculo.getId())) {
            porId.putIfAbsent(h.getId(), h);
        }
    }
}
