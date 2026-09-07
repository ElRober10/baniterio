package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a BBDD para {@link FichaBebida}. */
public interface FichaBebidaRepository extends JpaRepository<FichaBebida, Long> {

    Optional<FichaBebida> findByAsistenciaId(Long asistenciaId);

    @Query("select f from FichaBebida f where f.asistencia.evento.id = :eventoId")
    List<FichaBebida> findByEventoId(@Param("eventoId") Long eventoId);

    @Query("""
        select f from FichaBebida f
        where f.estadoPago = :estado
          and f.asistencia.evento.cuenta.id = :cuentaId
        """)
    List<FichaBebida> findByEstadoYCuenta(@Param("estado") EstadoPagoCuota estado,
                                          @Param("cuentaId") Long cuentaId);

    @Query("""
        select coalesce(sum(f.cuota), 0) from FichaBebida f
        where f.asistencia.evento.cuenta.id = :cuentaId
          and f.cuota is not null
          and f.asistencia.estado in (com.baniterio.api.identidad.EstadoAsistencia.APUNTADO,
                                      com.baniterio.api.identidad.EstadoAsistencia.EN_DUDA)
          and f.estadoPago in (com.baniterio.api.identidad.EstadoPagoCuota.PENDIENTE_PAGO,
                               com.baniterio.api.identidad.EstadoPagoCuota.DECLARADO)
        """)
    BigDecimal sumaCuotasPorEntrar(@Param("cuentaId") Long cuentaId);

    @Query("""
        select f from FichaBebida f
        where f.asistencia.evento.cuenta.id = :cuentaId
          and f.cuota is not null
          and f.asistencia.estado in (com.baniterio.api.identidad.EstadoAsistencia.APUNTADO,
                                      com.baniterio.api.identidad.EstadoAsistencia.EN_DUDA)
        """)
    List<FichaBebida> findConCuotaDeCuenta(@Param("cuentaId") Long cuentaId);

    @Query("""
        select coalesce(sum(f.cuota), 0) from FichaBebida f
        where f.estadoPago = com.baniterio.api.identidad.EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO
          and f.asistencia.evento.cuenta.id = :cuentaId
        """)
    BigDecimal sumaPorIngresar(@Param("cuentaId") Long cuentaId);
}
