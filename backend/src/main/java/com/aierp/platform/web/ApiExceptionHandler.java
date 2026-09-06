package com.aierp.platform.web;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class) ResponseEntity<ApiProblem> notFound(NoSuchElementException ex) { return problem(HttpStatus.NOT_FOUND, "NOT_FOUND"); }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class}) ResponseEntity<ApiProblem> badRequest(Exception ex) { return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED"); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ApiProblem> forbidden(AccessDeniedException ex) { return problem(HttpStatus.FORBIDDEN, "FORBIDDEN"); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class) ResponseEntity<ApiProblem> stale(ObjectOptimisticLockingFailureException ex) { return problem(HttpStatus.CONFLICT, "STALE_ROW_VERSION"); }
    private ResponseEntity<ApiProblem> problem(HttpStatus status, String code) { return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(new ApiProblem(code, UUID.randomUUID().toString(), List.of())); }
}
