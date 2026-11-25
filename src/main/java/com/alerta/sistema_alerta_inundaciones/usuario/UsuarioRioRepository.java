package com.alerta.sistema_alerta_inundaciones.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA para consultar y administrar las relaciones
 * de seguimiento entre usuarios y ríos ({@link UsuarioRio}).
 */
public interface UsuarioRioRepository extends JpaRepository<UsuarioRio, Long> {

    /**
     * Obtiene todas las selecciones de ríos de un usuario.
     *
     * @param usuarioId identificador del usuario
     * @return lista de relaciones {@link UsuarioRio} del usuario
     */
    List<UsuarioRio> findByUsuarioId(Long usuarioId);

    /**
     * Busca la relación de seguimiento específica entre un usuario y un río.
     *
     * @param usuarioId identificador del usuario
     * @param rioId     identificador del río
     * @return relación encontrada, si existe
     */
    Optional<UsuarioRio> findByUsuarioIdAndRioId(Long usuarioId, Long rioId);

    /**
     * Verifica si un usuario ya sigue un río determinado.
     *
     * @param usuarioId identificador del usuario
     * @param rioId     identificador del río
     * @return {@code true} si existe la relación
     */
    boolean existsByUsuarioIdAndRioId(Long usuarioId, Long rioId);

    /**
     * Obtiene todas las relaciones de seguimiento asociadas a un río.
     *
     * @param rioId identificador del río
     * @return lista de seguidores del río
     */
    List<UsuarioRio> findByRioId(Long rioId);

    /**
     * Obtiene los identificadores de todos los usuarios que siguen un río.
     *
     * @param rioId identificador del río
     * @return lista de IDs de usuarios seguidores
     */
    @Query("select ur.usuario.id from UsuarioRio ur where ur.rio.id = :rioId")
    List<Long> findUsuarioIdsByRioId(@Param("rioId") Long rioId);

    /**
     * Elimina la relación de seguimiento entre un usuario y un río.
     *
     * @param usuarioId identificador del usuario
     * @param rioId     identificador del río
     */
    void deleteByUsuarioIdAndRioId(Long usuarioId, Long rioId);

    /**
     * Elimina todas las relaciones de seguimiento asociadas a un río.
     *
     * @param rioId identificador del río
     */
    void deleteByRioId(Long rioId);
}
