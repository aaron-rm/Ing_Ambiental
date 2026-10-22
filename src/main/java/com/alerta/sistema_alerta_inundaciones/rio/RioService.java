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

    public Rio actualizarRio(Long id, Rio datos) {
        Rio existente = rioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe un rio con el ID " + id + "."));
        existente.setNombre(datos.getNombre());
        existente.setGeoglowsId(datos.getGeoglowsId());
        existente.setLatitud(datos.getLatitud());
        existente.setLongitud(datos.getLongitud());
        return rioRepository.save(existente);
    }
}