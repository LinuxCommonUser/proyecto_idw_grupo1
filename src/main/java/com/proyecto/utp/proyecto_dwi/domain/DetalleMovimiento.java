package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "DetalleMovimiento")
public class DetalleMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_id", nullable = false)
    private Movimiento movimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal cantidad;

    @Column(name = "costo_unitario", precision = 18, scale = 4)
    private BigDecimal costoUnitario;
}
