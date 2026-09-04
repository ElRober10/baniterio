package com.baniterio.api.evento;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.baniterio.api.evento.dto.FichaBebidaRequest;
import com.baniterio.api.evento.dto.FichaBebidaResponse;
import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Bebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.identidad.UsuarioRepository;
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

    public FichaBebidaService(AsistenciaEventoRepository asistencias, FichaBebidaRepository fichas,
                              BebidaRepository bebidas, EventoRepository eventos,
                              UsuarioRepository usuarios, BebidaService bebidaService) {
        this.asistencias = asistencias;
        this.fichas = fichas;
        this.bebidas = bebidas;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.bebidaService = bebidaService;
    }

    /**
     * Guarda mi ficha de bebida para un evento de San Miguel y devuelve la cuota
     * calculada. Crea la fila de asistencia si no existía y la deja en el estado
     * indicado (APUNTADO o EN_DUDA).
     */
    @Transactional
    public FichaBebidaResponse guardar(Long usuarioId, Long eventoId, FichaBebidaRequest req) {
        Evento e = cargarConFicha(eventoId);
        EstadoAsistencia estado = EstadoAsistencia.valueOf(req.estado());
        AsistenciaEvento a = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .orElseGet(() -> AsistenciaEvento.builder()
                        .evento(e).usuario(usuarios.findById(usuarioId).orElseThrow()).build());
        a.setEstado(estado);
        asistencias.save(a);
        return aplicar(e, a, usuarioId, req);
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
        for (FichaBebida f : fichas.findByEventoId(eventoId)) {
            var calc = CalculadoraCuota.calcular(e.getCuotaMaxima(), f.isEmbarazada(),
                    f.isAsisteDia1(), f.isAsisteDia2(), f.getAlcohol() != null, f.getAlternativa());
            f.setCuota(calc.cuota());
            fichas.save(f);
        }
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
        boolean dia1 = dosDias ? req.vaDia1() : true;
        boolean dia2 = dosDias ? req.vaDia2() : true;

        var calc = CalculadoraCuota.calcular(e.getCuotaMaxima(), embarazada, dia1, dia2,
                alcohol != null, alternativa);

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
}
