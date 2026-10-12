package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Rol")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 30)
    private String nombre;

}
