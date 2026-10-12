package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Producto")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Boolean activo = true;

}