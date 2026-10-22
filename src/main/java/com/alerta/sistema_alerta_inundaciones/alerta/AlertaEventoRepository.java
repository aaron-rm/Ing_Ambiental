package com.alerta.sistema_alerta_inundaciones.alerta;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertaEventoRepository extends JpaRepository<AlertaEvento, Long> {

    Optional<AlertaEvento> findTopByReachIdOrderByFechaCreacionDesc(Long reachId);

    List<AlertaEvento> findByReachIdOrderByFechaCreacionDesc(Long reachId, Pageable pageable);

    Optional<AlertaEvento> findByClaveIdempotencia(String claveIdempotencia);

    List<AlertaEvento> findByEstadoEnvio(EstadoEnvio estadoEnvio);
}
