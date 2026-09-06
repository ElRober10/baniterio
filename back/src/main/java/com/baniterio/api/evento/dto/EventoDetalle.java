package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ficha completa de un evento para su pantalla de detalle. {@code creadoPor}
 * es {@code null} en los eventos sembrados. {@code puedoEditar}/{@code puedoBorrar}
 * los calcula el servicio para el usuario que pregunta (solo sirven para pintar
 * botones; el permiso real se re-comprueba al actuar). {@code oculto} es
 * {@code true} si el evento está "borrado" (oculto, recuperable); cuando lo está,
 * el botón de {@code puedoBorrar} pasa a ser "Recuperar" en vez de "Ocultar".
 * Las 5 {@code cuota*} son {@code null} mientras un administrador no las fije;
 * si las 5 son {@code null} la pantalla no muestra el bloque de cuotas.
 */
public record EventoDetalle(Long id, String nombre, String descripcion, String lugar,
                            LocalDate fecha, LocalDate fechaFin, boolean pasado,
                            CuentaRef cuenta,
                            BigDecimal cuotaCubatas, BigDecimal cuotaCervezas,
                            BigDecimal cuotaCubatas1Dia, BigDecimal cuotaCervezas1Dia,
                            BigDecimal cuotaEmbarazada,
                            CreadoPor creadoPor,
                            boolean puedoEditar, boolean puedoBorrar, boolean oculto,
                            AsistenciaDetalle asistencia) {

    public record CreadoPor(Long id, String nombre) {
    }
}
