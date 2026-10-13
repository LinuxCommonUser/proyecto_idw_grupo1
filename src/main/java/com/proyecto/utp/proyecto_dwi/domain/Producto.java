package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "producto", schema = "dbo")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, columnDefinition = "nvarchar(30)")
    private String codigo;

    @Column(nullable = false, length = 120, columnDefinition = "nvarchar(120)")
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_id", nullable = false)
    private Unidad unidad;

    @Column(nullable = false)
    private boolean activo;

    protected Producto() {
    }

    public Producto(
            String codigo,
            String nombre,
            Categoria categoria,
            Unidad unidad) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
        this.activo = true;
    }

    public void actualizar(
            String codigo,
            String nombre,
            Categoria categoria) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Unidad getUnidad() {
        return unidad;
    }

    public boolean isActivo() {
        return activo;
    }
}