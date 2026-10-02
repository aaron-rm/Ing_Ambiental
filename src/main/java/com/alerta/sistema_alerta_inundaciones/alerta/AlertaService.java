package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.GeneradorAlertaFinal;
import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AlertaService {

    private final SuscripcionAlertaRepository suscripcionRepository;
    private final RioRepository rioRepository;
    private final GeneradorAlertaFinal generadorAlerta = new GeneradorAlertaFinal();

    public AlertaService(SuscripcionAlertaRepository suscripcionRepository, RioRepository rioRepository) {
        this.suscripcionRepository = suscripcionRepository;
        this.rioRepository = rioRepository;
    }

    /**
     * Consulta el pronóstico en GEOGloWS, evalúa el nivel de riesgo según los umbrales
     * y genera la respuesta consolidada.
     */
    public AlertaRioDTO consultarAlertaTramo(long reachId, String nombreRio) throws IOException, InterruptedException {
        LectorDatosIngresados repo = new LectorDatosIngresados();
        repo.sincronizarTramo(reachId, "cuenca", "N/D");

        LectorDatosIngresados.PronosticoCaudal p = repo.pronosticos.get(reachId);
        LectorDatosIngresados.UmbralAlerta u = repo.umbrales.get(reachId);
        LectorDatosIngresados.NivelAlerta nivel = generadorAlerta.evaluarNivel(repo, reachId);

        AlertaRioDTO dto = new AlertaRioDTO();
        dto.setReachId(reachId);
        dto.setNombreRio(nombreRio != null ? nombreRio : "Tramo " + reachId);
        dto.setNivelAlerta(nivel.name());

        if (u != null) {
            dto.setUmbralVigilancia(u.caudalVigilancia);
            dto.setUmbralAlerta(u.caudalAlerta);
            dto.setUmbralEmergencia(u.caudalEmergencia);
        }

        if (p != null && !p.mediaSerie.isEmpty()) {
            dto.setCaudalActualOInicial(p.mediaSerie.get(0).valor);
            double maxP = p.mediaSerie.stream().mapToDouble(pt -> pt.valor).max().orElse(0.0);
            dto.setCaudalMaximoPronosticado(maxP);

            List<AlertaRioDTO.PuntoPronosticoDTO> puntos = new ArrayList<>();
            java.util.Map<java.time.LocalDate, List<Double>> valoresPorDia = new java.util.LinkedHashMap<>();
            java.time.ZoneId utcZone = java.time.ZoneId.of("UTC");

            for (LectorDatosIngresados.SerieTemporalPunto pt : p.mediaSerie) {
                puntos.add(new AlertaRioDTO.PuntoPronosticoDTO(pt.fecha, pt.valor));
                java.time.LocalDate fechaDia = pt.fecha.atZone(utcZone).toLocalDate();
                valoresPorDia.computeIfAbsent(fechaDia, k -> new ArrayList<>()).add(pt.valor);
            }
            dto.setPronostico(puntos);

            List<PronosticoDiaDTO> dias = new ArrayList<>();
            int numDia = 1;
            for (java.util.Map.Entry<java.time.LocalDate, List<Double>> entry : valoresPorDia.entrySet()) {
                List<Double> vals = entry.getValue();
                double avg = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                String estadoDia = "NORMAL";
                if (u != null) {
                    if (max >= u.caudalEmergencia) estadoDia = "PELIGRO DE INUNDACION";
                    else if (max >= u.caudalAlerta) estadoDia = "RIESGO DE INUNDACION";
                    else if (max >= u.caudalVigilancia) estadoDia = "VIGILANCIA";
                }
                dias.add(new PronosticoDiaDTO(numDia++, entry.getKey(), avg, max, estadoDia));
            }
            dto.setPronosticoDiario(dias);
        }

        dto.setMensaje(generarMensajeAlerta(nivel, dto.getCaudalMaximoPronosticado(), dto.getUmbralVigilancia()));
        return dto;
    }

    /**
     * Consulta la alerta utilizando el id de un Río guardado en la base de datos PostgreSQL.
     */
    public AlertaRioDTO consultarAlertaPorRioId(Long rioId) throws IOException, InterruptedException {
        Optional<Rio> rioOpt = rioRepository.findById(rioId);
        if (rioOpt.isEmpty()) {
            throw new IllegalArgumentException("No se encontró el río con ID: " + rioId);
        }
        Rio rio = rioOpt.get();
        return consultarAlertaTramo(rio.getGeoglowsId(), rio.getNombre());
    }

    /**
     * Simula un caudal alto para demostrar el flujo de detección y disparo de alerta de inundación.
     */
    public AlertaRioDTO simularInundacion(long reachId, double caudalSimulado) throws IOException, InterruptedException {
        AlertaRioDTO alertaBase = consultarAlertaTramo(reachId, "Simulación - Tramo " + reachId);
        alertaBase.setCaudalMaximoPronosticado(caudalSimulado);
        alertaBase.setCaudalActualOInicial(caudalSimulado);

        if (caudalSimulado >= alertaBase.getUmbralEmergencia()) {
            alertaBase.setNivelAlerta(LectorDatosIngresados.NivelAlerta.EMERGENCIA.name());
        } else if (caudalSimulado >= alertaBase.getUmbralAlerta()) {
            alertaBase.setNivelAlerta(LectorDatosIngresados.NivelAlerta.ALERTA.name());
        } else if (caudalSimulado >= alertaBase.getUmbralVigilancia()) {
            alertaBase.setNivelAlerta(LectorDatosIngresados.NivelAlerta.VIGILANCIA.name());
        } else {
            alertaBase.setNivelAlerta(LectorDatosIngresados.NivelAlerta.NORMAL.name());
        }

        alertaBase.setMensaje("[SIMULACIÓN] " + generarMensajeAlerta(
                LectorDatosIngresados.NivelAlerta.valueOf(alertaBase.getNivelAlerta()),
                caudalSimulado,
                alertaBase.getUmbralVigilancia()
        ));
        return alertaBase;
    }

    /**
     * Registra una suscripción para recibir alertas cuando se detecte riesgo.
     */
    public SuscripcionAlerta suscribirUsuario(Long reachId, String nombreContacto, String contacto) {
        SuscripcionAlerta suscripcion = new SuscripcionAlerta(reachId, nombreContacto, contacto);
        return suscripcionRepository.save(suscripcion);
    }

    public List<SuscripcionAlerta> obtenerSuscripcionesPorTramo(Long reachId) {
        return suscripcionRepository.findByReachId(reachId);
    }

    private String generarMensajeAlerta(LectorDatosIngresados.NivelAlerta nivel, double caudalMax, double umbralVigilancia) {
        switch (nivel) {
            case EMERGENCIA:
                return "PELIGRO DE INUNDACION: Caudal previsto (" + String.format(java.util.Locale.US, "%.2f", caudalMax) +
                        " m3/s) supera el umbral de emergencia. Riesgo de desbordamiento severo.";
            case ALERTA:
                return "RIESGO DE INUNDACION: Caudal previsto (" + String.format(java.util.Locale.US, "%.2f", caudalMax) +
                        " m3/s) supera el umbral de alerta. Posible afectacion a zonas riberenas.";
            case VIGILANCIA:
                return "VIGILANCIA: Caudal previsto (" + String.format(java.util.Locale.US, "%.2f", caudalMax) +
                        " m3/s) por encima de niveles habituales. Monitoreo preventivo.";
            case NORMAL:
            default:
                return "NORMAL: Caudal previsto (" + String.format(java.util.Locale.US, "%.2f", caudalMax) +
                        " m3/s) se mantiene por debajo de los umbrales de riesgo.";
        }
    }
}
