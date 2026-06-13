package tn.cityvoice.evenementservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Événement introuvable ────────────────────────────────
    @ExceptionHandler(EvenementNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            EvenementNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), 404));
    }

    // ─── Événement complet ────────────────────────────────────
    @ExceptionHandler(EvenementCompletException.class)
    public ResponseEntity<Map<String, Object>> handleComplet(
            EvenementCompletException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(ex.getMessage(), 409));
    }

    // ─── Erreurs de validation (@Valid) ───────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> erreurs = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            erreurs.put(field, message);
        });

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 400);
        body.put("erreurs", erreurs);
        return ResponseEntity.badRequest().body(body);
    }

    // ─── Erreur générique ─────────────────────────────────────
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(
            RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getMessage(), 400));
    }

    // ─── Helper ───────────────────────────────────────────────
    private Map<String, Object> buildError(String message, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("erreur", message);
        return body;
    }
}