package com.baniterio.api.perfil.dto;

import java.util.List;

import com.baniterio.api.identidad.ImagenPerfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/v1/perfil}: todo el estado del editor en una sola
 * llamada (datos + imagen + pareja + hijos). El backend valida y reconcilia.
 *
 * <p>{@code nombre}/{@code apellidos}: si llegan en blanco se conserva lo que ya
 * había en {@code usuario} (el editor los manda siempre, pero no obligamos a que
 * vengan). {@code tienePareja} es obligatorio ({@code true}/{@code false}): el
 * editor fuerza a responder sí o no.
 */
public record GuardarPerfilRequest(
        @Size(max = 80) String nombre,
        @Size(max = 120) String apellidos,
        @Size(max = 60) String mote,
        @Size(max = 500) String sobreMi,
        @NotNull ImagenPerfil imagenTipo,
        @NotBlank @Size(max = 80) String imagenRef,
        @NotNull Boolean tienePareja,
        String parejaNombre,
        String parejaTelefono,
        List<@Valid HijoRequest> hijos) {
}
