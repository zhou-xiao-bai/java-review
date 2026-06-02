package com.javareview.common;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<ErrorResponse> handleConflict(IllegalStateException exception) {
		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of("conflict", exception.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
		return ResponseEntity
				.badRequest()
				.body(ErrorResponse.of("bad_request", exception.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("not_found", exception.getMessage()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	ResponseEntity<ErrorResponse> handleBadCredentials() {
		return ResponseEntity
				.status(HttpStatus.UNAUTHORIZED)
				.body(ErrorResponse.of("bad_credentials", "Invalid username/email or password."));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		List<String> details = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList();

		return ResponseEntity
				.badRequest()
				.body(new ErrorResponse("validation_error", "Request validation failed.", details, Instant.now()));
	}

	public record ErrorResponse(String code, String message, List<String> details, Instant timestamp) {

		static ErrorResponse of(String code, String message) {
			return new ErrorResponse(code, message, List.of(), Instant.now());
		}
	}
}
