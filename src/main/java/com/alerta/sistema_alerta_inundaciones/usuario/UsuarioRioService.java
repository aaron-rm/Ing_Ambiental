package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioRioService {

    private final UsuarioRioRepository seleccionRepository;
    private final RioRepository rioRepository;

    public UsuarioRioService(UsuarioRioRepository seleccionRepository, RioRepository rioRepository) {
        this.seleccionRepository = seleccionRepository;
        this.rioRepository = rioRepository;
    }

    @Transactional(readOnly = true)
    public List<RioSeguidoDTO> listar(Usuario usuario) {
        return seleccionRepository.findByUsuarioId(usuario.getId()).stream()
                .map(RioSeguidoDTO::desde)
                .toList();
    }

    @Transactional
    public SeleccionRioResponse seguir(Usuario usuario, Long rioId) {
        Rio rio = rioRepository.findById(rioId)
                .orElseThrow(() -> new IllegalArgumentException("No existe un río con ID " + rioId + "."));

        if (seleccionRepository.existsByUsuarioIdAndRioId(usuario.getId(), rioId)) {
            return new SeleccionRioResponse(rioId, true, "El río ya estaba en tus alertas.");
        }

        seleccionRepository.save(new UsuarioRio(usuario, rio));
        return new SeleccionRioResponse(rioId, true, "Río agregado a tus alertas.");
    }

    @Transactional
    public void dejarDeSeguir(Usuario usuario, Long rioId) {
        seleccionRepository.deleteByUsuarioIdAndRioId(usuario.getId(), rioId);
    }
}
