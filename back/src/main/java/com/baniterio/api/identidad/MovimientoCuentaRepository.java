package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoCuentaRepository extends JpaRepository<MovimientoCuenta, Long> {

    List<MovimientoCuenta> findByCuentaIdOrderByFechaAscIdAsc(Long cuentaId);

    List<MovimientoCuenta> findByCuentaIdAndAnioOrderByFechaAscIdAsc(Long cuentaId, int anio);

    Optional<MovimientoCuenta> findByFichaAsistenciaIdAndOrigen(Long fichaAsistenciaId, OrigenMovimiento origen);

    boolean existsByFichaAsistenciaIdAndOrigen(Long fichaAsistenciaId, OrigenMovimiento origen);

    @Query("select coalesce(sum(m.importe), 0) from MovimientoCuenta m where m.cuenta.id = :cuentaId")
    BigDecimal sumImporte(@Param("cuentaId") Long cuentaId);

    @Query("select coalesce(sum(m.importe), 0) from MovimientoCuenta m "
            + "where m.cuenta.id = :cuentaId and m.anio = :anio")
    BigDecimal sumImporteAnio(@Param("cuentaId") Long cuentaId, @Param("anio") int anio);

    @Query("select distinct m.anio from MovimientoCuenta m "
            + "where m.cuenta.id = :cuentaId order by m.anio desc")
    List<Integer> aniosConMovimientos(@Param("cuentaId") Long cuentaId);
}
