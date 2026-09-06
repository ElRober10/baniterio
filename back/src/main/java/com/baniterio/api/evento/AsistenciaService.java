package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baniterio.api.auth.ServicioPermisos;
import java.util.List;

import com.baniterio.api.evento.dto.AsistenciaDetalle;
import com.baniterio.api.evento.dto.AsistenciaResumen;
import com.baniterio.api.evento.dto.AsistenteFila;
import com.baniterio.api.evento.dto.BebidaFila;
import com.baniterio.api.evento.dto.FichaBebidaRequest;
import com.baniterio.api.evento.dto.FichaBebidaResponse;
import com.baniterio.api.evento.dto.ListadoAsistentesResponse;
import com.baniterio.api.evento.dto.PendienteRespuesta;
import com.baniterio.api.evento.dto.PersonaPagable;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.NotificacionEvento;
import com.baniterio.api.identidad.NotificacionEventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoFamiliarService;
import com.baniterio.api.identidad.VinculoPareja;
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import com.baniterio.api.push.ResolutorAudiencia;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la asistencia a un evento (pieza 3a): responder Me apunto / No voy /
 * En duda, mandar la notificación de convocatoria, añadir gente a mano y saber
 * qué eventos tengo pendientes de contestar. El formulario de bebida y la cuota
 * (San Miguel) son la pieza 3b.
 */
@Service
public class AsistenciaService {

    private static final String TITULO_PUSH = "Eventos";
    private static final String SLUG_PENA = "baniterio";
    /** Horas que hay que esperar entre un envío de notificación y el siguiente. */
    private static final int HORAS_ENTRE_ENVIOS = 48;

    private final AsistenciaEventoRepository asistencias;
    private final NotificacionEventoRepository notificaciones;
    private final EventoRepository eventos;
    private final UsuarioRepository usuarios;
    private final PenaRepository penas;
    private final ServicioPermisos permisos;
    private final ApplicationEventPublisher publisher;
    private final ResolutorAudiencia resolutor;
    private final FichaBebidaService fichaBebida;
    private final VinculoFamiliarService vinculoFamiliar;
    private final FichaBebidaRepository fichas;
    private final VinculoParejaRepository vinculosPareja;

    public AsistenciaService(AsistenciaEventoRepository asistencias,
                             NotificacionEventoRepository notificaciones, EventoRepository eventos,
                             UsuarioRepository usuarios, PenaRepository penas, ServicioPermisos permisos,
                             ApplicationEventPublisher publisher, ResolutorAudiencia resolutor,
                             FichaBebidaService fichaBebida, VinculoFamiliarService vinculoFamiliar,
                             FichaBebidaRepository fichas, VinculoParejaRepository vinculosPareja) {
        this.asistencias = asistencias;
        this.notificaciones = notificaciones;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.penas = penas;
        this.permisos = permisos;
        this.publisher = publisher;
        this.resolutor = resolutor;
        this.fichaBebida = fichaBebida;
        this.vinculoFamiliar = vinculoFamiliar;
        this.fichas = fichas;
        this.vinculosPareja = vinculosPareja;
    }

    /** Un administrador de verdad, o quien organiza (creó) el evento. */
    private boolean puedeGestionar(Long usuarioId, Evento e) {
        return permisos.esAdministrador(usuarioId)
                || (e.getCreadoPor() != null && e.getCreadoPor().getId().equals(usuarioId));
    }

    private Evento cargar(Long eventoId) {
        return eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
    }

    /** Un evento sigue "en juego" mientras su fecha de inicio no haya pasado. */
    private static void exigirNoPasado(Evento e) {
        if (e.getFecha().isBefore(LocalDate.now())) {
            throw new EventoYaPasadoException();
        }
    }

    /**
     * Respuesta a un evento, la propia o —si {@code actuanteId} puede responder
     * por {@code objetivoId} (pareja con vínculo aceptado, o hijo con cuenta
     * propia; ver {@link VinculoFamiliarService})— en nombre de otro. Crea la
     * fila la primera vez y la actualiza en las siguientes; se puede cambiar
     * hasta el día del evento. Evento pasado → {@link EventoYaPasadoException};
     * sin vínculo con {@code objetivoId} → {@link SinPermisoEventoException}.
     */
    @Transactional
    public void responder(Long actuanteId, Long eventoId, Long objetivoId, EstadoAsistencia estado) {
        Evento e = cargar(eventoId);
        exigirNoPasado(e);
        Long objetivo = exigirPuedeResponderPor(actuanteId, objetivoId);
        AsistenciaEvento a = asistencias.findByEventoIdAndUsuarioId(eventoId, objetivo)
                .orElseGet(() -> AsistenciaEvento.builder()
                        .evento(e)
                        .usuario(usuarios.findById(objetivo).orElseThrow())
                        .build());
        a.setEstado(estado);
        if (!objetivo.equals(actuanteId)) {
            a.setRegistradoPor(usuarios.findById(actuanteId).orElseThrow());
        }
        asistencias.save(a);
    }

    /**
     * Resuelve por quién responde {@code actuanteId} ({@code objetivoId} o, si es
     * {@code null}, uno mismo) y comprueba el vínculo. Se usa desde {@link
     * #responder} y desde {@link FichaBebidaService#guardar}.
     */
    Long exigirPuedeResponderPor(Long actuanteId, Long objetivoId) {
        Long objetivo = objetivoId != null ? objetivoId : actuanteId;
        if (!objetivo.equals(actuanteId)
                && vinculoFamiliar.personasQuePuedoResponder(actuanteId).stream()
                        .noneMatch(p -> p.id().equals(objetivo))) {
            throw new SinPermisoEventoException();
        }
        return objetivo;
    }

    /**
     * Manda (o reenvía) la notificación de convocatoria. Solo un administrador o
     * quien organiza el evento; evento pasado → {@link EventoYaPasadoException};
     * reenvío antes de 48 h desde el último envío → {@link NotificacionReenvioProntoException}.
     * Registra el envío y publica el push: la primera convocatoria va a toda la
     * peña ({@link Audiencia.TodaLaPena}, incluido quien la manda y quien ya haya
     * respondido); los reenvíos van solo a quien todavía no ha contestado
     * ({@link Audiencia.SinRespuestaEvento}).
     */
    @Transactional
    public void mandarNotificacion(Long usuarioId, Long eventoId, String texto) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        exigirNoPasado(e);
        var ultima = notificaciones.findFirstByEventoIdOrderByEnviadaAtDesc(eventoId);
        // TODO(rober): reactivar el bloqueo de 48h para reenviar — comentado a
        // propósito para poder probar convocatorias sin esperar (2026-09-04).
        // ultima.ifPresent(n -> {
        //     Instant limite = Instant.now().minus(HORAS_ENTRE_ENVIOS, ChronoUnit.HOURS);
        //     if (n.getEnviadaAt().isAfter(limite)) {
        //         throw new NotificacionReenvioProntoException();
        //     }
        // });
        boolean primeraVez = ultima.isEmpty();
        String limpio = (texto == null || texto.isBlank()) ? null : texto.trim();
        notificaciones.save(NotificacionEvento.builder()
                .evento(e)
                .texto(limpio)
                .enviadaPor(usuarios.findById(usuarioId).orElseThrow())
                .build());
        String cuerpo = limpio != null ? limpio
                : "«" + e.getNombre() + "» — ¿te apuntas? Entra en la app y responde.";
        Audiencia audiencia = primeraVez
                ? new Audiencia.TodaLaPena()
                : new Audiencia.SinRespuestaEvento(eventoId);
        publisher.publishEvent(new AvisoPushEvent(audiencia, TITULO_PUSH, cuerpo));
    }

    /**
     * Añade a mano a alguien sin app (invitado, persona sin cuenta). Solo un
     * administrador o quien organiza el evento. La fila queda sin {@code usuario}
     * y con {@code registradoPor} = quien la creó.
     */
    @Transactional
    public AsistenciaResumen anadirAMano(Long usuarioId, Long eventoId, String nombre,
                                         EstadoAsistencia estado, FichaBebidaRequest ficha) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        Usuario registrador = usuarios.findById(usuarioId).orElseThrow();
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(e)
                .nombre(nombre.trim())
                .estado(estado)
                .registradoPor(registrador)
                .build());

        FichaBebidaResponse fr = null;
        boolean llevaFicha = e.getCuenta().isLlevaFichaBebida()
                && (estado == EstadoAsistencia.APUNTADO || estado == EstadoAsistencia.EN_DUDA);
        if (llevaFicha && ficha != null) {
            fr = fichaBebida.guardarAMano(usuarioId, eventoId, a, ficha);
        }
        return new AsistenciaResumen(a.getId(), nombre.trim(), a.getEstado(), true,
                fr != null ? fr.cuota() : null, fr != null ? fr.modalidad() : null);
    }

    /**
     * Quita una asistencia añadida a mano. Solo un administrador o quien organiza
     * el evento; {@link AsistenciaNoEncontradaException} si no existe en ese
     * evento; {@link AsistenciaNoManualException} si la fila es de un usuario real
     * (esas no se borran, la persona cambia su propia respuesta).
     */
    @Transactional
    public void quitarAMano(Long usuarioId, Long eventoId, Long asistenciaId) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        AsistenciaEvento a = asistencias.findByIdAndEventoId(asistenciaId, eventoId)
                .orElseThrow(AsistenciaNoEncontradaException::new);
        if (a.getUsuario() != null) {
            throw new AsistenciaNoManualException();
        }
        asistencias.delete(a);
    }

    /**
     * El bloque de asistencia que va dentro de {@code EventoDetalle} para el
     * usuario que pregunta: su respuesta, si puede mandar la notificación, cuándo
     * se puede reenviar y los recuentos. {@code sinContestar} reutiliza la misma
     * audiencia del push ({@link Audiencia.SinRespuestaEvento}).
     */
    @Transactional(readOnly = true)
    public AsistenciaDetalle detalleDe(Long usuarioId, Evento e) {
        Long eventoId = e.getId();
        String miAsistencia = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .map(a -> a.getEstado().name()).orElse(null);
        boolean puedeNotificar = puedeGestionar(usuarioId, e)
                && !e.getFecha().isBefore(LocalDate.now());
        Instant reenviableAt = notificaciones.findFirstByEventoIdOrderByEnviadaAtDesc(eventoId)
                .map(n -> n.getEnviadaAt().plus(HORAS_ENTRE_ENVIOS, ChronoUnit.HOURS))
                .orElse(null);
        boolean notificacionMandada = notificaciones.existsByEventoId(eventoId);
        int apuntados = (int) asistencias.countByEventoIdAndEstado(eventoId, EstadoAsistencia.APUNTADO);
        int noVoy = (int) asistencias.countByEventoIdAndEstado(eventoId, EstadoAsistencia.NO_VOY);
        int enDuda = (int) asistencias.countByEventoIdAndEstado(eventoId, EstadoAsistencia.EN_DUDA);
        int sinContestar = resolutor.resolver(new Audiencia.SinRespuestaEvento(eventoId)).size();
        return new AsistenciaDetalle(miAsistencia, puedeNotificar, reenviableAt, notificacionMandada,
                apuntados, noVoy, enDuda, sinContestar, fichaBebida.detalleDe(usuarioId, e));
    }

    /**
     * Listado de asistentes de un evento de San Miguel (pieza 4). Solo lectura:
     * quién va (APUNTADO / EN_DUDA, nunca NO_VOY), qué bebe, su cuota y —de
     * momento siempre {@code false}— si ha pagado. Añade {@code puedoPagarPor}:
     * a quién puede cubrir el usuario que pregunta (su pareja, hijos mayores con
     * cuenta e invitados propios, apuntados y con cuota). 404 si no existe el
     * evento; 409 {@code EVENTO_SIN_FICHA} si no es de San Miguel.
     */
    @Transactional(readOnly = true)
    public ListadoAsistentesResponse listadoAsistentes(Long usuarioId, Long eventoId) {
        Evento e = cargar(eventoId);
        if (!e.getCuenta().isLlevaFichaBebida()) {
            throw new EventoSinFichaException();
        }

        Map<Long, FichaBebida> fichaPorAsistencia = fichas.findByEventoId(eventoId).stream()
                .collect(Collectors.toMap(FichaBebida::getAsistenciaId, Function.identity()));

        List<AsistenciaEvento> filas = asistencias.findByEventoIdAndEstadoIn(
                eventoId, List.of(EstadoAsistencia.APUNTADO, EstadoAsistencia.EN_DUDA));

        Comparator<AsistenciaEvento> orden = Comparator
                .comparingInt((AsistenciaEvento a) -> a.getEstado() == EstadoAsistencia.APUNTADO ? 0 : 1)
                .thenComparing(a -> nombreDe(a).toLowerCase());

        List<AsistenteFila> asistentes = filas.stream().sorted(orden)
                .map(a -> aFila(a, fichaPorAsistencia.get(a.getId())))
                .toList();

        BigDecimal totalCuotas = asistentes.stream()
                .map(AsistenteFila::cuota).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal miCuota = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .map(a -> fichaPorAsistencia.get(a.getId()))
                .map(FichaBebida::getCuota).orElse(null);

        return new ListadoAsistentesResponse(asistentes, totalCuotas, BigDecimal.ZERO,
                puedoPagarPor(usuarioId, eventoId, fichaPorAsistencia), miCuota);
    }

    private static String nombreDe(AsistenciaEvento a) {
        return a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre();
    }

    private static AsistenteFila aFila(AsistenciaEvento a, FichaBebida f) {
        BebidaFila bebida = f == null ? null : new BebidaFila(
                f.getAlcohol() != null ? f.getAlcohol().getNombre() : null,
                f.getRefresco().getNombre(),
                f.getAlternativa().name(),
                f.getModalidad().name());
        return new AsistenteFila(nombreDe(a), a.getEstado().name(), a.getUsuario() == null,
                bebida, f == null ? null : f.getCuota(), false);
    }

    private List<PersonaPagable> puedoPagarPor(Long usuarioId, Long eventoId,
                                               Map<Long, FichaBebida> fichaPorAsistencia) {
        List<PersonaPagable> out = new ArrayList<>();
        Long parejaId = idPareja(usuarioId).orElse(null);

        for (VinculoFamiliarService.Persona p : vinculoFamiliar.personasParaPago(usuarioId)) {
            asistencias.findByEventoIdAndUsuarioId(eventoId, p.id())
                    .map(a -> fichaPorAsistencia.get(a.getId()))
                    .map(FichaBebida::getCuota)
                    .ifPresent(cuota -> out.add(new PersonaPagable(p.nombre(), cuota,
                            p.id().equals(parejaId) ? "PAREJA" : "HIJO", p.id(), null)));
        }

        for (AsistenciaEvento a : asistencias
                .findByEventoIdAndUsuarioIsNullAndRegistradoPorId(eventoId, usuarioId)) {
            if (a.getEstado() == EstadoAsistencia.NO_VOY) {
                continue;
            }
            FichaBebida f = fichaPorAsistencia.get(a.getId());
            if (f == null || f.getCuota() == null) {
                continue;
            }
            out.add(new PersonaPagable(a.getNombre(), f.getCuota(), "INVITADO", null, a.getId()));
        }
        return out;
    }

    /** Id del usuario que es "la otra mitad" de la pareja con vínculo aceptado. */
    private java.util.Optional<Long> idPareja(Long usuarioId) {
        return vinculosPareja.findBySolicitanteIdAndEstado(usuarioId, EstadoVinculo.ACEPTADO)
                .map(VinculoPareja::getParejaUsuario).map(Usuario::getId)
                .or(() -> vinculosPareja.findByParejaUsuarioIdAndEstado(usuarioId, EstadoVinculo.ACEPTADO)
                        .map(VinculoPareja::getSolicitante).map(Usuario::getId));
    }

    /**
     * Eventos pendientes de contestar (hay notificación y no hay respuesta):
     * los propios y los de cada persona por la que {@code usuarioId} pueda
     * responder (ver {@link VinculoFamiliarService}), ordenados por fecha
     * ascendente. El modal bloqueante los recorre uno a uno.
     */
    @Transactional(readOnly = true)
    public List<PendienteRespuesta> pendientesRespuesta(Long usuarioId) {
        Long penaId = penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
        LocalDate hoy = LocalDate.now();
        List<PendienteRespuesta> resultado = new ArrayList<>();
        for (VinculoFamiliarService.Persona persona : vinculoFamiliar.personasQuePuedoResponder(usuarioId)) {
            for (Evento e : eventos.pendientesRespuesta(penaId, persona.id(), hoy)) {
                resultado.add(new PendienteRespuesta(EventoService.aResumen(e),
                        new PendienteRespuesta.ParaUsuario(persona.id(), persona.nombre())));
            }
        }
        resultado.sort(Comparator.comparing(p -> p.evento().fecha()));
        return resultado;
    }
}
