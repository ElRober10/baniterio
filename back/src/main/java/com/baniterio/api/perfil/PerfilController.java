package com.baniterio.api.perfil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.media.CatalogoAvatares;
import com.baniterio.api.perfil.dto.AvatarResumen;
import com.baniterio.api.perfil.dto.GuardarPerfilRequest;
import com.baniterio.api.perfil.dto.PerfilResponse;
import com.baniterio.api.perfil.dto.SubirFotoResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Editor de perfil del miembro: leer mi perfil, guardarlo entero (datos + imagen
 * + pareja + hijos), subir una foto y listar el catálogo de avatares. Solo
 * traduce HTTP ↔ dominio; la lógica vive en {@link PerfilService}. El id del
 * usuario sale siempre del token ({@code principal.id()}), nunca del cuerpo.
 *
 * <p>Errores → {@link com.baniterio.api.web.ApiExceptionHandler} (avatar
 * inexistente 400, ref de foto inválida 400, tipo de imagen 415, tamaño 413).
 */
@Tag(name = "Perfil", description = "Editor del perfil propio: datos, imagen, foto, pareja e hijos.")
@RestController
@RequestMapping("/api/v1/perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final CatalogoAvatares catalogo;

    public PerfilController(PerfilService perfilService, CatalogoAvatares catalogo) {
        this.perfilService = perfilService;
        this.catalogo = catalogo;
    }

    /** Devuelve mi perfil completo: datos, imagen, pareja e hijos. */
    @GetMapping
    public PerfilResponse miPerfil(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return perfilService.miPerfil(principal.id());
    }

    /** Guarda mi perfil entero de una vez (datos + imagen + pareja + hijos). */
    @PutMapping
    public PerfilResponse guardar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody GuardarPerfilRequest req) {
        return perfilService.guardar(principal.id(), req);
    }

    /** Lista el catálogo de avatares que se puede elegir como imagen de perfil. */
    @GetMapping("/avatares")
    public List<AvatarResumen> avatares() {
        return catalogo.listar();
    }

    /** Sube una foto de perfil (multipart, campo {@code archivo}) y devuelve su referencia. */
    @PostMapping("/foto")
    public SubirFotoResponse subirFoto(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            return perfilService.subirFoto(principal.id(), archivo.getBytes(), archivo.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Confirmo el vínculo de pareja que otra persona declaró conmigo. */
    @PostMapping("/pareja/aceptar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aceptarPareja(@AuthenticationPrincipal UsuarioPrincipal principal) {
        perfilService.aceptarPareja(principal.id());
    }

    /** Rechazo el vínculo de pareja que otra persona declaró conmigo. */
    @PostMapping("/pareja/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazarPareja(@AuthenticationPrincipal UsuarioPrincipal principal) {
        perfilService.rechazarPareja(principal.id());
    }

    /** Deshago mi vínculo de pareja vivo (cualquiera de los dos lados puede). */
    @DeleteMapping("/pareja")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void romperPareja(@AuthenticationPrincipal UsuarioPrincipal principal) {
        perfilService.romperPareja(principal.id());
    }
}
