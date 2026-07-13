package com.schedulr.common.error;

import com.schedulr.common.api.ApiResponse;
import com.schedulr.common.error.exception.ConflictException;
import com.schedulr.common.error.exception.InvalidRequestException;
import com.schedulr.common.error.exception.NotAuthorizedException;
import com.schedulr.common.error.exception.NotFoundException;
import com.schedulr.common.error.exception.UnauthenticatedException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
    return respond(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidRequestException ex) {
    return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(NotAuthorizedException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotAuthorized(NotAuthorizedException ex) {
    return respond(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
    return respond(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
    return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(UnauthenticatedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(UnauthenticatedException ex) {
    return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return respond(HttpStatus.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return respond(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(ApiResponse.error(message));
  }
}
