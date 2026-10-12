package com.proyecto.utp.proyecto_dwi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.utp.proyecto_dwi.domain.Existencia;
import com.proyecto.utp.proyecto_dwi.domain.Producto;
import com.proyecto.utp.proyecto_dwi.domain.Sede;

import java.util.Optional;

public interface ExistenciaRepository extends JpaRepository<Existencia, Long> {
    // Consulta para obtener el saldo de un producto en una sede específica
    Optional<Existencia> findBySedeAndProducto(Sede sede, Producto producto);
}
