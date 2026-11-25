package com.alerta.sistema_alerta_inundaciones.alerta;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio JPA para consultar y almacenar suscripciones heredadas por tramo.
 */
public interface SuscripcionAlertaRepository extends JpaRepository<SuscripcionAlerta, Long> {
    /**
     * Obtiene todas las suscripciones registradas para un tramo específico.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @return lista de suscripciones asociadas al tramo
     */
    List<SuscripcionAlerta> findByReachId(Long reachId);
}
