package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.ClienteApiGeoglows;
import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados;
import com.alerta.sistema_alerta_inundaciones.clima.DatosPrecipitacion;
import com.alerta.sistema_alerta_inundaciones.clima.ProveedorPrecipitacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reutiliza el cliente y el modelo existentes de GEOGloWS y añade la consulta
 * de precipitación. Si Open-Meteo falla, el caudal sigue siendo evaluable y la
 * falta de lluvia queda registrada como advertencia.
 */
@Service
public class ProveedorDatosRiesgoGeoglows implements ProveedorDatosRiesgo {

    private static final Logger log = LoggerFactory.getLogger(ProveedorDatosRiesgoGeoglows.class);

    private final ClienteApiGeoglows clienteGeoglows;
    private final ProveedorPrecipitacion proveedorPrecipitacion;

    public ProveedorDatosRiesgoGeoglows(ClienteApiGeoglows clienteGeoglows,
                                        ProveedorPrecipitacion proveedorPrecipitacion) {
        this.clienteGeoglows = clienteGeoglows;
        this.proveedorPrecipitacion = proveedorPrecipitacion;
    }

    @Override
    public DatosRiesgoTramo obtener(long reachId, String nombreRio, Double latitud, Double longitud)
            throws IOException, InterruptedException {
        LectorDatosIngresados lector = new LectorDatosIngresados(clienteGeoglows);
        lector.sincronizarTramo(reachId, "cuenca", "N/D");

        LectorDatosIngresados.PronosticoCaudal pronostico = lector.pronosticos.get(reachId);
        LectorDatosIngresados.UmbralAlerta umbrales = lector.umbrales.get(reachId);

        if (pronostico == null || pronostico.mediaSerie == null || pronostico.mediaSerie.isEmpty()) {
            throw new IOException("GEOGloWS no devolvió una serie de caudal utilizable para el tramo " + reachId + ".");
        }
        if (umbrales == null) {
            throw new IOException("No se pudieron obtener los umbrales de caudal para el tramo " + reachId + ".");
        }

        Instant ahora = Instant.now();
        List<DatosRiesgoTramo.PuntoCaudal> todos = new ArrayList<>();
        for (LectorDatosIngresados.SerieTemporalPunto punto : pronostico.mediaSerie) {
            if (punto != null && punto.fecha != null && Double.isFinite(punto.valor)) {
                todos.add(new DatosRiesgoTramo.PuntoCaudal(punto.fecha, punto.valor));
            }
        }
        if (todos.isEmpty()) {
            throw new IOException("GEOGloWS devolvió puntos de caudal sin fecha o valor válido.");
        }

        // Para el riesgo se prioriza el horizonte futuro; si la serie ya no tiene
        // futuro (dato viejo), se conserva la serie completa para que el evaluador
        // pueda marcarla como desactualizada.
        List<DatosRiesgoTramo.PuntoCaudal> futuros = todos.stream()
                .filter(p -> !p.fecha().isBefore(ahora.minus(Duration.ofHours(1))))
                .sorted(Comparator.comparing(DatosRiesgoTramo.PuntoCaudal::fecha))
                .toList();
        List<DatosRiesgoTramo.PuntoCaudal> base = futuros.isEmpty() ? todos : futuros;

        double caudalMaximo = base.stream().mapToDouble(DatosRiesgoTramo.PuntoCaudal::caudal).max().orElse(0.0);
        DatosRiesgoTramo.PuntoCaudal puntoActual = base.stream()
                .min(Comparator.comparingLong(p -> Math.abs(Duration.between(ahora, p.fecha()).toSeconds())))
                .orElse(base.get(0));

        List<String> advertencias = new ArrayList<>();
        if (umbrales.fuenteCalibracion != null
                && umbrales.fuenteCalibracion.toLowerCase(java.util.Locale.ROOT).contains("referencia")) {
            advertencias.add("GEOGloWS no entregó periodos de retorno utilizables; se usan umbrales de referencia.");
        }

        DatosPrecipitacion precipitacion;
        boolean coordenadasValidas = latitud != null && longitud != null
                && Double.isFinite(latitud) && Double.isFinite(longitud)
                && !(latitud == 0.0 && longitud == 0.0);
        if (coordenadasValidas) {
            try {
                precipitacion = proveedorPrecipitacion.obtener(latitud, longitud);
            } catch (RuntimeException e) {
                log.warn("Fallo inesperado al consultar precipitación para el tramo {}: {}", reachId, e.getMessage());
                precipitacion = DatosPrecipitacion.noDisponible(latitud, longitud,
                        "No se pudo consultar la precipitación: " + e.getMessage());
            }
        } else {
            precipitacion = DatosPrecipitacion.noDisponible(0.0, 0.0,
                    "El río no tiene coordenadas configuradas; no se consultó precipitación.");
            advertencias.add("Configure latitud y longitud del río para incorporar la precipitación.");
        }

        if (precipitacion.advertencia() != null) {
            advertencias.add(precipitacion.advertencia());
        }

        return new DatosRiesgoTramo(
                reachId,
                nombreRio != null && !nombreRio.isBlank() ? nombreRio : "Tramo " + reachId,
                latitud,
                longitud,
                puntoActual.caudal(),
                caudalMaximo,
                umbrales.caudalVigilancia,
                umbrales.caudalAlerta,
                umbrales.caudalEmergencia,
                pronostico.fechaEmision,
                "GEOGloWS API v2 (forecaststats/" + reachId + ")",
                umbrales.fuenteCalibracion,
                List.copyOf(base),
                precipitacion,
                List.copyOf(advertencias)
        );
    }
}
