package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio encargado de eliminar de forma transaccional un río del catálogo
 * junto con sus muestras y las selecciones de seguimiento de usuarios.
 */
@Service
public class EliminacionRioService {

    private final RioRepository rioRepository;
    private final MuestraRepository muestraRepository;
    private final UsuarioRioRepository usuarioRioRepository;

    /**
     * Crea el servicio de eliminación de ríos.
     *
     * @param rioRepository        repositorio de ríos
     * @param muestraRepository    repositorio de muestras de ríos
     * @param usuarioRioRepository repositorio de relaciones usuario-río
     */
    public EliminacionRioService(RioRepository rioRepository,
                                 MuestraRepository muestraRepository,
                                 UsuarioRioRepository usuarioRioRepository) {
        this.rioRepository = rioRepository;
        this.muestraRepository = muestraRepository;
        this.usuarioRioRepository = usuarioRioRepository;
    }

    /**
     * Elimina el río indicado y borra previamente sus muestras y relaciones con usuarios.
     *
     * @param id identificador del río a eliminar
     * @throws IllegalArgumentException si no existe un río con ese identificador
     */
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
