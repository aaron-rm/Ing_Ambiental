package com.alerta.sistema_alerta_inundaciones.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRioRepository extends JpaRepository<UsuarioRio, Long> {

    List<UsuarioRio> findByUsuarioId(Long usuarioId);

    Optional<UsuarioRio> findByUsuarioIdAndRioId(Long usuarioId, Long rioId);

    boolean existsByUsuarioIdAndRioId(Long usuarioId, Long rioId);

    List<UsuarioRio> findByRioId(Long rioId);

    @Query("select ur.usuario.id from UsuarioRio ur where ur.rio.id = :rioId")
    List<Long> findUsuarioIdsByRioId(@Param("rioId") Long rioId);

    void deleteByUsuarioIdAndRioId(Long usuarioId, Long rioId);
}
