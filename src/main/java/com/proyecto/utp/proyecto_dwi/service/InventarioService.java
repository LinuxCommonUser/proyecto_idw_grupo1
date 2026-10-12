package com.proyecto.utp.proyecto_dwi.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.utp.proyecto_dwi.domain.DetalleMovimiento;
import com.proyecto.utp.proyecto_dwi.domain.Existencia;
import com.proyecto.utp.proyecto_dwi.domain.Movimiento;
import com.proyecto.utp.proyecto_dwi.domain.Producto;
import com.proyecto.utp.proyecto_dwi.domain.Sede;
import com.proyecto.utp.proyecto_dwi.domain.Usuario;
import com.proyecto.utp.proyecto_dwi.repository.ExistenciaRepository;
import com.proyecto.utp.proyecto_dwi.repository.MovimientoRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional
public class InventarioService {
    
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;

    public InventarioService(ExistenciaRepository existenciaRepo, MovimientoRepository movimientoRepo) {
        this.existenciaRepository = existenciaRepo;
        this.movimientoRepository = movimientoRepo;
    }

    public void registrarEntrada(Sede sede, Producto producto, BigDecimal cantidadEntrante, Usuario responsable, String motivo, String referencia, UUID idempotencia) {
        if (cantidadEntrante.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva"); // RB03[cite: 1]
        }

        Existencia existencia = existenciaRepository.findBySedeAndProducto(sede, producto)
            .orElseThrow(() -> new RuntimeException("Existencia no encontrada"));
            
        existencia.setCantidad(existencia.getCantidad().add(cantidadEntrante));
        existenciaRepository.save(existencia);
        
        Movimiento movimiento = new Movimiento();
        movimiento.setSede(sede);
        movimiento.setResponsable(responsable);
        movimiento.setTipo("ENTRADA"); 
        movimiento.setFechaRegistro(LocalDateTime.now()); // Fecha de registro procede del servidor[cite: 1]
        movimiento.setMotivo(motivo);
        movimiento.setReferencia(referencia);
        movimiento.setIdempotencia(idempotencia);
        
        DetalleMovimiento detalle = new DetalleMovimiento();
        detalle.setMovimiento(movimiento);
        detalle.setProducto(producto);
        detalle.setCantidad(cantidadEntrante);
        
        if (movimiento.getDetalles() == null) {
            movimiento.setDetalles(new ArrayList<>());
        }
        movimiento.getDetalles().add(detalle);
        
        movimientoRepository.save(movimiento);
    }
}