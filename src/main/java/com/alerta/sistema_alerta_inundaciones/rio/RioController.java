package com.alerta.sistema_alerta_inundaciones.rio;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rios")
public class RioController {

    private final RioService rioService;

    public RioController(RioService rioService) {
        this.rioService = rioService;
    }   

    @PostMapping
    public Rio crearRio(@RequestBody Rio rio) {
        return rioService.guardarRio(rio);
    }

    @GetMapping
    public List<Rio> obtenerRios() {
        return rioService.obtenerTodos();
    }
}