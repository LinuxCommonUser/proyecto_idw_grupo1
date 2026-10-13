package com.proyecto.utp.proyecto_dwi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.utp.proyecto_dwi.domain.Categoria;


public interface CategoriaRepository
        extends JpaRepository<Categoria, Integer> {

    List<Categoria> findByActivoTrueOrderByNombreAsc();
}
