package com.alerta.sistema_alerta_inundaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaAlertaInundacionesApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaAlertaInundacionesApplication.class, args);
	}

}
