package com.proyecto.utp.proyecto_dwi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.proyecto.utp.proyecto_dwi.domain.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
}