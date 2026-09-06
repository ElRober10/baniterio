package com.baniterio.api.evento;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.baniterio.api.evento.dto.FichaBebidaDetalle;
import com.baniterio.api.evento.dto.FichaBebidaRequest;
import com.baniterio.api.evento.dto.FichaBebidaResponse;
import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Bebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.EstadoBebida;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoFamiliarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ficha de bebida de San Miguel (pieza 3b): guardar qué bebe cada peñista y
 * calcular su cuota con {@link CalculadoraCuota}. Trabaja con los repos
 * directamente (no con {@code AsistenciaService}) para el upsert del estado, para
 * no crear un ciclo de dependencias.
 */
@Service
public class FichaBebidaService {

    private final AsistenciaEventoRepository asistencias;
    private final FichaBebidaRepository fichas;
    private final BebidaRepository bebidas;
    private final EventoRepository eventos;
    private final UsuarioRepository usuarios;
    private final BebidaService bebidaService;
    private final VinculoFamiliarService vinculoFamiliar;

    public FichaBebidaService(AsistenciaEventoRepository asistencias, FichaBebidaRepository fichas,
                              BebidaRepository bebidas, EventoRepository eventos,
                              UsuarioRepository usuarios, BebidaService bebidaService,
                              VinculoFamiliarService vinculoFamiliar) {
        this.asistencias = asistencias;
        this.fichas = fichas;
        this.bebidas = bebidas;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.bebidaService = bebidaService;
        this.vinculoFamiliar = vinculoFamiliar;
    }

    /**
     * Guarda la ficha de bebida de un evento de San Miguel —la propia, o la de
     * {@code req.paraUsuarioId()} si {@code actuanteId} puede responder por esa
     * persona (ver {@link VinculoFamiliarService})— y devuelve la cuota
     * calculada. Crea la fila de asistencia si no existía y la deja en el estado
     * indicado (APUNTADO o EN_DUDA). Sin vínculo con {@code paraUsuarioId} →
     * {@link SinPermisoEventoException}.
     */
    @Transactional
    public FichaBebidaResponse guardar(Long actuanteId, Long eventoId, FichaBebidaRequest req) {
        Evento e = cargarConFicha(eventoId);
        Long objetivo = req.paraUsuarioId() != null ? req.paraUsuarioId() : actuanteId;
        if (!objetivo.equals(actuanteId)
                && vinculoFamiliar.personasQuePuedoResponder(actuanteId).stream()
                        .noneMatch(p -> p.id().equals(objetivo))) {
            throw new SinPermisoEventoException();
        }
        EstadoAsistencia estado = EstadoAsistencia.valueOf(req.estado());
        AsistenciaEvento a = asistencias.findByEventoIdAndUsuarioId(eventoId, objetivo)
                .orElseGet(() -> AsistenciaEvento.builder()
                        .evento(e).usuario(usuarios.findById(objetivo).orElseThrow()).build());
        a.setEstado(estado);
        if (!objetivo.equals(actuanteId)) {
            a.setRegistradoPor(usuarios.findById(actuanteId).orElseThrow());
        }
        asistencias.save(a);
        return aplicar(e, a, actuanteId, req);
    }

    /**
     * Rellena la ficha de una asistencia que acaba de crear un admin "a mano".
     * No toca el estado (lo fija el alta). {@code registradorId} es el admin, que
     * es a quien se le atribuye una bebida propuesta con "Otra…".
     */
    @Transactional
    public FichaBebidaResponse guardarAMano(Long registradorId, Long eventoId,
                                            AsistenciaEvento asistencia, FichaBebidaRequest req) {
        Evento e = cargarConFicha(eventoId);
        return aplicar(e, asistencia, registradorId, req);
    }

    /** Recalcula el importe de la cuota (no la modalidad) de todas las fichas de un evento. */
    @Transactional
    public void recalcularCuotas(Long eventoId) {
        Evento e = eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
        var cuotas = cuotasDe(e);
        for (FichaBebida f : fichas.findByEventoId(eventoId)) {
            var calc = CalculadoraCuota.calcular(cuotas, f.isEmbarazada(),
                    f.isAsisteDia1(), f.isAsisteDia2(), f.getAlcohol() != null);
            f.setCuota(calc.cuota());
            fichas.save(f);
        }
    }

    private static CalculadoraCuota.Cuotas cuotasDe(Evento e) {
        return new CalculadoraCuota.Cuotas(e.getCuotaCubatas(), e.getCuotaCervezas(),
                e.getCuotaCubatas1Dia(), e.getCuotaCervezas1Dia(), e.getCuotaEmbarazada());
    }

    private Evento cargarConFicha(Long eventoId) {
        Evento e = eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
        if (!e.getCuenta().isLlevaFichaBebida()) {
            throw new EventoSinFichaException();
        }
        if (e.getFecha().isBefore(LocalDate.now())) {
            throw new EventoYaPasadoException();
        }
        return e;
    }

    private FichaBebidaResponse aplicar(Evento e, AsistenciaEvento a, Long proponenteId,
                                        FichaBebidaRequest req) {
        boolean embarazada = req.esEmbarazada();
        Bebida alcohol = embarazada ? null : resolver(TipoBebida.ALCOHOL, req.alcoholBebidaId(),
                req.alcoholOtra(), proponenteId, false);
        Bebida refresco = resolver(TipoBebida.REFRESCO, req.refrescoBebidaId(),
                req.refrescoOtra(), proponenteId, true);
        Alternativa alternativa = embarazada ? Alternativa.NADA
                : Alternativa.valueOf(req.alternativa());
        String cervezaEspecial = (!embarazada && alternativa == Alternativa.CERVEZA_ESPECIAL)
                ? req.cervezaEspecial().trim() : null;

        boolean dosDias = esDeDosDias(e);
        boolean dia1 = !dosDias || req.vaDia1();
        boolean dia2 = !dosDias || req.vaDia2();

        var calc = CalculadoraCuota.calcular(cuotasDe(e), embarazada, dia1, dia2,
                alcohol != null);

        FichaBebida f = fichas.findByAsistenciaId(a.getId())
                .orElseGet(() -> FichaBebida.builder().asistencia(a).build());
        f.setAsistencia(a);
        f.setAlcohol(alcohol);
        f.setRefresco(refresco);
        f.setAlternativa(alternativa);
        f.setCervezaEspecial(cervezaEspecial);
        f.setEmbarazada(embarazada);
        f.setAsisteDia1(dia1);
        f.setAsisteDia2(dia2);
        f.setModalidad(calc.modalidad());
        f.setCuota(calc.cuota());
        fichas.save(f);

        return new FichaBebidaResponse(calc.modalidad().name(), calc.cuota(), calc.cuota() == null);
    }

    private Bebida resolver(TipoBebida tipo, Long id, String otra, Long proponenteId, boolean obligatorio) {
        if (otra != null && !otra.isBlank()) {
            return bebidaService.resolverOtra(tipo, otra, proponenteId);
        }
        if (id != null) {
            return bebidas.findById(id).orElseThrow(BebidaNoEncontradaException::new);
        }
        if (obligatorio) {
            throw new BebidaNoEncontradaException();
        }
        return null;
    }

    /** San Miguel es de 2 días: {@code fecha} y {@code fechaFin}, con un día de diferencia. */
    private static boolean esDeDosDias(Evento e) {
        return e.getFechaFin() != null
                && ChronoUnit.DAYS.between(e.getFecha(), e.getFechaFin()) == 1;
    }

    /**
     * El bloque de ficha que va dentro de {@code EventoDetalle.asistencia} para el
     * usuario que pregunta. {@code llevaFicha=false} si el evento no es de San
     * Miguel; {@code miFicha} null si aún no la ha rellenado.
     */
    @Transactional(readOnly = true)
    public FichaBebidaDetalle detalleDe(Long usuarioId, Evento e) {
        if (!e.getCuenta().isLlevaFichaBebida()) {
            return new FichaBebidaDetalle(false, List.of(), null);
        }
        List<LocalDate> dias = esDeDosDias(e)
                ? List.of(e.getFecha(), e.getFechaFin())
                : List.of(e.getFecha());
        FichaBebidaDetalle.MiFicha mia = asistencias.findByEventoIdAndUsuarioId(e.getId(), usuarioId)
                .flatMap(a -> fichas.findByAsistenciaId(a.getId()))
                .map(FichaBebidaService::aMiFicha)
                .orElse(null);
        return new FichaBebidaDetalle(true, dias, mia);
    }

    private static FichaBebidaDetalle.MiFicha aMiFicha(FichaBebida f) {
        Bebida al = f.getAlcohol();
        Bebida re = f.getRefresco();
        boolean bebidaPendiente = (al != null && al.getEstado() != EstadoBebida.ACEPTADA)
                || re.getEstado() != EstadoBebida.ACEPTADA;
        return new FichaBebidaDetalle.MiFicha(
                al != null ? al.getId() : null,
                al != null ? al.getNombre() : "No bebo alcohol",
                re.getId(), re.getNombre(),
                f.getAlternativa().name(), f.getCervezaEspecial(),
                f.isEmbarazada(), f.isAsisteDia1(), f.isAsisteDia2(),
                f.getModalidad().name(), f.getCuota(), f.getCuota() == null, bebidaPendiente);
    }
}
