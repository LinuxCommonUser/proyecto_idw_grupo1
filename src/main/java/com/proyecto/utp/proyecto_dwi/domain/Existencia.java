package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "Existencia")
public class Existencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal cantidad;

    @Column(name = "stock_minimo", precision = 18, scale = 3, nullable = false)
    private BigDecimal stockMinimo;

    @Version
    private Integer version; // RNF003: Control de concurrencia
}