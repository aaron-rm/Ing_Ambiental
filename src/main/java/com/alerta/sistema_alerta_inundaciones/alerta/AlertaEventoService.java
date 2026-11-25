package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesMonitoreo;
import com.alerta.sistema_alerta_inundaciones.riesgo.DatosRiesgoTramo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluacionRiesgo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Decide si una evaluación produce un evento nuevo y persiste el estado del
 * tramo de forma atómica. La comparación contra el estado persistido evita
 * duplicados tras reinicios y actualizaciones repetidas.
 */
@Service
public class AlertaEventoService {

    private static final Logger log = LoggerFactory.getLogger(AlertaEventoService.class);

    private final EstadoRiesgoTramoRepository estadoRepository;
    private final AlertaEventoRepository eventoRepository;
    private final PropiedadesMonitoreo propiedadesMonitoreo;

    public AlertaEventoService(EstadoRiesgoTramoRepository estadoRepository,
                               AlertaEventoRepository eventoRepository,
                               PropiedadesMonitoreo propiedadesMonitoreo) {
        this.estadoRepository = estadoRepository;
        this.eventoRepository = eventoRepository;
        this.propiedadesMonitoreo = propiedadesMonitoreo;
    }

    @Transactional
    public Optional<AlertaEvento> registrarEvaluacion(DatosRiesgoTramo datos,
                                                      EvaluacionRiesgo evaluacion,
                                                      String origen) {
        EstadoRiesgoTramo estado = estadoRepository.buscarParaActualizar(evaluacion.reachId())
                .orElseGet(() -> nuevoEstado(evaluacion.reachId()));
        NivelAlerta anterior = estado.getId() == null || estado.getNivelActual() == null
                ? NivelAlerta.NORMAL : estado.getNivelActual();

        if (!evaluacion.confiable()) {
            estado.setEvaluacionConfiable(false);
            estado.setCaudalMaximo(evaluacion.caudalMaximo());
            estado.setLluvia24hMm(evaluacion.lluvia24hMm());
            estado.setFechaEvaluacion(evaluacion.fechaEvaluacion());
            estado.setFechaDatos(evaluacion.fechaEmisionCaudal());
            if (estado.getId() == null) {
                estado.setNivelActual(NivelAlerta.NORMAL);
            }
            estadoRepository.save(estado);
            log.warn("Evaluación no confiable para el tramo {}: no se genera alerta.", evaluacion.reachId());
            return Optional.empty();
        }

        boolean cambio = estado.getId() == null
                ? evaluacion.nivel() != NivelAlerta.NORMAL
                : evaluacion.nivel() != anterior;

        AlertaEvento evento = null;
        if (cambio) {
            String clave = claveIdempotencia(evaluacion, anterior);
            Optional<AlertaEvento> existente = eventoRepository.findByClaveIdempotencia(clave);
            if (existente.isPresent()) {
                evento = existente.get();
            } else {
                evento = construirEvento(datos, evaluacion, anterior, clave, origen);
                evento.setEstadoEnvio(debeNotificar(evaluacion.nivel(), anterior)
                        ? EstadoEnvio.PENDIENTE : EstadoEnvio.OMITIDA);
                evento = eventoRepository.save(evento);
                log.info("Evento de alerta {} creado para el tramo {}: {} -> {}.",
                        evento.getId(), evaluacion.reachId(), anterior, evaluacion.nivel());
            }
        }

        estado.setNivelActual(evaluacion.nivel());
        estado.setEvaluacionConfiable(true);
        estado.setCaudalMaximo(evaluacion.caudalMaximo());
        estado.setLluvia24hMm(evaluacion.lluvia24hMm());
        estado.setFechaEvaluacion(evaluacion.fechaEvaluacion());
        estado.setFechaDatos(evaluacion.fechaEmisionCaudal());
        if (evento != null) {
            estado.setUltimaAlertaId(evento.getId());
        }
        estadoRepository.save(estado);

        return Optional.ofNullable(evento);
    }

    public boolean debeNotificar(NivelAlerta nivel, NivelAlerta anterior) {
        if (nivel == NivelAlerta.ALERTA || nivel == NivelAlerta.EMERGENCIA) {
            return true;
        }
        return nivel == NivelAlerta.NORMAL
                && anterior != null
                && anterior.ordinal() >= NivelAlerta.ALERTA.ordinal()
                && propiedadesMonitoreo.isNotificarRecuperacion();
    }

    private EstadoRiesgoTramo nuevoEstado(Long reachId) {
        EstadoRiesgoTramo estado = new EstadoRiesgoTramo();
        estado.setReachId(reachId);
        estado.setNivelActual(NivelAlerta.NORMAL);
        return estado;
    }

    private AlertaEvento construirEvento(DatosRiesgoTramo datos, EvaluacionRiesgo evaluacion,
                                         NivelAlerta anterior, String clave, String origen) {
        AlertaEvento evento = new AlertaEvento();
        evento.setReachId(evaluacion.reachId());
        evento.setNombreRio(evaluacion.nombreRio());
        evento.setNivel(evaluacion.nivel());
        evento.setNivelAnterior(anterior);
        evento.setNivelCaudal(evaluacion.nivelCaudal());
        evento.setNivelLluvia(evaluacion.nivelLluvia());
        evento.setEstadoDatos(evaluacion.estadoDatos());
        evento.setDatosConfiables(evaluacion.confiable());
        evento.setCaudalMaximo(evaluacion.caudalMaximo());
        evento.setUmbralVigilancia(evaluacion.umbralVigilancia());
        evento.setUmbralAlerta(evaluacion.umbralAlerta());
        evento.setUmbralEmergencia(evaluacion.umbralEmergencia());
        evento.setLluvia24hMm(evaluacion.lluvia24hMm());
        evento.setLluvia72hMm(evaluacion.lluvia72hMm());
        evento.setMensaje(evaluacion.mensaje());
        evento.setFactores(unir(evaluacion.factores()));
        evento.setAdvertencias(unir(evaluacion.advertencias()));
        evento.setFuenteCaudal(evaluacion.fuenteCaudal());
        evento.setFuentePrecipitacion(evaluacion.fuentePrecipitacion());
        evento.setFechaDatos(datos.fechaEmisionCaudal());
        evento.setFechaEvaluacion(evaluacion.fechaEvaluacion());
        evento.setClaveIdempotencia(clave);
        evento.setOrigen(origen);
        return evento;
    }

    private String claveIdempotencia(EvaluacionRiesgo evaluacion, NivelAlerta anterior) {
        // La fecha de emisión del pronóstico identifica la actualización de datos.
        // Si no existe, se usa la hora de evaluación para no bloquear futuros
        // episodios del mismo nivel.
        long referencia = evaluacion.fechaEmisionCaudal() != null
                ? evaluacion.fechaEmisionCaudal().toEpochMilli()
                : evaluacion.fechaEvaluacion().truncatedTo(ChronoUnit.HOURS).toEpochMilli();
        return evaluacion.reachId() + ":" + anterior + "->" + evaluacion.nivel() + ":" + referencia;
    }

    private static String unir(List<String> lineas) {
        return lineas == null || lineas.isEmpty() ? null : String.join("\n", lineas);
    }
}
