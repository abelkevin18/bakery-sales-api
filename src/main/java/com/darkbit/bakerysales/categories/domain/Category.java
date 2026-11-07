package com.darkbit.bakerysales.categories.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * El slug es inmutable: se genera al crear la categoría y no cambia aunque se
 * renombre, para no romper enlaces ni filtros ya compartidos.
 */
public record Category(
		UUID id,
		String name,
		String slug,
		String description,
		boolean active,
		int productCount,
		Instant createdAt,
		Instant updatedAt) {
}
