package com.proyecto.utp.proyecto_dwi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductoRequest(

        @NotBlank(message = "El código es obligatorio") @Size(max = 30, message = "El código admite hasta 30 caracteres") String codigo,

        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120, message = "El nombre admite hasta 120 caracteres") String nombre,

        @NotNull(message = "Selecciona una categoría") @Positive(message = "La categoría no es válida") Integer categoriaId,

        @NotNull(message = "Selecciona una unidad") @Positive(message = "La unidad no es válida") Integer unidadId

) {
}