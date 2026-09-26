package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RioService {

    private final RioRepository rioRepository;

    public RioService(RioRepository rioRepository) {
        this.rioRepository = rioRepository;
    }

    public Rio guardarRio(Rio rio) {
        return rioRepository.save(rio);
    }

    public List<Rio> obtenerTodos() {
        return rioRepository.findAll();
    }
}