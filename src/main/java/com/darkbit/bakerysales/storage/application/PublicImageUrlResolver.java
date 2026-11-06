package com.darkbit.bakerysales.storage.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.darkbit.bakerysales.storage.infrastructure.SupabaseProperties;

/**
 * Deriva el path de storage a partir de la URL pública, de modo que el cliente
 * solo tenga que enviar un campo y nunca pueda desalinear url y path.
 */
@Component
public class PublicImageUrlResolver {

	private final String prefix;

	public PublicImageUrlResolver(SupabaseProperties properties) {
		this.prefix = properties.baseUrl() + "/storage/v1/object/public/"
			+ properties.storage().bucket() + "/";
	}

	public Optional<String> resolvePath(String publicUrl) {
		if (publicUrl == null || !publicUrl.startsWith(prefix)) {
			return Optional.empty();
		}
		String path = publicUrl.substring(prefix.length());
		return path.isBlank() ? Optional.empty() : Optional.of(path);
	}

	public String publicUrl(String path) {
		return prefix + path;
	}
}
