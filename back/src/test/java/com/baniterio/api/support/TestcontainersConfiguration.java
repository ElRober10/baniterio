package com.baniterio.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * NO añadir {@code .withReuse(true)}: varios ITs consumen estado de siembra/autorización
     * que no se limpia (p. ej. el teléfono del fundador `616985168` queda marcado como `usado`
     * tras el test de superadmin). Con el contenedor reutilizado, la segunda ejecución falla.
     * Cada `mvn verify` necesita una base de datos limpia.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
