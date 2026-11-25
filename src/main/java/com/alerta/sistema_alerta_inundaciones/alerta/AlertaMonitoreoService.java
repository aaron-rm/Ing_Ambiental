package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.config.PropiedadesMonitoreo;
import com.alerta.sistema_alerta_inundaciones.riesgo.DatosRiesgoTramo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluacionRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluadorRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.ProveedorDatosRiesgo;
import com.alerta.sistema_alerta_inundaciones.push.PushService;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Orquesta el ciclo de monitoreo: datos reales -> evaluación -> evento
 * persistido -> notificación push. Nunca deja que un fallo externo detenga el
 * resto de los tramos ni el funcionamiento general del backend.
 */
@Service
public class AlertaMonitoreoService {

    private static final Logger log = LoggerFactory.getLogger(AlertaMonitoreoService.class);

    private final RioRepository rioRepository;
    private final ProveedorDatosRiesgo proveedorDatos;
    private final EvaluadorRiesgo evaluador;
    private final AlertaEventoService alertaEventoService;
    private final PushService pushService;
    private final PropiedadesMonitoreo propiedadesMonitoreo;
    private final ConcurrentHashMap<Long, ReentrantLock> bloqueos = new ConcurrentHashMap<>();

    /**
     * Crea el servicio de monitoreo periódico y evaluación manual de ríos.
     *
     * @param rioRepository repositorio de ríos configurados en el sistema
     * @param proveedorDatos proveedor de datos hidrometeorológicos del tramo
     * @param evaluador componente que calcula el nivel de riesgo
     * @param alertaEventoService servicio que registra cambios de nivel y eventos
     * @param pushService servicio de envío de notificaciones push
     * @param propiedadesMonitoreo propiedades de activación e intervalos del monitoreo
     */
    public AlertaMonitoreoService(RioRepository rioRepository,
                                  ProveedorDatosRiesgo proveedorDatos,
                                  EvaluadorRiesgo evaluador,
                                  AlertaEventoService alertaEventoService,
                                  PushService pushService,
                                  PropiedadesMonitoreo propiedadesMonitoreo) {
        this.rioRepository = rioRepository;
        this.proveedorDatos = proveedorDatos;
        this.evaluador = evaluador;
        this.alertaEventoService = alertaEventoService;
        this.pushService = pushService;
        this.propiedadesMonitoreo = propiedadesMonitoreo;
    }

    /**
     * Ejecuta el ciclo programado de monitoreo sobre todos los ríos registrados
     * que tengan un identificador de GEOGloWS configurado.
     */
    @Scheduled(
            fixedDelayString = "${alerta.monitoreo.intervalo-ms:900000}",
            initialDelayString = "${alerta.monitoreo.retraso-inicial-ms:60000}"
    )
    public void monitorearRios() {
        if (!propiedadesMonitoreo.isHabilitado()) {
            return;
        }
        List<Rio> rios = rioRepository.findAll();
        for (Rio rio : rios) {
            if (rio.getGeoglowsId() == null) {
                continue;
            }
            try {
                ResultadoMonitoreo resultado = evaluarYProcesar(rio, "PROGRAMADO");
                if (resultado.eventoId() != null) {
                    log.info("Monitoreo tramo {}: evento {} ({}).", rio.getGeoglowsId(),
                            resultado.eventoId(), resultado.mensaje());
                }
            } catch (Exception e) {
                // Un tramo con problemas no debe impedir evaluar los demás.
                log.warn("No se pudo monitorear el río {} (reachId {}): {}",
                        rio.getNombre(), rio.getGeoglowsId(), e.getMessage());
            }
        }
    }

    /**
     * Busca un río por su identificador de GEOGloWS y ejecuta su evaluación completa.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param origen procedencia de la solicitud de evaluación
     * @return resumen del resultado de la evaluación y envío de notificaciones
     * @throws IllegalArgumentException si no existe un río configurado para el {@code reachId}
     * @throws Exception si falla la obtención de datos o el procesamiento del tramo
     */
    public ResultadoMonitoreo evaluarPorReachId(Long reachId, String origen) throws Exception {
        Rio rio = rioRepository.findFirstByGeoglowsId(reachId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay un río configurado con el reachId " + reachId + "."));
        return evaluarYProcesar(rio, origen);
    }

    /**
     * Evalúa un río obteniendo sus datos actuales, registrando posibles eventos
     * y despachando notificaciones push si corresponde, evitando ejecuciones concurrentes
     * sobre el mismo tramo.
     *
     * @param rio entidad del río a evaluar
     * @param origen procedencia de la evaluación
     * @return resumen del estado obtenido y de la notificación generada
     * @throws Exception si ocurre un error al consultar las fuentes externas o persistir el resultado
     */
    public ResultadoMonitoreo evaluarYProcesar(Rio rio, String origen) throws Exception {
        ReentrantLock bloqueo = bloqueos.computeIfAbsent(rio.getGeoglowsId(), id -> new ReentrantLock());
        if (!bloqueo.tryLock()) {
            return new ResultadoMonitoreo(rio.getGeoglowsId(), null, null, false, false,
                    "Ya hay una evaluación en curso para este tramo.");
        }
        try {
            DatosRiesgoTramo datos = proveedorDatos.obtener(
                    rio.getGeoglowsId(), rio.getNombre(), rio.getLatitud(), rio.getLongitud());
            EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

            Optional<AlertaEvento> evento = alertaEventoService.registrarEvaluacion(datos, evaluacion, origen);
            if (evento.isEmpty()) {
                return new ResultadoMonitoreo(
                        rio.getGeoglowsId(),
                        null,
                        evaluacion.nivel().name(),
                        evaluacion.confiable(),
                        false,
                        evaluacion.confiable()
                                ? "Sin cambio de nivel; no se creó un evento nuevo."
                                : "Datos no confiables; no se creó alerta."
                );
            }

            AlertaEvento creado = evento.get();
            boolean notificado = false;
            if (creado.getEstadoEnvio() == EstadoEnvio.PENDIENTE) {
                pushService.enviarParaEvento(creado);
                notificado = creado.getEstadoEnvio() == EstadoEnvio.ENVIADA
                        || creado.getEstadoEnvio() == EstadoEnvio.PARCIAL;
            }
            return new ResultadoMonitoreo(
                    rio.getGeoglowsId(),
                    creado.getId(),
                    evaluacion.nivel().name(),
                    evaluacion.confiable(),
                    notificado,
                    "Evento " + creado.getId() + " registrado con estado de envío " + creado.getEstadoEnvio() + "."
            );
        } finally {
            bloqueo.unlock();
        }
    }

    /**
     * Resumen devuelto tras evaluar un tramo en el ciclo de monitoreo.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param eventoId identificador del evento generado, o {@code null} si no hubo cambio
     * @param nivel nivel de alerta resultante de la evaluación
     * @param datosConfiables indica si los datos de entrada cumplen criterios de vigencia
     * @param notificado indica si se enviaron notificaciones push asociadas al evento
     * @param mensaje descripción breve del resultado obtenido
     */
    public record ResultadoMonitoreo(
            Long reachId,
            Long eventoId,
            String nivel,
            boolean datosConfiables,
            boolean notificado,
            String mensaje
    ) {
    }
}
