package com.baniterio.api.identidad;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que las migraciones Flyway se aplican bien contra un Postgres real
 * y que la siembra (V6) deja la peña y el teléfono del fundador. Es la red de
 * seguridad del SQL: si alguien rompe una migración, este test falla en
 * {@code mvn verify}.
 */
class FlywayMigrationIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplica_todas_las_migraciones() {
        Integer aplicadas = jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(aplicadas).isGreaterThanOrEqualTo(6);
    }

    @Test
    void siembra_la_pena_baniterio_y_solo_el_telefono_del_fundador() {
        Integer penas = jdbc.queryForObject(
            "SELECT count(*) FROM pena WHERE slug = 'baniterio'", Integer.class);
        assertThat(penas).isEqualTo(1);

        // V6 siembra un único teléfono: el del fundador. Los demás se cargan a mano
        // desde back/scripts/seed-telefonos-baniterio.local.sql (fuera de git).
        // Otros ITs dan de alta teléfonos propios, así que filtramos por el número exacto.
        Integer fundador = jdbc.queryForObject("""
            SELECT count(*) FROM telefono_autorizado t
            JOIN pena p ON p.id = t.pena_id
            WHERE p.slug = 'baniterio' AND t.telefono = '616985168'
            """, Integer.class);
        assertThat(fundador).isEqualTo(1);
    }
}
