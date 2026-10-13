package com.proyecto.utp.proyecto_dwi.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

        @ExceptionHandler(ResponseStatusException.class)
        public ProblemDetail manejarErrorDeNegocio(
                        ResponseStatusException exception) {
                return ProblemDetail.forStatusAndDetail(
                                exception.getStatusCode(),
                                exception.getReason() == null
                                                ? "No se pudo completar la operación"
                                                : exception.getReason());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ProblemDetail manejarValidacion(
                        MethodArgumentNotValidException exception) {
                Map<String, String> errores = new LinkedHashMap<>();

                exception.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> errores.put(
                                                error.getField(),
                                                error.getDefaultMessage()));

                ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                "Revisa los campos enviados");

                problema.setProperty("errores", errores);

                return problema;
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ProblemDetail manejarIntegridad(
                        DataIntegrityViolationException exception) {
                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.CONFLICT,
                                "La operación contiene un código duplicado "
                                                + "o una referencia de datos no válida");
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ProblemDetail manejarJsonInvalido(
                        HttpMessageNotReadableException exception) {
                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                "El contenido enviado no tiene un formato JSON válido");
        }
}
