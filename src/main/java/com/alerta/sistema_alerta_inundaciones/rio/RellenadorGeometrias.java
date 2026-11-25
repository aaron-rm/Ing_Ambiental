package com.alerta.sistema_alerta_inundaciones.rio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Tarea de arranque en segundo plano que busca ríos con {@code geoglowsId} pero
 * sin geometría GeoJSON guardada y la descarga desde el servicio de GEOGloWS.
 */
@Component
public class RellenadorGeometrias implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RellenadorGeometrias.class);
    private static final long PAUSA_ENTRE_PETICIONES_MS = 300L;

    private final RioRepository rioRepository;
    private final GeometriaTramoClient geometriaTramoClient;

    /**
     * Crea el rellenador de geometrías.
     *
     * @param rioRepository        repositorio de ríos
     * @param geometriaTramoClient cliente para consultar geometrías de tramos en GEOGloWS
     */
    public RellenadorGeometrias(RioRepository rioRepository, GeometriaTramoClient geometriaTramoClient) {
        this.rioRepository = rioRepository;
        this.geometriaTramoClient = geometriaTramoClient;
    }

    /**
     * Inicia un hilo demonio al arrancar la aplicación para completar las geometrías faltantes.
     *
     * @param args argumentos de arranque de la aplicación
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            Thread hilo = new Thread(this::rellenar, "rellenador-geometrias");
            hilo.setDaemon(true);
            hilo.start();
        } catch (Exception e) {
            log.warn("No se pudo iniciar el relleno de geometrías: {}", e.getMessage());
        }
    }

    // Recorre los ríos sin geometría y consulta GEOGloWS aplicando una pausa entre peticiones.
    private void rellenar() {
        try {
            List<Rio> pendientes = rioRepository.findSinGeometriaConGeoglowsId();
            if (pendientes == null || pendientes.isEmpty()) {
                return;
            }
            log.info("Rellenando geometrías faltantes para {} ríos.", pendientes.size());
            for (int i = 0; i < pendientes.size(); i++) {
                if (i > 0) {
                    Thread.sleep(PAUSA_ENTRE_PETICIONES_MS);
                }
                Rio rio = pendientes.get(i);
                if (rio.getGeoglowsId() == null) {
                    continue;
                }
                try {
                    Optional<String> geometria = geometriaTramoClient.buscar(rio.getGeoglowsId());
                    if (geometria.isPresent()) {
                        rio.setGeometria(geometria.get());
                        rioRepository.save(rio);
                        log.info("Geometría guardada para río {} (reachId={}).",
                                rio.getNombre(), rio.getGeoglowsId());
                    }
                } catch (Exception e) {
                    log.warn("No se pudo rellenar la geometría del río {} (reachId={}): {}",
                            rio.getNombre(), rio.getGeoglowsId(), e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Relleno de geometrías interrumpido.");
        } catch (Exception e) {
            log.warn("Error al rellenar geometrías de ríos existentes: {}", e.getMessage());
        }
    }
}
