package com.alerta.sistema_alerta_inundaciones.alerta;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SuscripcionAlertaRepository extends JpaRepository<SuscripcionAlerta, Long> {
    List<SuscripcionAlerta> findByReachId(Long reachId);
}
