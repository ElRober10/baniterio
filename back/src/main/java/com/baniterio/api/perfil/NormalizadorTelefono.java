package com.baniterio.api.perfil;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normaliza un teléfono a "móvil español de 9 dígitos" o lo rechaza.
 *
 * <p>La peña es toda española; el registro ya exige {@code ^[67]\d{8}$}. Aquí
 * se acepta lo que teclee el usuario (o lo que venga de la agenda del móvil)
 * con espacios, guiones, paréntesis y un prefijo {@code +34}/{@code 0034}, y se
 * devuelve el número limpio. Si tras limpiar no es un móvil español válido,
 * {@link Optional#empty()} (el llamante decide: pedir corregirlo a mano).
 */
public final class NormalizadorTelefono {

    private static final Pattern MOVIL_ES = Pattern.compile("^[67]\\d{8}$");

    private NormalizadorTelefono() {
    }

    public static Optional<String> normalizar(String entrada) {
        if (entrada == null) {
            return Optional.empty();
        }
        String limpio = entrada.replaceAll("[\\s()\\-]", "");
        if (limpio.startsWith("+34")) {
            limpio = limpio.substring(3);
        } else if (limpio.startsWith("0034")) {
            limpio = limpio.substring(4);
        }
        return MOVIL_ES.matcher(limpio).matches() ? Optional.of(limpio) : Optional.empty();
    }
}
