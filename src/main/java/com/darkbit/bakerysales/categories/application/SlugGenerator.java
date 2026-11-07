package com.darkbit.bakerysales.categories.application;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Convierte un nombre en slug: minúsculas, sin acentos, separado por guiones.
 * "Tortas Clásicas" -> "tortas-clasicas"
 */
@Component
public class SlugGenerator {

	private static final int MAX_LENGTH = 50;
	private static final String FALLBACK = "categoria";

	public String generate(String name) {
		if (name == null || name.isBlank()) {
			return FALLBACK;
		}
		String withoutAccents = Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT),
			Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		String slug = withoutAccents
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-+|-+$", "");
		if (slug.length() > MAX_LENGTH) {
			slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
		}
		return slug.isBlank() ? FALLBACK : slug;
	}
}
