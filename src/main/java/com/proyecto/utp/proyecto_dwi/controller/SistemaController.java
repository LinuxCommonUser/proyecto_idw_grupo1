package com.proyecto.utp.proyecto_dwi.controller;

import java.time.LocalDate;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import com.proyecto.utp.proyecto_dwi.dto.SistemaRequest;
import com.proyecto.utp.proyecto_dwi.service.AccesoService;
import com.proyecto.utp.proyecto_dwi.service.InventarioService;

import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api")
public class SistemaController {
    private final AccesoService acceso;
    private final InventarioService inventario;

    public SistemaController(AccesoService acceso, InventarioService inventario) {
        this.acceso = acceso;
        this.inventario = inventario;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/setup/status")
    public Map<String, Boolean> setupStatus() {
        return Map.of("necesario", acceso.necesitaSetup());
    }

    @PostMapping("/setup")
    public void setup(@Valid @RequestBody SistemaRequest.Setup r) {
        acceso.setup(r);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return acceso.perfil();
    }

    @GetMapping("/sedes")
    public List<Map<String, Object>> sedes() {
        return acceso.sedes();
    }

    @GetMapping("/destinos")
    public List<Map<String, Object>> destinos() {
        return inventario.destinos();
    }

    @PostMapping("/sedes")
    public Map<String, Long> nuevaSede(@Valid @RequestBody SistemaRequest.Sede r) {
        return Map.of("id", acceso.guardarSede(null, r));
    }

    @PutMapping("/sedes/{id}")
    public Map<String, Long> editarSede(@PathVariable Integer id, @Valid @RequestBody SistemaRequest.Sede r) {
        return Map.of("id", acceso.guardarSede(id, r));
    }

    @PatchMapping("/sedes/{id}/estado")
    public void estadoSede(@PathVariable int id, @RequestParam boolean activo) {
        acceso.estadoSede(id, activo);
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios() {
        return acceso.usuarios();
    }

    @PostMapping("/usuarios")
    public Map<String, Long> nuevoUsuario(@Valid @RequestBody SistemaRequest.Usuario r) {
        return Map.of("id", acceso.guardarUsuario(null, r));
    }

    @PutMapping("/usuarios/{id}")
    public Map<String, Long> editarUsuario(@PathVariable Long id, @Valid @RequestBody SistemaRequest.Usuario r) {
        return Map.of("id", acceso.guardarUsuario(id, r));
    }

    @GetMapping("/existencias")
    public List<Map<String, Object>> stock(@RequestParam(required = false) Integer sedeId) {
        return inventario.stock(sedeId);
    }

    @PostMapping("/existencias/minimo")
    public void minimo(@Valid @RequestBody SistemaRequest.Minimo r) {
        inventario.minimo(r);
    }

    @GetMapping("/kardex")
    public List<Map<String, Object>> kardex(@RequestParam int sedeId, @RequestParam long productoId) {
        return inventario.kardex(sedeId, productoId);
    }

    @GetMapping("/movimientos")
    public List<Map<String, Object>> movimientos(@RequestParam(required = false) Integer sedeId,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return inventario.movimientos(sedeId, desde, hasta);
    }

    @GetMapping("/movimientos/{id}")
    public Map<String, Object> movimiento(@PathVariable long id) {
        return inventario.detalleMovimiento(id);
    }

    @PostMapping("/movimientos")
    public Map<String, Object> movimiento(@Valid @RequestBody SistemaRequest.Movimiento r) {
        return inventario.movimiento(r);
    }

    @PostMapping("/ajustes")
    public Map<String, Object> ajuste(@Valid @RequestBody SistemaRequest.Ajuste r) {
        return inventario.ajuste(r);
    }

    @PostMapping("/movimientos/{id}/reverso")
    public Map<String, Object> reverso(@PathVariable long id, @Valid @RequestBody SistemaRequest.Reverso r) {
        return inventario.reverso(id, r);
    }

    @GetMapping("/transferencias")
    public List<Map<String, Object>> transferencias() {
        return inventario.transferencias();
    }

    @GetMapping("/transferencias/{id}")
    public Map<String, Object> transferencia(@PathVariable long id) {
        return inventario.detalleTransferencia(id);
    }

    @PostMapping("/transferencias")
    public Map<String, Object> transferencia(@Valid @RequestBody SistemaRequest.Transferencia r) {
        return inventario.crearTransferencia(r);
    }

    @PostMapping("/transferencias/{id}/{accion}")
    public Map<String, Object> accion(@PathVariable long id, @PathVariable String accion,
            @Valid @RequestBody SistemaRequest.Motivo r) {
        return inventario.accionTransferencia(id, accion, r.motivo());
    }

    @GetMapping("/auditoria")
    public List<Map<String, Object>> auditoria() {
        return inventario.auditoria();
    }
}