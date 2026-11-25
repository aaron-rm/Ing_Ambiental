package com.alerta.sistema_alerta_inundaciones.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de {@link DispositivoPush}. Consulta los dispositivos
 * registrados para push por token, dispositivo o usuario.
 */
public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    /**
     * Busca un dispositivo por su token FCM.
     *
     * @param token token de registro FCM
     * @return el dispositivo, si existe
     */
    Optional<DispositivoPush> findByToken(String token);

    /**
     * Lista todos los dispositivos activos.
     *
     * @return dispositivos con {@code activo = true}
     */
    List<DispositivoPush> findByActivoTrue();

    /**
     * Lista los dispositivos activos de una instalación.
     *
     * @param deviceId identificador de la instalación
     * @return dispositivos activos con ese identificador
     */
    List<DispositivoPush> findByDeviceIdAndActivoTrue(String deviceId);

    /**
     * Lista los dispositivos activos de varios usuarios.
     *
     * @param usuarioIds identificadores de los usuarios
     * @return dispositivos activos de esos usuarios
     */
    List<DispositivoPush> findByUsuarioIdInAndActivoTrue(Collection<Long> usuarioIds);

    /**
     * Lista los dispositivos activos de un usuario.
     *
     * @param usuarioId identificador del usuario
     * @return dispositivos activos del usuario
     */
    List<DispositivoPush> findByUsuarioIdAndActivoTrue(Long usuarioId);

    /**
     * Cuenta los dispositivos activos de un usuario.
     *
     * @param usuarioId identificador del usuario
     * @return cantidad de dispositivos activos
     */
    @Query("select count(d) from DispositivoPush d where d.usuario.id = :usuarioId and d.activo = true")
    long countByUsuarioIdAndActivoTrue(@Param("usuarioId") Long usuarioId);
}
