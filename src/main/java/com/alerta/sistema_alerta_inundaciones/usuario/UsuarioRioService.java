package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de negocio para gestionar la selección múltiple de ríos que sigue
 * cada usuario para recibir alertas push.
 */
@Service
public class UsuarioRioService {

    private final UsuarioRioRepository seleccionRepository;
    private final RioRepository rioRepository;

    /**
     * Crea el servicio de seguimiento de ríos por usuario.
     *
     * @param seleccionRepository repositorio de relaciones usuario-río
     * @param rioRepository       repositorio de ríos
     */
    public UsuarioRioService(UsuarioRioRepository seleccionRepository, RioRepository rioRepository) {
        this.seleccionRepository = seleccionRepository;
        this.rioRepository = rioRepository;
    }

    /**
     * Lista los ríos que sigue actualmente el usuario indicado.
     *
     * @param usuario usuario propietario de la selección
     * @return lista de {@link RioSeguidoDTO}
     */
    @Transactional(readOnly = true)
    public List<RioSeguidoDTO> listar(Usuario usuario) {
        return seleccionRepository.findByUsuarioId(usuario.getId()).stream()
                .map(RioSeguidoDTO::desde)
                .toList();
    }

    /**
     * Registra de forma idempotente el seguimiento de un río por parte de un usuario.
     *
     * @param usuario usuario que desea seguir el río
     * @param rioId   identificador del río a seguir
     * @return respuesta con el estado de seguimiento y un mensaje descriptivo
     * @throws IllegalArgumentException si no existe un río con el identificador dado
     */
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

    /**
     * Elimina la suscripción de seguimiento de un usuario sobre un río.
     *
     * @param usuario usuario que deja de seguir el río
     * @param rioId   identificador del río
     */
    @Transactional
    public void dejarDeSeguir(Usuario usuario, Long rioId) {
        seleccionRepository.deleteByUsuarioIdAndRioId(usuario.getId(), rioId);
    }
}
