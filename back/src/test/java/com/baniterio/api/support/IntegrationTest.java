package com.baniterio.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Clase base para los tests de integración (los que acaban en {@code *IT}).
 *
 * <p>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} levanta la aplicación
 * ENTERA en un puerto libre real, como en producción. {@code @Import(...)}
 * añade el Postgres de Testcontainers. Resultado: los tests hacen peticiones
 * HTTP de verdad contra la app de verdad, con Flyway aplicando el SQL real
 * sobre un Postgres real. Más lento que un test unitario, pero prueba que todas
 * las piezas encajan.
 *
 * <p>Convención Maven: los {@code *Test} los ejecuta el plugin Surefire en
 * {@code mvn test}; los {@code *IT} los ejecuta Failsafe en {@code mvn verify}.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "app.identidad.telefono-fundador=600000001")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {
}
