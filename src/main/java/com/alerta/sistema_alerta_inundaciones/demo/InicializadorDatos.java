package com.alerta.sistema_alerta_inundaciones.demo;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Crea un río inicial solo si la tabla está vacía, para que la PWA tenga una
 * zona demostrable sin sobrescribir datos de otros integrantes.
 */
@Component
public class InicializadorDatos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorDatos.class);

    private final RioRepository rioRepository;
    private final boolean crearRioInicial;
    private final long reachIdInicial;

    public InicializadorDatos(RioRepository rioRepository,
                              @Value("${alerta.demo.crear-rio-inicial:true}") boolean crearRioInicial,
                              @Value("${alerta.demo.reach-id-inicial:710039498}") long reachIdInicial) {
        this.rioRepository = rioRepository;
        this.crearRioInicial = crearRioInicial;
        this.reachIdInicial = reachIdInicial;
    }

    @Override
    public void run(String... args) {
        if (!crearRioInicial || rioRepository.count() > 0) {
            return;
        }
        Rio rio = new Rio("Rio Pacora (Panama)", reachIdInicial, 9.08, -79.28);
        rioRepository.save(rio);
        log.info("Río inicial creado: {} (reachId {}).", rio.getNombre(), rio.getGeoglowsId());
    }
}
