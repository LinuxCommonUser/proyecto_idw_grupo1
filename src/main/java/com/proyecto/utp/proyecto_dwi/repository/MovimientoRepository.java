package com.proyecto.utp.proyecto_dwi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.proyecto.utp.proyecto_dwi.domain.Movimiento;
import com.proyecto.utp.proyecto_dwi.domain.Sede;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    // Método derivado para consultar la secuencia de movimientos (kardex) filtrando por sede, ordenado por fecha
    List<Movimiento> findBySedeOrderByFechaRegistroDesc(Sede sede);
}