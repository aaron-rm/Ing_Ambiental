package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.ClienteApiGeoglows;
import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados;
import com.alerta.sistema_alerta_inundaciones.clima.DatosPrecipitacion;
import com.alerta.sistema_alerta_inundaciones.riesgo.DatosRiesgoTramo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluacionRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluadorRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.ProveedorDatosRiesgo;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AlertaService {

    private final SuscripcionAlertaRepository suscripcionRepository;
    private final RioRepository rioRepository;
    private final ProveedorDatosRiesgo proveedorDatos;
    private final EvaluadorRiesgo evaluador;
    private final ClienteApiGeoglows clienteGeoglows;
    private final AlertaEventoRepository eventoRepository;

    public AlertaService(SuscripcionAlertaRepository suscripcionRepository,
                         RioRepository rioRepository,
                         ProveedorDatosRiesgo proveedorDatos,
                         EvaluadorRiesgo evaluador,
                         ClienteApiGeoglows clienteGeoglows,
                         AlertaEventoRepository eventoRepository) {
        this.suscripcionRepository = suscripcionRepository;
        this.rioRepository = rioRepository;
        this.proveedorDatos = proveedorDatos;
        this.evaluador = evaluador;
        this.clienteGeoglows = clienteGeoglows;
        this.eventoRepository = eventoRepository;
    }

    /**
     * Consulta el pronóstico en GEOGloWS y la precipitación, evalúa el nivel de
     * riesgo y genera la respuesta consolidada. No crea eventos ni envía push:
     * eso corresponde al monitoreo programado o a una evaluación administrativa.
     */
    public AlertaRioDTO consultarAlertaTramo(long reachId, String nombreRio)
            throws IOException, InterruptedException {
        Optional<Rio> rio = rioRepository.findFirstByGeoglowsId(reachId);
        AlertaRioDTO dto = consultarInterno(
                reachId,
                nombreRio != null ? nombreRio : rio.map(Rio::getNombre).orElse("Tramo " + reachId),
                rio.map(Rio::getLatitud).orElse(null),
                rio.map(Rio::getLongitud).orElse(null)
        );
        rio.ifPresent(value -> dto.setRioId(value.getId()));
        return dto;
    }

    /** Consulta la alerta utilizando el id de un Río guardado en PostgreSQL. */
    public AlertaRioDTO consultarAlertaPorRioId(Long rioId) throws IOException, InterruptedException {
        Rio rio = rioRepository.findById(rioId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el río con ID: " + rioId));
        if (rio.getGeoglowsId() == null) {
            throw new IllegalArgumentException("El río no tiene un reachId de GEOGloWS configurado.");
        }
        AlertaRioDTO dto = consultarInterno(rio.getGeoglowsId(), rio.getNombre(), rio.getLatitud(), rio.getLongitud());
        dto.setRioId(rioId);
        return dto;
    }

    private AlertaRioDTO consultarInterno(long reachId, String nombreRio, Double latitud, Double longitud)
            throws IOException, InterruptedException {
        DatosRiesgoTramo datos = proveedorDatos.obtener(reachId, nombreRio, latitud, longitud);
        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);
        AlertaRioDTO dto = mapear(datos, evaluacion);
        eventoRepository.findTopByReachIdOrderByFechaCreacionDesc(reachId)
                .ifPresent(evento -> dto.setAlertaEventoId(evento.getId()));
        return dto;
    }

    private AlertaRioDTO mapear(DatosRiesgoTramo datos, EvaluacionRiesgo evaluacion) {
        AlertaRioDTO dto = new AlertaRioDTO();
        dto.setReachId(evaluacion.reachId());
        dto.setNombreRio(evaluacion.nombreRio());
        dto.setNivelAlerta(evaluacion.nivel().name());
        dto.setCaudalActualOInicial(evaluacion.caudalActual());
        dto.setCaudalMaximoPronosticado(evaluacion.caudalMaximo());
        dto.setUmbralVigilancia(evaluacion.umbralVigilancia());
        dto.setUmbralAlerta(evaluacion.umbralAlerta());
        dto.setUmbralEmergencia(evaluacion.umbralEmergencia());
        dto.setMensaje(evaluacion.mensaje());
        dto.setEstadoDatos(evaluacion.estadoDatos().name());
        dto.setDatosConfiables(evaluacion.confiable());
        dto.setFechaEvaluacion(evaluacion.fechaEvaluacion());
        dto.setFechaEmisionCaudal(evaluacion.fechaEmisionCaudal());
        dto.setFechaActualizacionPrecipitacion(evaluacion.fechaDatosPrecipitacion());
        dto.setFuenteCaudal(evaluacion.fuenteCaudal());
        dto.setFuentePrecipitacion(evaluacion.fuentePrecipitacion());
        dto.setFuenteUmbrales(evaluacion.fuenteUmbrales());
        dto.setNivelRiesgoCaudal(evaluacion.nivelCaudal().name());
        dto.setNivelRiesgoLluvia(evaluacion.nivelLluvia().name());
        dto.setLluvia24hMm(evaluacion.lluvia24hMm());
        dto.setLluvia72hMm(evaluacion.lluvia72hMm());
        dto.setFactoresRiesgo(evaluacion.factores());
        dto.setAdvertencias(evaluacion.advertencias());
        dto.setRecomendaciones(recomendaciones(evaluacion));
        dto.setSimulacion(false);

        if (datos.precipitacion() != null && datos.precipitacion().disponible()) {
            dto.setMaxLluviaHorariaMm(datos.precipitacion().maxLluviaHorariaMm());
            dto.setPronosticoLluvia(datos.precipitacion().serie().stream()
                    .filter(p -> !p.fecha().isBefore(Instant.now().minusSeconds(3600)))
                    .map(p -> new AlertaRioDTO.PuntoLluviaDTO(p.fecha(), p.mm()))
                    .toList());
        }

        List<AlertaRioDTO.PuntoPronosticoDTO> puntos = new ArrayList<>();
        Map<LocalDate, List<Double>> valoresPorDia = new LinkedHashMap<>();
        ZoneId utc = ZoneId.of("UTC");
        for (DatosRiesgoTramo.PuntoCaudal punto : datos.pronostico()) {
            puntos.add(new AlertaRioDTO.PuntoPronosticoDTO(punto.fecha(), punto.caudal()));
            valoresPorDia.computeIfAbsent(punto.fecha().atZone(utc).toLocalDate(), k -> new ArrayList<>())
                    .add(punto.caudal());
        }
        dto.setPronostico(puntos);

        List<PronosticoDiaDTO> dias = new ArrayList<>();
        int numeroDia = 1;
        for (Map.Entry<LocalDate, List<Double>> entrada : valoresPorDia.entrySet()) {
            List<Double> valores = entrada.getValue();
            double promedio = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double minimo = valores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maximo = valores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            String estadoDia = "NORMAL";
            if (maximo >= evaluacion.umbralEmergencia()) {
                estadoDia = "PELIGRO DE INUNDACION";
            } else if (maximo >= evaluacion.umbralAlerta()) {
                estadoDia = "RIESGO DE INUNDACION";
            } else if (maximo >= evaluacion.umbralVigilancia()) {
                estadoDia = "VIGILANCIA";
            }
            dias.add(new PronosticoDiaDTO(numeroDia++, entrada.getKey(), promedio, minimo, maximo, estadoDia));
        }
        dto.setPronosticoDiario(dias);
        return dto;
    }

    private List<String> recomendaciones(EvaluacionRiesgo evaluacion) {
        List<String> recomendaciones = new ArrayList<>(recomendacionesPorNivel(evaluacion.nivel()));
        if (evaluacion.nivelLluvia().ordinal() > evaluacion.nivelCaudal().ordinal()) {
            recomendaciones.add("El riesgo proviene principalmente de la lluvia pronosticada: "
                    + "aunque el caudal todavía sea bajo, pueden ocurrir crecidas repentinas.");
        }
        if (!evaluacion.confiable()) {
            recomendaciones.add("Los datos no son completamente confiables; toma esta estimación "
                    + "como orientativa y consulta los avisos oficiales.");
        }
        return List.copyOf(recomendaciones);
    }

    private List<String> recomendacionesPorNivel(LectorDatosIngresados.NivelAlerta nivel) {
        return switch (nivel) {
            case EMERGENCIA -> List.of(
                    "Aléjate de la ribera y de las zonas bajas o inundables.",
                    "Busca un lugar alto y sigue las indicaciones de SINAPROC y de las autoridades locales.",
                    "No intentes cruzar calles, puentes ni cauces con agua.");
            case ALERTA -> List.of(
                    "Evita acercarte al cauce y a las zonas propensas a inundación.",
                    "Prepara un plan de salida y mantente atento a los avisos oficiales.",
                    "No dejes objetos ni vehículos en zonas bajas.");
            case VIGILANCIA -> List.of(
                    "Mantente atento a la evolución del río y a los avisos oficiales.",
                    "Revisa que tu familia y tus vecinos conozcan una ruta de evacuación.");
            case NORMAL -> List.of(
                    "No se requiere una acción inmediata.",
                    "Mantente informado por los canales oficiales.");
        };
    }

    /**
     * Simula un caudal para demostrar la clasificación de niveles. No crea un
     * evento persistido ni envía notificaciones push reales.
     */
    public AlertaRioDTO simularInundacion(long reachId, double caudalSimulado)
            throws IOException, InterruptedException {
        LectorDatosIngresados.UmbralAlerta umbrales;
        try {
            umbrales = clienteGeoglows.obtenerUmbrales(reachId);
        } catch (IOException e) {
            umbrales = null;
        }

        double uVig = umbrales != null ? umbrales.caudalVigilancia : 15.0;
        double uAlt = umbrales != null ? umbrales.caudalAlerta : 30.0;
        double uEmg = umbrales != null ? umbrales.caudalEmergencia : 60.0;

        LectorDatosIngresados.NivelAlerta nivel;
        if (caudalSimulado >= uEmg) {
            nivel = LectorDatosIngresados.NivelAlerta.EMERGENCIA;
        } else if (caudalSimulado >= uAlt) {
            nivel = LectorDatosIngresados.NivelAlerta.ALERTA;
        } else if (caudalSimulado >= uVig) {
            nivel = LectorDatosIngresados.NivelAlerta.VIGILANCIA;
        } else {
            nivel = LectorDatosIngresados.NivelAlerta.NORMAL;
        }

        AlertaRioDTO sim = new AlertaRioDTO();
        sim.setReachId(reachId);
        sim.setNombreRio("Simulacion - Tramo " + reachId);
        sim.setCaudalActualOInicial(caudalSimulado);
        sim.setCaudalMaximoPronosticado(caudalSimulado);
        sim.setUmbralVigilancia(uVig);
        sim.setUmbralAlerta(uAlt);
        sim.setUmbralEmergencia(uEmg);
        sim.setNivelAlerta(nivel.name());
        sim.setNivelRiesgoCaudal(nivel.name());
        sim.setNivelRiesgoLluvia("NO_EVALUADO");
        sim.setEstadoDatos("SIMULACION");
        sim.setDatosConfiables(false);
        sim.setSimulacion(true);
        sim.setFuenteCaudal("Escenario simulado (no es un dato real)");
        sim.setFuenteUmbrales(umbrales != null ? umbrales.fuenteCalibracion : "Configuración de referencia");
        sim.setFechaEvaluacion(Instant.now());
        sim.setMensaje("SIMULACION: " + generarMensajeAlerta(nivel, caudalSimulado));
        sim.setFactoresRiesgo(List.of(
                "Escenario simulado con caudal de " + String.format(Locale.US, "%.2f", caudalSimulado) + " m³/s.",
                "No se consultaron datos de precipitación para la simulación."
        ));
        sim.setAdvertencias(List.of(
                "Esta simulación no representa una condición real y no envía notificaciones push."
        ));
        sim.setRecomendaciones(recomendacionesPorNivel(nivel));
        return sim;
    }

    public SuscripcionAlerta suscribirUsuario(Long reachId, String nombreContacto, String contacto) {
        String nombreFinal = (nombreContacto == null || nombreContacto.isBlank()) ? "Usuario Demo" : nombreContacto.trim();
        String contactoFinal = (contacto == null || contacto.isBlank()) ? "usuario@demo.com" : contacto.trim();

        if (!"Usuario Demo".equalsIgnoreCase(nombreFinal) && !ValidadorContacto.esNombreValido(nombreFinal)) {
            throw new IllegalArgumentException("Nombre de contacto invalido. Debe contener letras y un formato valido.");
        }
        if (!"usuario@demo.com".equalsIgnoreCase(contactoFinal) && !ValidadorContacto.esContactoValido(contactoFinal)) {
            throw new IllegalArgumentException("Contacto invalido. Debe ser un correo electronico valido o celular de Panama (8 digitos iniciando con 6, ej: 6123-4567).");
        }

        SuscripcionAlerta suscripcion = new SuscripcionAlerta(reachId, nombreFinal, contactoFinal);
        return suscripcionRepository.save(suscripcion);
    }

    public List<SuscripcionAlerta> obtenerSuscripcionesPorTramo(Long reachId) {
        return suscripcionRepository.findByReachId(reachId);
    }

    public List<AlertaEventoDTO> obtenerHistorial(Long reachId, int limite) {
        int maximo = Math.min(Math.max(limite, 1), 100);
        return eventoRepository.findByReachIdOrderByFechaCreacionDesc(reachId, PageRequest.of(0, maximo))
                .stream()
                .map(AlertaEventoDTO::desde)
                .toList();
    }

    public AlertaEventoDTO obtenerEvento(Long eventoId) {
        AlertaEvento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la alerta con ID: " + eventoId));
        return AlertaEventoDTO.desde(evento);
    }

    private String generarMensajeAlerta(LectorDatosIngresados.NivelAlerta nivel, double caudalMax) {
        return switch (nivel) {
            case EMERGENCIA -> "PELIGRO DE INUNDACION: Caudal simulado (" + String.format(Locale.US, "%.2f", caudalMax)
                    + " m3/s) supera el umbral de emergencia.";
            case ALERTA -> "RIESGO DE INUNDACION: Caudal simulado (" + String.format(Locale.US, "%.2f", caudalMax)
                    + " m3/s) supera el umbral de alerta.";
            case VIGILANCIA -> "VIGILANCIA: Caudal simulado (" + String.format(Locale.US, "%.2f", caudalMax)
                    + " m3/s) por encima de niveles habituales.";
            case NORMAL -> "NORMAL: Caudal simulado (" + String.format(Locale.US, "%.2f", caudalMax)
                    + " m3/s) por debajo de los umbrales de riesgo.";
        };
    }
}
