package com.alerta.sistema_alerta_inundaciones.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA para gestionar la persistencia y consulta de entidades {@link Usuario}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca un usuario por su identificador normalizado.
     *
     * @param identificador correo o nombre de usuario normalizado
     * @return usuario encontrado, si existe
     */
    Optional<Usuario> findByIdentificador(String identificador);

    /**
     * Comprueba si ya existe una cuenta registrada con el identificador dado.
     *
     * @param identificador correo o nombre de usuario normalizado
     * @return {@code true} si el identificador ya está en uso
     */
    boolean existsByIdentificador(String identificador);

    /**
     * Obtiene todos los usuarios ordenados alfabéticamente por su identificador.
     *
     * @return lista de usuarios ordenada por identificador ascendente
     */
    List<Usuario> findAllByOrderByIdentificadorAsc();
}
