package com.alerta.sistema_alerta_inundaciones.clima;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente de Open-Meteo para pronóstico horario de precipitación.
 *
 * Open-Meteo es una API pública documentada que no requiere clave para este uso.
 * Se consulta una sola vez por coordenada aproximada y se cachea durante unos
 * minutos para respetar límites de uso y evitar llamadas innecesarias.
 */
@Component
public class OpenMeteoCliente implements ProveedorPrecipitacion {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoCliente.class);
    private static final int DIAS_PRONOSTICO = 7;

    private final RestClient restClient;
    private final Duration cacheTtl;
    private final Map<String, EntradaCache> cache = new ConcurrentHashMap<>();

    @Autowired
    public OpenMeteoCliente(
            @Value("${clima.open-meteo.url:https://api.open-meteo.com/v1/forecast}") String url,
            @Value("${clima.open-meteo.cache-minutos:15}") long cacheMinutos) {
        this(RestClient.builder().baseUrl(url).build(), Duration.ofMinutes(Math.max(1, cacheMinutos)));
    }

    /** Constructor para pruebas con un servidor HTTP local. */
    OpenMeteoCliente(RestClient restClient, Duration cacheTtl) {
        this.restClient = restClient;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public DatosPrecipitacion obtener(double latitud, double longitud) {
        String clave = String.format(java.util.Locale.US, "%.3f,%.3f", latitud, longitud);
        EntradaCache entrada = cache.get(clave);
        Instant ahora = Instant.now();

        if (entrada != null && entrada.fechaConsulta.plus(cacheTtl).isAfter(ahora)) {
            return entrada.datos;
        }

        try {
            DatosPrecipitacion datos = consultar(latitud, longitud);
            if (datos.disponible()) {
                cache.put(clave, new EntradaCache(datos.fechaConsulta(), datos));
                return datos;
            }
            // Si la consulta falla pero existe caché previa, se devuelve marcada como
            // desactualizada para que la evaluación pueda degradar la confianza.
            if (entrada != null) {
                DatosPrecipitacion previa = entrada.datos;
                return new DatosPrecipitacion(
                        true, previa.latitud(), previa.longitud(), previa.fuente(),
                        previa.fechaConsulta(), previa.lluvia24hMm(), previa.lluvia72hMm(),
                        previa.maxLluviaHorariaMm(), previa.serie(),
                        "Open-Meteo no respondió; se muestran datos en caché de "
                                + previa.fechaConsulta() + ". " + datos.advertencia()
                );
            }
            return datos;
        } catch (Exception e) {
            log.warn("No se pudo consultar Open-Meteo para {},{}: {}", latitud, longitud, e.getMessage());
            if (entrada != null) {
                DatosPrecipitacion previa = entrada.datos;
                return new DatosPrecipitacion(
                        true, previa.latitud(), previa.longitud(), previa.fuente(),
                        previa.fechaConsulta(), previa.lluvia24hMm(), previa.lluvia72hMm(),
                        previa.maxLluviaHorariaMm(), previa.serie(),
                        "Open-Meteo no respondió; se muestran datos en caché de "
                                + previa.fechaConsulta() + "."
                );
            }
            return DatosPrecipitacion.noDisponible(latitud, longitud,
                    "No se pudo consultar Open-Meteo: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private DatosPrecipitacion consultar(double latitud, double longitud) {
        Map<String, Object> respuesta = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("latitude", latitud)
                        .queryParam("longitude", longitud)
                        .queryParam("hourly", "precipitation")
                        .queryParam("forecast_days", DIAS_PRONOSTICO)
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (respuesta == null) {
            return DatosPrecipitacion.noDisponible(latitud, longitud, "Open-Meteo devolvió una respuesta vacía.");
        }

        Object hourlyObj = respuesta.get("hourly");
        if (!(hourlyObj instanceof Map<?, ?> hourly)) {
            return DatosPrecipitacion.noDisponible(latitud, longitud, "Open-Meteo no devolvió la sección hourly.");
        }

        Object tiemposObj = hourly.get("time");
        Object valoresObj = hourly.get("precipitation");
        if (!(tiemposObj instanceof List<?> tiempos) || !(valoresObj instanceof List<?> valores)
                || tiempos.isEmpty() || tiempos.size() != valores.size()) {
            return DatosPrecipitacion.noDisponible(latitud, longitud,
                    "Open-Meteo devolvió series incompletas o vacías.");
        }

        Instant ahora = Instant.now();
        Instant limite24 = ahora.plus(Duration.ofHours(24));
        Instant limite72 = ahora.plus(Duration.ofHours(72));
        double lluvia24 = 0.0;
        double lluvia72 = 0.0;
        double maxHoraria = 0.0;
        int horas24 = 0;
        int horas72 = 0;
        List<DatosPrecipitacion.PuntoLluvia> serie = new ArrayList<>();

        for (int i = 0; i < tiempos.size(); i++) {
            Instant fecha = parsearFecha(String.valueOf(tiempos.get(i)));
            Object valorCrudo = valores.get(i);
            if (fecha == null || !(valorCrudo instanceof Number numero)) {
                continue;
            }
            double mm = numero.doubleValue();
            if (mm < 0) {
                continue;
            }
            serie.add(new DatosPrecipitacion.PuntoLluvia(fecha, mm));
            if (fecha.isBefore(ahora.minus(Duration.ofHours(1)))) {
                continue;
            }
            if (!fecha.isAfter(limite24)) {
                lluvia24 += mm;
                horas24++;
            }
            if (!fecha.isAfter(limite72)) {
                lluvia72 += mm;
                horas72++;
                maxHoraria = Math.max(maxHoraria, mm);
            }
        }

        if (horas24 == 0 && horas72 == 0) {
            return DatosPrecipitacion.noDisponible(latitud, longitud,
                    "Open-Meteo no devolvió horas futuras utilizables.");
        }

        return new DatosPrecipitacion(
                true, latitud, longitud, "Open-Meteo (api.open-meteo.com)",
                ahora, redondear(lluvia24), redondear(lluvia72), redondear(maxHoraria),
                List.copyOf(serie), null
        );
    }

    private static Instant parsearFecha(String texto) {
        try {
            // Se solicita timezone=UTC, por lo que las marcas vienen sin offset.
            return LocalDateTime.parse(texto).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            try {
                return Instant.parse(texto);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private record EntradaCache(Instant fechaConsulta, DatosPrecipitacion datos) {
    }
}
