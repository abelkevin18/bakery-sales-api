package com.darkbit.bakerysales.faqs.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.darkbit.bakerysales.faqs.application.FaqExceptions.BadRequest;
import com.darkbit.bakerysales.faqs.application.FaqExceptions.InvalidInput;
import com.darkbit.bakerysales.faqs.application.FaqExceptions.NotFound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = FaqController.class)
public class FaqExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(FaqExceptionHandler.class);

	@ExceptionHandler(InvalidInput.class)
	public ResponseEntity<ErrorResponse> invalidInput(InvalidInput exception) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
			exception.getMessage(), exception.details());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException exception) {
		Map<String, List<String>> details = exception.getBindingResult().getFieldErrors().stream()
			.collect(Collectors.groupingBy(
				error -> error.getField(),
				Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())));
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
			"Revisa los campos marcados.", details);
	}

	@ExceptionHandler(NotFound.class)
	public ResponseEntity<ErrorResponse> notFound(NotFound exception) {
		return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), null);
	}

	@ExceptionHandler({ BadRequest.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
		String message = exception instanceof BadRequest
			? exception.getMessage()
			: "La solicitud contiene parámetros o JSON inválidos.";
		return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
		String requestId = requestId();
		LOGGER.error("Unexpected error processing FAQ request. requestId={}", requestId, exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
			new ErrorResponse(new ErrorDetail("INTERNAL_ERROR",
				"Ocurrió un error inesperado.", null, requestId)));
	}

	private ResponseEntity<ErrorResponse> response(
			HttpStatus status, String code, String message, Map<String, List<String>> details) {
		return ResponseEntity.status(status).body(new ErrorResponse(
			new ErrorDetail(code, message, details, requestId())));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID().toString().replace("-", "");
	}

	public record ErrorResponse(ErrorDetail error) {
	}

	public record ErrorDetail(
			String code,
			String message,
			Map<String, List<String>> details,
			String requestId) {
	}
}
