package com.proyecto.utp.proyecto_dwi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.proyecto.utp.proyecto_dwi.repository.ProductoRepository;

@Controller
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    public ProductoController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    // Retorna la vista MOCKUP003 Productos
    @GetMapping
    public String listarProductos(Model model) {
        // Envía los productos de SQL Server a la vista HTML
        model.addAttribute("productos", productoRepository.findAll());
        
        // El String retornado corresponde al nombre del archivo HTML (productos.html)
        return "productos"; 
    }
}
