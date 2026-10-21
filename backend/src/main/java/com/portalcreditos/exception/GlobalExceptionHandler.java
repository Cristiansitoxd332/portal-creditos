package com.portalcreditos.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo global de errores: convierte cualquier excepcion en una respuesta JSON clara
 * y nunca expone detalles internos (trazas, SQL) al usuario.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorResponse> negocio(NegocioException ex, HttpServletRequest req) {
        HttpStatus estado = ex.getEstado();
        return ResponseEntity.status(estado)
                .body(ErrorResponse.de(estado.value(), estado.getReasonPhrase(), ex.getMessage(), req.getRequestURI()));
    }

    /** Errores de validacion de los datos de entrada (@Valid): devuelve el mensaje de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> detalles = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            detalles.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse cuerpo = new ErrorResponse(LocalDateTime.now(), 400, "Bad Request",
                "Hay datos invalidos en la solicitud", req.getRequestURI(), detalles);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "Bad Request", "El cuerpo de la solicitud no tiene un formato JSON valido", req.getRequestURI()));
    }

    /**
     * Cualquier otro error. Los errores propios de Spring MVC (ruta inexistente 404,
     * metodo no permitido 405, parametro invalido 400...) conservan su codigo HTTP;
     * los inesperados se registran en el log y se responde un mensaje generico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex, HttpServletRequest req) {
        if (ex instanceof org.springframework.web.ErrorResponse errorSpring) {
            int codigo = errorSpring.getStatusCode().value();
            HttpStatus estado = HttpStatus.resolve(codigo);
            String nombre = estado != null ? estado.getReasonPhrase() : "Error";
            String detalle = errorSpring.getBody().getDetail();
            return ResponseEntity.status(codigo)
                    .body(ErrorResponse.de(codigo, nombre, detalle != null ? detalle : nombre, req.getRequestURI()));
        }
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.de(500, "Internal Server Error",
                        "Ocurrio un error inesperado. Intente de nuevo mas tarde.", req.getRequestURI()));
    }
}
