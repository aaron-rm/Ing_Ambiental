package com.alerta.sistema_alerta_inundaciones.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Elimina el CHECK heredado usuarios_rol_check (USER, ADMIN) y amplía la columna
 * rol en PostgreSQL para permitir el rol INVESTIGADOR sin romper el arranque.
 */
@Component
public class ReparadorCheckRol implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReparadorCheckRol.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    /**
     * Crea el reparador de la restricción CHECK de la columna {@code rol}.
     *
     * @param jdbcTemplateProvider proveedor opcional de {@link JdbcTemplate}
     */
    public ReparadorCheckRol(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /**
     * Ejecuta al arranque la sentencia SQL para eliminar la restricción {@code usuarios_rol_check} si existe.
     *
     * @param args argumentos de inicio de la aplicación
     */
    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check");
        } catch (Exception e) {
            log.warn("No se pudo eliminar la restricción usuarios_rol_check: {}", e.getMessage());
        }
    }
}
