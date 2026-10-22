package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RioRepository extends JpaRepository<Rio, Long> {

    Optional<Rio> findFirstByGeoglowsId(Long geoglowsId);
}