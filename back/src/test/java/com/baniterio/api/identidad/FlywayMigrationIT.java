package com.baniterio.api.identidad;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("habilitar en Task 2 al crear V6")
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
    void siembra_la_pena_baniterio_con_sus_telefonos() {
        Integer penas = jdbc.queryForObject(
            "SELECT count(*) FROM pena WHERE slug = 'baniterio'", Integer.class);
        Integer telefonos = jdbc.queryForObject("""
            SELECT count(*) FROM telefono_autorizado t
            JOIN pena p ON p.id = t.pena_id
            WHERE p.slug = 'baniterio'
            """, Integer.class);
        assertThat(penas).isEqualTo(1);
        assertThat(telefonos).isEqualTo(56);
    }
}
