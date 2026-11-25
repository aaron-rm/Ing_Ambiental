package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EliminacionRioService {

    private final RioRepository rioRepository;
    private final MuestraRepository muestraRepository;
    private final UsuarioRioRepository usuarioRioRepository;

    public EliminacionRioService(RioRepository rioRepository,
                                 MuestraRepository muestraRepository,
                                 UsuarioRioRepository usuarioRioRepository) {
        this.rioRepository = rioRepository;
        this.muestraRepository = muestraRepository;
        this.usuarioRioRepository = usuarioRioRepository;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!rioRepository.existsById(id)) {
            throw new IllegalArgumentException("Río no encontrado");
        }
        muestraRepository.deleteByRioId(id);
        usuarioRioRepository.deleteByRioId(id);
        rioRepository.deleteById(id);
    }
}
