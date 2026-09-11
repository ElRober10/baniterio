package com.baniterio.api.admin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.PenaPilotoService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test unitario de {@link AdminService#pendientesPorArea}: agrega los
 * {@link ContadorPendientes}, filtra a las áreas del usuario y omite las que
 * están a cero. El resto de {@link AdminService} se prueba en sus *IT.
 */
class AdminServiceTest {

    private final ServicioPermisos servicioPermisos = mock(ServicioPermisos.class);
    private final PenaPilotoService pena = mock(PenaPilotoService.class);

    {
        when(pena.id()).thenReturn(1L);
    }

    private ContadorPendientes contador(AreaProtegida area, long n) {
        ContadorPendientes c = mock(ContadorPendientes.class);
        when(c.area()).thenReturn(area);
        when(c.contar(anyLong())).thenReturn(n);
        return c;
    }

    private AdminService servicioCon(List<ContadorPendientes> contadores) {
        return new AdminService(null, null, null, null, pena, null, servicioPermisos, null, contadores);
    }

    @Test
    void incluye_solo_las_areas_del_usuario_con_pendientes() {
        when(servicioPermisos.areasDe(7L)).thenReturn(Set.of(AreaProtegida.ADMIN_SOLICITUDES));
        AdminService servicio = servicioCon(List.of(
                contador(AreaProtegida.ADMIN_SOLICITUDES, 3),
                contador(AreaProtegida.ADMIN_PERMISOS, 9)));

        assertThat(servicio.pendientesPorArea(7L))
                .isEqualTo(Map.of(AreaProtegida.ADMIN_SOLICITUDES, 3L));
    }

    @Test
    void omite_las_areas_a_cero() {
        when(servicioPermisos.areasDe(7L)).thenReturn(Set.of(AreaProtegida.ADMIN_SOLICITUDES));
        AdminService servicio = servicioCon(List.of(contador(AreaProtegida.ADMIN_SOLICITUDES, 0)));

        assertThat(servicio.pendientesPorArea(7L)).isEmpty();
    }

    @Test
    void usuario_sin_areas_recibe_mapa_vacio() {
        when(servicioPermisos.areasDe(7L)).thenReturn(Set.of());
        AdminService servicio = servicioCon(List.of(contador(AreaProtegida.ADMIN_SOLICITUDES, 5)));

        assertThat(servicio.pendientesPorArea(7L)).isEmpty();
    }
}
