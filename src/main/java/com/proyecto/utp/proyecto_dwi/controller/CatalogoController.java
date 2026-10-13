package com.proyecto.utp.proyecto_dwi.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.utp.proyecto_dwi.domain.Categoria;
import com.proyecto.utp.proyecto_dwi.domain.Unidad;
import com.proyecto.utp.proyecto_dwi.repository.CategoriaRepository;
import com.proyecto.utp.proyecto_dwi.repository.UnidadRepository;

@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CategoriaRepository categoriaRepository;
    private final UnidadRepository unidadRepository;

    public CatalogoController(
            CategoriaRepository categoriaRepository,
            UnidadRepository unidadRepository
    ) {
        this.categoriaRepository = categoriaRepository;
        this.unidadRepository = unidadRepository;
    }

    @GetMapping("/categorias")
    public List<Categoria> listarCategorias() {
        return categoriaRepository
                .findByActivoTrueOrderByNombreAsc();
    }

    @GetMapping("/unidades")
    public List<Unidad> listarUnidades() {
        return unidadRepository
                .findAllByOrderByNombreAsc();
    }
}
