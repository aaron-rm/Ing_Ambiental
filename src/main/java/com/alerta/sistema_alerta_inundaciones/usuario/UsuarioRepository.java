package com.alerta.sistema_alerta_inundaciones.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByIdentificador(String identificador);

    boolean existsByIdentificador(String identificador);
}
