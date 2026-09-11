package com.baniterio.api.cuenta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.cuenta.dto.CuentaDetalle;
import com.baniterio.api.cuenta.dto.CuentaResumen;
import com.baniterio.api.identidad.CategoriaMovimiento;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.media.AlmacenRecibos;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sección Cuentas: listado, detalle (la hoja completa) y —solo admin— alta/baja
 * de gastos e ingresos con recibo. El recibo, una vez subido, se sirve por
 * {@code /api/v1/media/recibos/{archivo}} (nombre UUID, como las fotos de perfil).
 */
@Tag(name = "Cuentas", description = "Hoja de cuentas de la peña: movimientos, saldos, ropa y cierre de año.")
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final CuentaConsultaService consulta;
    private final CuentaAdminService admin;
    private final AlmacenRecibos recibos;

    public CuentaController(CuentaConsultaService consulta, CuentaAdminService admin, AlmacenRecibos recibos) {
        this.consulta = consulta;
        this.admin = admin;
        this.recibos = recibos;
    }

    /** Lista las cuentas de la peña (una por año contable). */
    @GetMapping
    public List<CuentaResumen> listar() {
        return consulta.listar();
    }

    /** La hoja completa de una cuenta: peñistas, libro de movimientos con saldo corriente y resumen de gastos. Con {@code anio} se consulta un año ya cerrado. */
    @GetMapping("/{id}")
    public CuentaDetalle detalle(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Integer anio) {
        return consulta.detalle(principal.id(), id, anio);
    }

    /** Marca "he transferido mi saldo al banco de la peña": solo apaga el aviso, no mueve el saldo. */
    @PostMapping("/{id}/transferencia-a-pena")
    public CuentaDetalle marcarTransferido(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return admin.marcarTransferido(principal.id(), id);
    }

    /** Cierra el año contable de la cuenta y abre el siguiente. Solo admin. */
    @PostMapping("/{id}/cerrar-anio")
    public CuentaDetalle cerrarAnio(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return admin.cerrarAnio(principal.id(), id);
    }

    /** Da de alta un gasto o ingreso manual (multipart), con recibo opcional (PDF/foto). Solo admin. */
    @PostMapping(path = "/{id}/movimientos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CuentaDetalle crearMovimiento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            @RequestParam String tipo,
            @RequestParam String concepto,
            @RequestParam BigDecimal importe,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Long adelantadoPorId,
            @RequestParam(required = false) MultipartFile recibo) {
        return admin.crearMovimiento(principal.id(), id,
                OrigenMovimiento.valueOf(tipo), concepto, importe,
                fecha != null ? LocalDate.parse(fecha) : null,
                categoria != null ? CategoriaMovimiento.valueOf(categoria) : null,
                adelantadoPorId, guardarReciboSiHay(recibo));
    }

    /** Borra un gasto/ingreso manual. Solo admin. */
    @DeleteMapping("/movimientos/{movId}")
    public CuentaDetalle borrarMovimiento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long movId) {
        return admin.borrarMovimiento(principal.id(), movId);
    }

    /** Marca la ropa (camiseta/sudadera: cantidad, talla, confirmada) de un asistente en el evento. Solo admin. */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/asistencias/{asistenciaId}/ropa")
    public CuentaDetalle marcarRopa(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId,
            @org.springframework.web.bind.annotation.RequestBody MarcarRopaRequest req) {
        return admin.marcarRopa(principal.id(), id, asistenciaId,
                req.camisetaCantidad(), req.camisetaTalla(), req.camisetaConfirmada(),
                req.sudaderaCantidad(), req.sudaderaTalla(), req.sudaderaConfirmada());
    }

    /** Body de {@code PUT .../ropa}: cada campo {@code null} = "no tocar". */
    public record MarcarRopaRequest(Integer camisetaCantidad, String camisetaTalla, Boolean camisetaConfirmada,
            Integer sudaderaCantidad, String sudaderaTalla, Boolean sudaderaConfirmada) {
    }

    /** Adjunta (o reemplaza) el recibo de un movimiento ya creado. Solo admin. */
    @PostMapping(path = "/movimientos/{movId}/recibo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void subirRecibo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long movId, @RequestParam MultipartFile recibo) {
        admin.guardarRecibo(principal.id(), movId, guardarReciboSiHay(recibo));
    }

    private String guardarReciboSiHay(MultipartFile recibo) {
        if (recibo == null || recibo.isEmpty()) {
            return null;
        }
        String ext = AlmacenRecibos.extensionDe(recibo.getContentType());
        if (ext == null) {
            throw new ReciboNoValidoException();
        }
        try {
            return recibos.guardar(recibo.getBytes(), ext);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
