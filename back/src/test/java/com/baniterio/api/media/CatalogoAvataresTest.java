package com.baniterio.api.media;

import com.baniterio.api.perfil.dto.AvatarResumen;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoAvataresTest {

    private final CatalogoAvatares catalogo = new CatalogoAvatares(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void carga_el_manifiesto_del_classpath() {
        assertThat(catalogo.listar())
                .isNotEmpty()
                .anySatisfy(a -> assertThat(a.id()).isEqualTo("01_chica"))
                .allSatisfy(a -> assertThat(a.genero()).isIn("CHICO", "CHICA"));
    }

    @Test
    void existe_solo_para_ids_del_catalogo() {
        String primero = catalogo.listar().get(0).id();
        assertThat(catalogo.existe(primero)).isTrue();
        assertThat(catalogo.existe("99_marciano")).isFalse();
    }

    @Test
    void lee_el_png_de_un_avatar_del_catalogo() {
        String primero = catalogo.listar().get(0).id();
        assertThat(catalogo.leerPng(primero)).get()
                .satisfies(bytes -> assertThat(bytes.length).isGreaterThan(100));
        assertThat(catalogo.leerPng("99_marciano")).isEmpty();
    }
}
