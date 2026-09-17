package com.baniterio.api.preciobebida;

import java.util.List;

import com.baniterio.api.preciobebida.dto.EventoPrecioBebidaDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Precio bebidas: lo ve cualquier miembro logueado. */
@Tag(name = "Precio bebidas", description = "Años y eventos para fijar el precio de las bebidas.")
@RestController
@RequestMapping("/api/v1/precio-bebida")
public class PrecioBebidaController {

    private final PrecioBebidaService service;

    public PrecioBebidaController(PrecioBebidaService service) {
        this.service = service;
    }

    @GetMapping("/eventos")
    public List<EventoPrecioBebidaDto> eventos() {
        return service.eventos();
    }
}
