package com.proyecto.utp.proyecto_dwi.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "Transferencia")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origen_id", nullable = false)
    private Sede origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destino_id", nullable = false)
    private Sede destino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creador_id", nullable = false)
    private Usuario creador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despachador_id")
    private Usuario despachador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id")
    private Usuario receptor;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "despachada_en")
    private LocalDateTime despachadaEn;

    @Column(name = "recibida_en")
    private LocalDateTime recibidaEn;

    @Column(length = 300)
    private String incidencia;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID idempotencia;
}
