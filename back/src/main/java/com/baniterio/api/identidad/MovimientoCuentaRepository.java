package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoCuentaRepository extends JpaRepository<MovimientoCuenta, Long> {

    List<MovimientoCuenta> findByCuentaIdOrderByFechaAscIdAsc(Long cuentaId);

    Optional<MovimientoCuenta> findByFichaAsistenciaId(Long fichaAsistenciaId);

    boolean existsByFichaAsistenciaId(Long fichaAsistenciaId);

    @Query("select coalesce(sum(m.importe), 0) from MovimientoCuenta m where m.cuenta.id = :cuentaId")
    BigDecimal sumImporte(@Param("cuentaId") Long cuentaId);
}
