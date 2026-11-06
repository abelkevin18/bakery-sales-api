package com.darkbit.bakerysales.storage.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.darkbit.bakerysales.storage.application.StorageExceptions.StorageUnavailable;
import com.darkbit.bakerysales.storage.application.StorageGateway;

@Component
public class SupabaseStorageGateway implements StorageGateway {

	private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseStorageGateway.class);
	private static final String STORAGE_API = "/storage/v1";

	private final RestClient restClient;
	private final SupabaseProperties properties;

	public SupabaseStorageGateway(SupabaseProperties properties) {
		this.properties = properties;
		String serviceRoleKey = properties.storage().serviceRoleKey();
		this.restClient = RestClient.builder()
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
			.defaultHeader("apikey", serviceRoleKey)
			.build();
	}

	@Override
	public SignedUpload createSignedUpload(String path, Duration ttl) {
		URI uri = storageUri("/object/upload/sign/" + properties.storage().bucket() + "/" + path);
		SignedUploadPayload payload;
		try {
			payload = restClient.post()
				.uri(uri)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("expiresIn", Math.max(1, ttl.toSeconds())))
				.retrieve()
				.body(SignedUploadPayload.class);
		}
		catch (RestClientException exception) {
			LOGGER.warn("Supabase Storage rejected the signed upload request. path={}", path, exception);
			throw new StorageUnavailable("No se pudo generar la URL de subida.");
		}
		if (payload == null || payload.url() == null || payload.url().isBlank()) {
			throw new StorageUnavailable("No se pudo generar la URL de subida.");
		}
		return new SignedUpload(absoluteUrl(payload.url()), payload.token());
	}

	@Override
	public void delete(String path) {
		URI uri = storageUri("/object/" + properties.storage().bucket() + "/" + path);
		try {
			restClient.delete().uri(uri).retrieve().toBodilessEntity();
		}
		catch (RestClientResponseException exception) {
			if (exception.getStatusCode().value() == 404) {
				return;
			}
			LOGGER.warn("Supabase Storage rejected the delete request. path={} status={}",
				path, exception.getStatusCode(), exception);
			throw new StorageUnavailable("No se pudo eliminar la imagen.");
		}
		catch (RestClientException exception) {
			LOGGER.warn("Supabase Storage is unreachable. path={}", path, exception);
			throw new StorageUnavailable("No se pudo eliminar la imagen.");
		}
	}

	private URI storageUri(String relativePath) {
		return URI.create(properties.baseUrl() + STORAGE_API + relativePath);
	}

	private String absoluteUrl(String signedUrl) {
		if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
			return signedUrl;
		}
		if (signedUrl.startsWith(STORAGE_API + "/")) {
			return properties.baseUrl() + signedUrl;
		}
		return properties.baseUrl() + STORAGE_API + signedUrl;
	}

	private record SignedUploadPayload(String url, String token) {
	}
}
