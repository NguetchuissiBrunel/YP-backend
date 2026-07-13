package com.yowpainter.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.yowpainter.shared.kernel.KernelClientException;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Requete invalide";
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (message.contains("non trouve") || message.contains("introuvable")) {
            status = HttpStatus.NOT_FOUND;
        } else if (message.contains("non authentifie")) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (message.contains("Profil local introuvable") || message.contains("pas un profil")
                || message.contains("n'appartient pas")) {
            status = HttpStatus.FORBIDDEN;
        } else if (message.contains("deja inscrit")) {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(com.yowpainter.shared.kernel.KernelPermissionDeniedException.class)
    public ResponseEntity<Map<String, String>> handleKernelPermissionDenied(com.yowpainter.shared.kernel.KernelPermissionDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNoSuchElement(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Ressource introuvable"));
    }

    @ExceptionHandler({IllegalStateException.class, KernelClientException.class})
    public ResponseEntity<Map<String, String>> handleKernelConfiguration(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Configuration kernel invalide";
        if (ex instanceof IllegalStateException illegalStateException) {
            if (message.contains("places disponibles")
                    || message.contains("déjà été scanné")
                    || message.contains("annule")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
            }
            if (message.contains("Organisation artiste")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Acces refuse"));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        
        log.info("[RestExceptionHandler] handleMethodArgumentNotValid - Validation failed!");
        log.info("- JVM default timezone: {}", java.util.TimeZone.getDefault().getID());
        log.info("- ZoneId.systemDefault(): {}", java.time.ZoneId.systemDefault());
        log.info("- Instant.now(): {}", java.time.Instant.now());
        log.info("- LocalDateTime.now(): {}", java.time.LocalDateTime.now());
        log.info("- Clock.systemUTC().instant(): {}", java.time.Clock.systemUTC().instant());

        java.util.List<Map<String, Object>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> {
                    java.util.Map<String, Object> err = new java.util.HashMap<>();
                    err.put("field", error.getField());
                    err.put("message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Validation failed");
                    err.put("rejectedValue", error.getRejectedValue());

                    log.info("- Field in error: {}", error.getField());
                    log.info("- Validation message: {}", error.getDefaultMessage());
                    log.info("- Rejected value: {}", error.getRejectedValue());

                    if ("startDateTime".equals(error.getField()) || "endDateTime".equals(error.getField())) {
                        if (error.getRejectedValue() instanceof java.time.Instant rejectedInstant) {
                            java.time.Instant currentInstant = java.time.Instant.now();
                            long diffSeconds = java.time.Duration.between(currentInstant, rejectedInstant).getSeconds();
                            log.info("- Comparison details for {}: rejectedValue (valeur reçue)={}, currentInstant (valeur courante)={}, diffSeconds (différence en secondes)={}",
                                    error.getField(), rejectedInstant, currentInstant, diffSeconds);
                        } else if (error.getRejectedValue() != null) {
                            try {
                                java.time.Instant rejectedInstant = java.time.Instant.parse(error.getRejectedValue().toString());
                                java.time.Instant currentInstant = java.time.Instant.now();
                                long diffSeconds = java.time.Duration.between(currentInstant, rejectedInstant).getSeconds();
                                log.info("- Parsed comparison details for {}: rejectedValue (valeur reçue)={}, currentInstant (valeur courante)={}, diffSeconds (différence en secondes)={}",
                                        error.getField(), rejectedInstant, currentInstant, diffSeconds);
                            } catch (Exception parseEx) {
                                log.warn("- Failed to parse rejected value to Instant: {}", error.getRejectedValue());
                            }
                        }
                    }
                    return err;
                })
                .collect(java.util.stream.Collectors.toList());

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .collect(java.util.stream.Collectors.joining(". "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", message.isEmpty() ? "Validation failed" : message,
                        "errors", errors
                ));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException ex) {
        java.util.List<Map<String, Object>> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> {
                    java.util.Map<String, Object> err = new java.util.HashMap<>();
                    String path = violation.getPropertyPath().toString();
                    String fieldName = path.substring(path.lastIndexOf('.') + 1);
                    err.put("field", fieldName);
                    err.put("message", violation.getMessage());
                    err.put("rejectedValue", violation.getInvalidValue());
                    return err;
                })
                .collect(java.util.stream.Collectors.toList());

        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.joining(". "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", message.isEmpty() ? "Constraint violation" : message,
                        "errors", errors
                ));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        String simpleMessage = "Format JSON illisible ou invalide";
        String field = null;
        Object rejectedValue = null;

        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException) {
            com.fasterxml.jackson.databind.exc.InvalidFormatException ife = 
                    (com.fasterxml.jackson.databind.exc.InvalidFormatException) ex.getCause();
            if (ife.getPath() != null && !ife.getPath().isEmpty()) {
                field = ife.getPath().get(0).getFieldName();
            }
            rejectedValue = ife.getValue();
            simpleMessage = "Format invalide pour le champ '" + field + "'. Valeur rejetee: " + rejectedValue;
        }

        java.util.Map<String, Object> err = new java.util.HashMap<>();
        err.put("field", field);
        err.put("message", simpleMessage);
        err.put("rejectedValue", rejectedValue);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", simpleMessage,
                        "errors", java.util.List.of(err)
                ));
    }

    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> handleDateTimeParse(
            java.time.format.DateTimeParseException ex) {
        String simpleMessage = "Format de date invalide: " + ex.getParsedString();
        java.util.Map<String, Object> err = new java.util.HashMap<>();
        err.put("field", null);
        err.put("message", simpleMessage);
        err.put("rejectedValue", ex.getParsedString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", simpleMessage,
                        "errors", java.util.List.of(err)
                ));
    }
}
