package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unidad", schema = "dbo")
public class Unidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 10, columnDefinition = "nvarchar(10)")
    private String codigo;

    @Column(nullable = false, length = 40, columnDefinition = "nvarchar(40)")
    private String nombre;

    protected Unidad() {
    }

    public Integer getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }
}
