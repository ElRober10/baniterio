package com.baniterio.api.preciobebida;

import java.util.List;

import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.preciobebida.dto.EventoPrecioBebidaDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precio bebidas: años → eventos de ese año. Lo ve cualquier miembro logueado
 * (sin área); la edición del precio en sí llega en una tarea aparte.
 */
@Service
public class PrecioBebidaService {

    private final EventoRepository eventos;
    private final PenaPilotoService pena;

    public PrecioBebidaService(EventoRepository eventos, PenaPilotoService pena) {
        this.eventos = eventos;
        this.pena = pena;
    }

    @Transactional(readOnly = true)
    public List<EventoPrecioBebidaDto> eventos() {
        return eventos.noOcultos(pena.id()).stream()
                .map(e -> new EventoPrecioBebidaDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()))
                .toList();
    }
}
