package com.alerta.sistema_alerta_inundaciones.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    Optional<DispositivoPush> findByToken(String token);

    List<DispositivoPush> findByReachIdAndActivoTrue(Long reachId);

    List<DispositivoPush> findByActivoTrue();

    List<DispositivoPush> findByDeviceIdAndActivoTrue(String deviceId);

    long countByReachIdAndActivoTrue(Long reachId);
}
