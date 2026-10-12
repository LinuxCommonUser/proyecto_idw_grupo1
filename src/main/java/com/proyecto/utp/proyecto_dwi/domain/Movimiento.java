package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Data
@Entity
@Table(name = "Movimiento")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario responsable;

    @Column(nullable = false, length = 25)
    private String tipo;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(nullable = false, length = 300)
    private String motivo;

    @Column(length = 100)
    private String referencia;

    @Column(length = 150)
    private String proveedor;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverso_de_id")
    private Movimiento reversoDe; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_id")
    private Transferencia transferencia;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID idempotencia; 

    @OneToMany(mappedBy = "movimiento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleMovimiento> detalles;
}