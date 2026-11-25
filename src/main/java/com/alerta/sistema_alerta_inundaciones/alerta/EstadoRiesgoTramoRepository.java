package com.alerta.sistema_alerta_inundaciones.alerta;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repositorio JPA para consultar y bloquear el estado de riesgo persistido por tramo.
 */
public interface EstadoRiesgoTramoRepository extends JpaRepository<EstadoRiesgoTramo, Long> {

    /**
     * Busca el estado actual de riesgo asociado a un tramo.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @return estado de riesgo del tramo si existe
     */
    Optional<EstadoRiesgoTramo> findByReachId(Long reachId);

    /**
     * Obtiene el estado de riesgo de un tramo aplicando bloqueo pesimista de escritura
     * para garantizar actualizaciones atómicas.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @return estado de riesgo bloqueado para actualización si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstadoRiesgoTramo e where e.reachId = :reachId")
    Optional<EstadoRiesgoTramo> buscarParaActualizar(@Param("reachId") Long reachId);
}
