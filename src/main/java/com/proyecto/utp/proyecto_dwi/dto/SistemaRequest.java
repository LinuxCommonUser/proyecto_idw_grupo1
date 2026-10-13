package com.proyecto.utp.proyecto_dwi.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class SistemaRequest {
    private SistemaRequest() {
    }

    public record Sede(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 100) String nombre,
            @Size(max = 250) String direccion) {
    }

    public record Usuario(@NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}") String usuario,
            @NotBlank @Size(max = 150) String nombre, @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = "ADMIN|ENCARGADO|CONSULTOR") String rol,
            @NotNull List<@Positive Integer> sedes, boolean activo) {
    }

    public record Setup(@NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}") String usuario,
            @NotBlank @Size(max = 150) String nombre, @NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record Linea(@NotNull @Positive Long productoId,
            @NotNull @Positive @Digits(integer = 15, fraction = 3) BigDecimal cantidad,
            @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal costoUnitario) {
    }

    public record Movimiento(@NotNull @Positive Integer sedeId, @NotBlank String tipo,
            @NotBlank @Size(max = 300) String motivo, @Size(max = 100) String referencia,
            @Size(max = 150) String proveedor, @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencia,
            @NotEmpty @Size(max = 100) List<@Valid Linea> lineas) {
    }

    public record Transferencia(@NotNull @Positive Integer origenId, @NotNull @Positive Integer destinoId,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencia,
            @NotEmpty @Size(max = 100) List<@Valid Linea> lineas) {
    }

    public record Motivo(@NotBlank @Size(max = 300) String motivo) {
    }

    public record Reverso(@NotBlank @Size(max = 300) String motivo,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencia) {
    }

    public record Ajuste(@NotNull @Positive Integer sedeId, @NotNull @Positive Long productoId,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 3) BigDecimal conteo,
            @NotBlank @Size(max = 300) String motivo,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencia) {
    }

    public record Minimo(@NotNull @Positive Integer sedeId, @NotNull @Positive Long productoId,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 3) BigDecimal minimo) {
    }
}
