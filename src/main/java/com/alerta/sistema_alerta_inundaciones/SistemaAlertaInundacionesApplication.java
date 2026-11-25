package com.alerta.sistema_alerta_inundaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clase principal del sistema de alerta temprana de inundaciones. Arranca la
 * aplicación Spring Boot, activa el escaneo de propiedades de configuración y
 * habilita las tareas programadas (monitoreo periódico de los ríos).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SistemaAlertaInundacionesApplication {

	/**
	 * Punto de entrada de la aplicación.
	 *
	 * @param args argumentos de línea de comandos pasados a Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(SistemaAlertaInundacionesApplication.class, args);
	}

}
