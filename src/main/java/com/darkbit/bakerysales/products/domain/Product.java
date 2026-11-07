package com.darkbit.bakerysales.products.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un producto siempre arrastra los datos de su categoría: la landing los
 * necesita para pintar los filtros y el panel admin para el select de la tabla,
 * así que se resuelven en la misma consulta y nunca se envían desde el cliente.
 */
public record Product(
		UUID id,
		String name,
		UUID categoryId,
		String categoryName,
		String categorySlug,
		String description,
		String size,
		BigDecimal priceFrom,
		String imageUrl,
		String imagePath,
		boolean featured,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
