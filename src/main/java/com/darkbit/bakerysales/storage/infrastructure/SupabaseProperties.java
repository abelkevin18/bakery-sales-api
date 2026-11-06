package com.darkbit.bakerysales.storage.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String url, Storage storage) {

	public SupabaseProperties {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("app.supabase.url es obligatorio.");
		}
		if (storage == null) {
			throw new IllegalArgumentException("app.supabase.storage es obligatorio.");
		}
	}

	public String baseUrl() {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	public record Storage(String bucket, String serviceRoleKey, Duration signedUrlTtl, DataSize maxFileSize) {

		public Storage {
			if (bucket == null || bucket.isBlank()) {
				throw new IllegalArgumentException("app.supabase.storage.bucket es obligatorio.");
			}
			if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
				throw new IllegalArgumentException("app.supabase.storage.service-role-key es obligatorio.");
			}
			if (signedUrlTtl == null) {
				signedUrlTtl = Duration.ofMinutes(5);
			}
			if (maxFileSize == null) {
				maxFileSize = DataSize.ofMegabytes(5);
			}
		}
	}
}
