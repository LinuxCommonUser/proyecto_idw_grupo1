package com.proyecto.utp.proyecto_dwi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.utp.proyecto_dwi.domain.Unidad;


public interface UnidadRepository
        extends JpaRepository<Unidad, Integer> {

    List<Unidad> findAllByOrderByNombreAsc();
}