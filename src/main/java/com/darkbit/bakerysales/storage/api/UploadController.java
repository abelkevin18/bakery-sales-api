package com.darkbit.bakerysales.storage.api;

import com.darkbit.bakerysales.storage.application.StorageExceptions.InvalidInput;
import com.darkbit.bakerysales.storage.application.StorageService;
import com.darkbit.bakerysales.storage.application.StorageService.SignedImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/uploads")
public class UploadController {

	private final StorageService service;

	public UploadController(StorageService service) {
		this.service = service;
	}

	@PostMapping("/sign")
	public ResponseEntity<DataResponse<SignedUploadResponse>> sign(
			@Valid @RequestBody SignUploadRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		SignedImage signed = service.signUpload(request.folder(), request.contentType(), request.size());
		return ResponseEntity.status(201).body(new DataResponse<>(SignedUploadResponse.from(signed)));
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(
			@RequestBody(required = false) DeleteUploadRequest body,
			@RequestParam(name = "path", required = false) String path) {
		String resolved = body != null && body.path() != null ? body.path() : path;
		if (resolved == null || resolved.isBlank()) {
			throw invalidBody();
		}
		service.delete(resolved);
		return ResponseEntity.noContent().build();
	}

	private InvalidInput invalidBody() {
		return new InvalidInput(Map.of("body", List.of("El cuerpo de la solicitud es obligatorio.")));
	}

	public record SignUploadRequest(
			@NotBlank(message = "El folder es obligatorio.")
			String folder,
			@NotBlank(message = "El contentType es obligatorio.")
			String contentType,
			Long size) {
	}

	public record DeleteUploadRequest(String path) {
	}

	public record SignedUploadResponse(
			String path,
			String uploadUrl,
			String publicUrl,
			long expiresIn) {
		private static SignedUploadResponse from(SignedImage signed) {
			return new SignedUploadResponse(
				signed.path(), signed.uploadUrl(), signed.publicUrl(), signed.expiresIn());
		}
	}

	public record DataResponse<T>(T data) {
	}
}
