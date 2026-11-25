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

    public ResultadoMonitoreo evaluarPorReachId(Long reachId, String origen) throws Exception {
        Rio rio = rioRepository.findFirstByGeoglowsId(reachId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay un río configurado con el reachId " + reachId + "."));
        return evaluarYProcesar(rio, origen);
    }

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
