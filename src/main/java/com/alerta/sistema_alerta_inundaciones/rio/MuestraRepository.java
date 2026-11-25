package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MuestraRepository extends JpaRepository<Muestra, Long> {

    List<Muestra> findTop500ByRioIdOrderByFechaDescIdDesc(Long rioId);
}
