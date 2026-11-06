package com.darkbit.bakerysales.storage.api;

import com.darkbit.bakerysales.storage.application.StorageExceptions.Conflict;
import com.darkbit.bakerysales.storage.application.StorageExceptions.InvalidInput;
import com.darkbit.bakerysales.storage.application.StorageExceptions.NotFound;
import com.darkbit.bakerysales.storage.application.StorageExceptions.PayloadTooLarge;
import com.darkbit.bakerysales.storage.application.StorageExceptions.StorageUnavailable;
import com.darkbit.bakerysales.storage.application.StorageExceptions.UnsupportedMediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = UploadController.class)
public class UploadExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(UploadExceptionHandler.class);

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

	@ExceptionHandler(PayloadTooLarge.class)
	public ResponseEntity<ErrorResponse> payloadTooLarge(PayloadTooLarge exception) {
		return response(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
			exception.getMessage(), null);
	}

	@ExceptionHandler(UnsupportedMediaType.class)
	public ResponseEntity<ErrorResponse> unsupportedMediaType(UnsupportedMediaType exception) {
		return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
			exception.getMessage(), null);
	}

	@ExceptionHandler(NotFound.class)
	public ResponseEntity<ErrorResponse> notFound(NotFound exception) {
		return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), null);
	}

	@ExceptionHandler(Conflict.class)
	public ResponseEntity<ErrorResponse> conflict(Conflict exception) {
		return response(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(StorageUnavailable.class)
	public ResponseEntity<ErrorResponse> storageUnavailable(StorageUnavailable exception) {
		LOGGER.warn("Storage operation failed: {}", exception.getMessage());
		return response(HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE",
			exception.getMessage(), null);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
		return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
			"La solicitud contiene parámetros o JSON inválidos.", null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
		String requestId = requestId();
		LOGGER.error("Unexpected error processing upload request. requestId={}", requestId, exception);
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
