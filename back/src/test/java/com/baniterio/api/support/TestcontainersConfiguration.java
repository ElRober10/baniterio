package com.baniterio.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Arranca un contenedor Docker de {@code postgres:17-alpine} solo para los tests.
 *
 * <p>Testcontainers lo levanta al empezar la suite y lo destruye al terminar.
 * {@code @ServiceConnection} le dice a Spring Boot "usa ESTE Postgres como base
 * de datos", sin tener que configurar la URL/usuario/contraseña a mano.
 *
 * <p>No usa el Postgres de {@code docker-compose.yml} (ese es para desarrollo):
 * cada ejecución de tests tiene su base de datos limpia y desechable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * NO añadir {@code .withReuse(true)}: varios ITs consumen estado de siembra/autorización
     * que no se limpia (p. ej. el teléfono del fundador `600000001` queda marcado como `usado`
     * tras el test de superadmin). Con el contenedor reutilizado, la segunda ejecución falla.
     * Cada `mvn verify` necesita una base de datos limpia.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
