package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio Spring Data JPA para consultar y eliminar muestras asociadas a los ríos.
 */
public interface MuestraRepository extends JpaRepository<Muestra, Long> {

    /**
     * Obtiene hasta 500 muestras de un río ordenadas por fecha e ID de forma descendente.
     *
     * @param rioId identificador del río
     * @return lista de las muestras más recientes del río
     */
    List<Muestra> findTop500ByRioIdOrderByFechaDescIdDesc(Long rioId);

    /**
     * Elimina todas las muestras pertenecientes a un río.
     *
     * @param rioId identificador del río
     */
    void deleteByRioId(Long rioId);
}
