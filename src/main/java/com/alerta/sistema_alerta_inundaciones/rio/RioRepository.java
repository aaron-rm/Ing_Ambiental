package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RioRepository extends JpaRepository<Rio, Long> {

    Optional<Rio> findFirstByGeoglowsId(Long geoglowsId);

    @Query("SELECT r FROM Rio r WHERE r.geoglowsId IS NOT NULL AND (r.geometria IS NULL OR TRIM(r.geometria) = '')")
    List<Rio> findSinGeometriaConGeoglowsId();
}