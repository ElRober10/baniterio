package com.baniterio.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de arranque de la aplicación (el {@code main}).
 *
 * <p>{@code @SpringBootApplication} arranca el conjunto: escanea este paquete y
 * los de debajo buscando componentes ({@code @Service}, {@code @RestController},
 * {@code @Configuration}, ...), y autoconfigura Tomcat, JPA, Flyway, Security...
 * a partir de las dependencias del pom y de {@code application.yml}.
 *
 * <p>{@code @ConfigurationPropertiesScan} registra los records
 * {@code @ConfigurationProperties} (aquí, {@link com.baniterio.api.config.AppProperties}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BaniterioApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaniterioApiApplication.class, args);
	}

}
