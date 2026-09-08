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
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;
    private final AlmacenRecibos recibos;

    public CuentaController(CuentaService cuentaService, AlmacenRecibos recibos) {
        this.cuentaService = cuentaService;
        this.recibos = recibos;
    }

    @GetMapping
    public List<CuentaResumen> listar() {
        return cuentaService.listar();
    }

    @GetMapping("/{id}")
    public CuentaDetalle detalle(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        return cuentaService.detalle(principal.id(), id);
    }

    @PostMapping("/{id}/transferencia-a-pena")
    public CuentaDetalle marcarTransferido(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return cuentaService.marcarTransferido(principal.id(), id);
    }

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
        return cuentaService.crearMovimiento(principal.id(), id,
                OrigenMovimiento.valueOf(tipo), concepto, importe,
                fecha != null ? LocalDate.parse(fecha) : null,
                categoria != null ? CategoriaMovimiento.valueOf(categoria) : null,
                adelantadoPorId, guardarReciboSiHay(recibo));
    }

    @DeleteMapping("/movimientos/{movId}")
    public CuentaDetalle borrarMovimiento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long movId) {
        return cuentaService.borrarMovimiento(principal.id(), movId);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/asistencias/{asistenciaId}/ropa")
    public CuentaDetalle marcarRopa(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId,
            @org.springframework.web.bind.annotation.RequestBody MarcarRopaRequest req) {
        return cuentaService.marcarRopa(principal.id(), id, asistenciaId,
                req.camisetaCantidad(), req.camisetaTalla(),
                req.sudaderaCantidad(), req.sudaderaTalla());
    }

    /** Body de {@code PUT .../ropa}: cada campo {@code null} = "no tocar". */
    public record MarcarRopaRequest(Integer camisetaCantidad, String camisetaTalla,
            Integer sudaderaCantidad, String sudaderaTalla) {
    }

    @PostMapping(path = "/movimientos/{movId}/recibo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void subirRecibo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long movId, @RequestParam MultipartFile recibo) {
        cuentaService.guardarRecibo(principal.id(), movId, guardarReciboSiHay(recibo));
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
