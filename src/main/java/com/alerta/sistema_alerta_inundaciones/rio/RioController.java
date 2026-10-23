package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rios")
public class RioController {

    private final RioService rioService;

    public RioController(RioService rioService) {
        this.rioService = rioService;
    }

    @GetMapping
    public List<RioResumenDTO> obtenerRios() {
        return rioService.obtenerTodos();
    }

    @GetMapping(value = "/mapa", produces = "application/geo+json")
    public ResponseEntity<String> obtenerMapa() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(rioService.obtenerMapaGeoJson());
    }
}
