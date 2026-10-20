package com.project.blackjack.global.config;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> invalid(RuntimeException ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
    }
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<?> conflict(Exception ex) { return ResponseEntity.status(409).body(Map.of("message", "동시에 처리된 요청이 있습니다. 다시 시도해 주세요.")); }
}
