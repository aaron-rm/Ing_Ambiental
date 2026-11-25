package com.alerta.sistema_alerta_inundaciones.alerta;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para consultar y persistir los eventos de alerta generados
 * ante cambios relevantes de riesgo en los tramos monitoreados.
 */
public interface AlertaEventoRepository extends JpaRepository<AlertaEvento, Long> {

    /**
     * Obtiene el evento de alerta más reciente registrado para un tramo.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @return evento más reciente si existe
     */
    Optional<AlertaEvento> findTopByReachIdOrderByFechaCreacionDesc(Long reachId);

    /**
     * Lista los eventos de alerta de un tramo ordenados del más reciente al más antiguo.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param pageable configuración de paginación y límite de resultados
     * @return lista de eventos encontrados para el tramo
     */
    List<AlertaEvento> findByReachIdOrderByFechaCreacionDesc(Long reachId, Pageable pageable);

    /**
     * Busca un evento de alerta por su clave única de idempotencia.
     *
     * @param claveIdempotencia clave generada para evitar eventos duplicados
     * @return evento coincidente si ya fue persistido
     */
    Optional<AlertaEvento> findByClaveIdempotencia(String claveIdempotencia);

    /**
     * Obtiene los eventos de alerta que se encuentran en un estado de envío específico.
     *
     * @param estadoEnvio estado del envío de notificaciones push
     * @return lista de eventos con el estado indicado
     */
    List<AlertaEvento> findByEstadoEnvio(EstadoEnvio estadoEnvio);
}
