package com.alerta.sistema_alerta_inundaciones.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    Optional<DispositivoPush> findByToken(String token);

    List<DispositivoPush> findByActivoTrue();

    List<DispositivoPush> findByDeviceIdAndActivoTrue(String deviceId);

    List<DispositivoPush> findByUsuarioIdInAndActivoTrue(Collection<Long> usuarioIds);

    @Query("select count(d) from DispositivoPush d where d.usuario.id = :usuarioId and d.activo = true")
    long countByUsuarioIdAndActivoTrue(@Param("usuarioId") Long usuarioId);
}
