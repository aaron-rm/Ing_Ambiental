package com.alerta.sistema_alerta_inundaciones.alerta;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EstadoRiesgoTramoRepository extends JpaRepository<EstadoRiesgoTramo, Long> {

    Optional<EstadoRiesgoTramo> findByReachId(Long reachId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstadoRiesgoTramo e where e.reachId = :reachId")
    Optional<EstadoRiesgoTramo> buscarParaActualizar(@Param("reachId") Long reachId);
}
