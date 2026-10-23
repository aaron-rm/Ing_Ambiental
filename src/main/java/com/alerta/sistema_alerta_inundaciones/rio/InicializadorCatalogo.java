package com.alerta.sistema_alerta_inundaciones.rio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * Carga el catálogo cerrado de cuatro ríos desde {@code catalogo/rios.json}.
 * Hace upsert por {@code reachId}: corrige el nombre del río inicial heredado
 * ("Río Pacora" era en realidad Río Abajo) sin duplicar filas ni borrar ríos
 * que otros integrantes hayan agregado a su base local.
 */
@Component
public class InicializadorCatalogo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorCatalogo.class);
    private static final String RECURSO = "catalogo/rios.json";

    private final RioRepository rioRepository;
    private final boolean cargarCatalogo;
    private final ObjectMapper mapper = new ObjectMapper();

    public InicializadorCatalogo(
            RioRepository rioRepository,
            @Value("${alerta.demo.cargar-catalogo:true}") boolean cargarCatalogo) {
        this.rioRepository = rioRepository;
        this.cargarCatalogo = cargarCatalogo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!cargarCatalogo) {
            return;
        }

        List<RioCatalogo> catalogo;
        try (InputStream entrada = new ClassPathResource(RECURSO).getInputStream()) {
            catalogo = mapper.readValue(entrada, new TypeReference<List<RioCatalogo>>() { });
        } catch (Exception e) {
            log.error("No se pudo leer {}. El catálogo de ríos no se cargó: {}",
                    RECURSO, e.getMessage());
            return;
        }

        int creados = 0;
        int actualizados = 0;
        for (RioCatalogo datos : catalogo) {
            validar(datos);
            Rio rio = rioRepository.findFirstByGeoglowsId(datos.reachId())
                    .orElseGet(Rio::new);
            boolean nuevo = rio.getId() == null;

            rio.setNombre(datos.nombre());
            rio.setGeoglowsId(datos.reachId());
            rio.setLatitud(datos.latitud());
            rio.setLongitud(datos.longitud());
            rio.setGeometria(datos.geometria().toString());
            rioRepository.save(rio);

            if (nuevo) {
                creados++;
            } else {
                actualizados++;
            }
        }

        long total = rioRepository.count();
        log.info("Catálogo de ríos cargado: {} creados, {} actualizados, {} ríos en la base de datos.",
                creados, actualizados, total);
        if (total > catalogo.size()) {
            log.warn("La base de datos contiene {} ríos además del catálogo cerrado. "
                    + "No se borraron para no destruir datos locales.", total - catalogo.size());
        }
    }

    private static void validar(RioCatalogo datos) {
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            throw new IllegalStateException("El catálogo contiene un río sin nombre.");
        }
        if (datos.reachId() == null) {
            throw new IllegalStateException("El río " + datos.nombre() + " no tiene reachId.");
        }
        if (datos.latitud() == null || datos.longitud() == null) {
            throw new IllegalStateException("El río " + datos.nombre() + " no tiene coordenadas.");
        }
        if (datos.geometria() == null || datos.geometria().isNull()) {
            throw new IllegalStateException("El río " + datos.nombre() + " no tiene geometría.");
        }
    }

    /** Forma de cada elemento de {@code catalogo/rios.json}. */
    private record RioCatalogo(
            String nombre,
            Long reachId,
            Double latitud,
            Double longitud,
            List<Long> tramos,
            JsonNode geometria
    ) {
    }
}
