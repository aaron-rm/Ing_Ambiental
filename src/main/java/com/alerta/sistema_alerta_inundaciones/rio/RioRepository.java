package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA para la entidad {@link Rio}.
 */
public interface RioRepository extends JpaRepository<Rio, Long> {

    /**
     * Busca el primer río registrado con el identificador de tramo de GEOGloWS indicado.
     *
     * @param geoglowsId identificador del tramo ({@code reach_id}) en GEOGloWS
     * @return río correspondiente, si existe
     */
    Optional<Rio> findFirstByGeoglowsId(Long geoglowsId);

    /**
     * Obtiene los ríos que tienen {@code geoglowsId} asignado pero aún no cuentan con geometría GeoJSON.
     *
     * @return lista de ríos pendientes de geometría
     */
    @Query("SELECT r FROM Rio r WHERE r.geoglowsId IS NOT NULL AND (r.geometria IS NULL OR TRIM(r.geometria) = '')")
    List<Rio> findSinGeometriaConGeoglowsId();
}