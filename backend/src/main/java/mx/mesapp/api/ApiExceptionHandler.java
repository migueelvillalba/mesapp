package mx.mesapp.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiProblem.class) ResponseEntity<?> problem(ApiProblem error) { return ResponseEntity.status(error.status).body(Map.of("timestamp", Instant.now(), "status", error.status.value(), "error", error.getMessage())); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(DataIntegrityViolationException error) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("timestamp", Instant.now(), "status", 409, "error", "La operación entra en conflicto con datos existentes o un pedido activo")); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException error) {
        var fields = error.getBindingResult().getFieldErrors().stream().collect(java.util.stream.Collectors.toMap(FieldError::getField, e -> e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage(), (a,b) -> a));
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "error", "Hay campos inválidos", "fields", fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> unreadable(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "error", "El cuerpo JSON no es válido o no usa UTF-8"));
    }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception error) { log.error("Error interno procesando una solicitud MesApp ({})",error.getClass().getSimpleName(),error);return ResponseEntity.status(500).body(Map.of("timestamp", Instant.now(), "status", 500, "error", "Ocurrió un error interno")); }
    public static class ApiProblem extends RuntimeException {
        final HttpStatus status;
        public ApiProblem(HttpStatus status, String message) { super(message); this.status = status; }
        public static ApiProblem bad(String message) { return new ApiProblem(HttpStatus.BAD_REQUEST, message); }
        public static ApiProblem unauthorized(String message) { return new ApiProblem(HttpStatus.UNAUTHORIZED, message); }
        public static ApiProblem forbidden(String message) { return new ApiProblem(HttpStatus.FORBIDDEN, message); }
        public static ApiProblem missing(String message) { return new ApiProblem(HttpStatus.NOT_FOUND, message); }
        public static ApiProblem conflict(String message) { return new ApiProblem(HttpStatus.CONFLICT, message); }
    }
}
