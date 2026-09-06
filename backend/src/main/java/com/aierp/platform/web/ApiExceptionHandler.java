package com.aierp.platform.web;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({NoSuchElementException.class,org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<ApiProblem> missing(Exception e,HttpServletRequest r) { return problem(404,"NOT_FOUND",r,List.of()); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiProblem> denied(Exception e,HttpServletRequest r) { return problem(403,"FORBIDDEN",r,List.of()); }
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,jakarta.persistence.OptimisticLockException.class})
    ResponseEntity<ApiProblem> stale(Exception e,HttpServletRequest r) { return problem(409,"STALE_ROW_VERSION",r,List.of()); }
    @ExceptionHandler({IllegalStateException.class,org.springframework.dao.DataIntegrityViolationException.class,org.springframework.dao.PessimisticLockingFailureException.class})
    ResponseEntity<ApiProblem> conflict(Exception e,HttpServletRequest r) { return problem(409,"CONFLICT",r,List.of()); }
    @ExceptionHandler(ValidationFailure.class)
    ResponseEntity<ApiProblem> validation(ValidationFailure e,HttpServletRequest r) { return problem(400,"VALIDATION_FAILED",r,List.of(new ApiProblem.FieldError(e.field,e.getMessage()))); }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> beanValidation(org.springframework.web.bind.MethodArgumentNotValidException e,HttpServletRequest r) {
        return problem(400,"VALIDATION_FAILED",r,e.getBindingResult().getFieldErrors().stream().map(f -> new ApiProblem.FieldError(f.getField(),f.getDefaultMessage())).toList());
    }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiProblem> malformed(Exception e,HttpServletRequest r) { return problem(400,"VALIDATION_FAILED",r,List.of(new ApiProblem.FieldError("request","Malformed or invalid request"))); }
    private ResponseEntity<ApiProblem> problem(int status,String code,HttpServletRequest r,List<ApiProblem.FieldError> fields) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(new ApiProblem(code,TraceFilter.id(r),fields));
    }
}
