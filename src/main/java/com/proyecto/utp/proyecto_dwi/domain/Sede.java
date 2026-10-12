package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Sede")
public class Sede {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 250)
    private String direccion;

    @Column(nullable = false)
    private Boolean activo = true;
}