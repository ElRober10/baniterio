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
    void v8_crea_permiso_area_con_sus_columnas_y_la_unica() {
        java.util.List<String> columnas = jdbc.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_name = 'permiso_area'
            ORDER BY column_name
            """, String.class);
        assertThat(columnas)
            .containsExactlyInAnyOrder("id", "usuario_id", "area", "concedido_por", "created_at");

        Integer unica = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.table_constraints
            WHERE table_name = 'permiso_area'
              AND constraint_type = 'UNIQUE'
              AND constraint_name = 'uk_permiso_area'
            """, Integer.class);
        assertThat(unica).isEqualTo(1);
    }

    @Test
    void v8_anade_password_hash_a_solicitud_ingreso() {
        Integer columna = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'solicitud_ingreso' AND column_name = 'password_hash'
            """, Integer.class);
        assertThat(columna).isEqualTo(1);
    }

    @Test
    void v13_crea_evento_con_el_check_de_fechas() {
        Integer tabla = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables WHERE table_name = 'evento'
            """, Integer.class);
        assertThat(tabla).isEqualTo(1);

        Integer check = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.table_constraints
            WHERE table_name = 'evento' AND constraint_type = 'CHECK'
              AND constraint_name = 'ck_evento_fecha_fin'
            """, Integer.class);
        assertThat(check).isEqualTo(1);
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
