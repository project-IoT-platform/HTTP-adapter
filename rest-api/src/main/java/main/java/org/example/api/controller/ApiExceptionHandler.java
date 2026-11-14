package main.java.org.example.api.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> invalid(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "invalid_parameter", "invalid request parameter");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badType(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "invalid_parameter",
                "parameter '" + e.getName() + "' has an invalid format");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missing(MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "invalid_parameter",
                "parameter '" + e.getParameterName() + "' is required");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> illegal(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "invalid_parameter", e.getMessage());
    }

    private static ResponseEntity<ApiError> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ApiError(error, message));
    }
}
