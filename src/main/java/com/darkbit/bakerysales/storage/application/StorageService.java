package com.darkbit.bakerysales.storage.application;

import static com.darkbit.bakerysales.storage.application.StorageExceptions.Conflict;
import static com.darkbit.bakerysales.storage.application.StorageExceptions.InvalidInput;
import static com.darkbit.bakerysales.storage.application.StorageExceptions.PayloadTooLarge;
import static com.darkbit.bakerysales.storage.application.StorageExceptions.UnsupportedMediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.darkbit.bakerysales.storage.application.StorageGateway.SignedUpload;
import com.darkbit.bakerysales.storage.infrastructure.JdbcImageUsageRepository;
import com.darkbit.bakerysales.storage.infrastructure.SupabaseProperties;

@Service
public class StorageService {

	private static final Set<String> FOLDERS = Set.of("products", "testimonials");
	private static final Map<String, String> EXTENSIONS = Map.of(
		"image/jpeg", "jpg",
		"image/png", "png",
		"image/webp", "webp");
	private static final Pattern PATH_PATTERN =
		Pattern.compile("^(products|testimonials)/[a-z0-9-]+\\.(jpg|jpeg|png|webp)$");

	private final StorageGateway gateway;
	private final JdbcImageUsageRepository usageRepository;
	private final SupabaseProperties properties;

	public StorageService(
			StorageGateway gateway,
			JdbcImageUsageRepository usageRepository,
			SupabaseProperties properties) {
		this.gateway = gateway;
		this.usageRepository = usageRepository;
		this.properties = properties;
	}

	public SignedImage signUpload(String folder, String contentType, Long size) {
		Map<String, List<String>> errors = new HashMap<>();
		String normalizedFolder = folder == null ? null : folder.trim().toLowerCase(Locale.ROOT);
		if (normalizedFolder == null || !FOLDERS.contains(normalizedFolder)) {
			errors.put("folder", List.of("El folder debe ser products o testimonials."));
		}
		String normalizedContentType = normalizeContentType(contentType);
		if (size == null || size <= 0) {
			errors.put("size", List.of("El tamaño del archivo debe ser mayor a 0."));
		}
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}
		if (!EXTENSIONS.containsKey(normalizedContentType)) {
			throw new UnsupportedMediaType("El archivo debe ser JPEG, PNG o WebP.");
		}
		if (size > properties.storage().maxFileSize().toBytes()) {
			throw new PayloadTooLarge("La imagen no puede superar los "
				+ properties.storage().maxFileSize().toMegabytes() + " MB.");
		}

		String path = normalizedFolder + "/" + UUID.randomUUID() + "." + EXTENSIONS.get(normalizedContentType);
		SignedUpload upload = gateway.createSignedUpload(path, properties.storage().signedUrlTtl());
		return new SignedImage(
			path,
			upload.uploadUrl(),
			publicUrl(path),
			properties.storage().signedUrlTtl().toSeconds());
	}

	public void delete(String path) {
		String normalized = path == null ? null : path.trim();
		if (normalized == null || !PATH_PATTERN.matcher(normalized).matches()) {
			throw new InvalidInput(Map.of("path", List.of("La ruta de la imagen no es válida.")));
		}
		if (usageRepository.isReferenced(normalized)) {
			throw new Conflict("La imagen está siendo usada por un registro existente.");
		}
		gateway.delete(normalized);
	}

	private String publicUrl(String path) {
		return properties.baseUrl() + "/storage/v1/object/public/"
			+ properties.storage().bucket() + "/" + path;
	}

	private String normalizeContentType(String contentType) {
		if (contentType == null) {
			return "";
		}
		int separator = contentType.indexOf(';');
		String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
		return value.trim().toLowerCase(Locale.ROOT);
	}

	public record SignedImage(String path, String uploadUrl, String publicUrl, long expiresIn) {
	}
}
