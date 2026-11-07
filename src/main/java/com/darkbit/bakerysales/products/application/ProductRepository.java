package com.darkbit.bakerysales.products.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.products.domain.Product;

public interface ProductRepository {

	/**
	 * Productos activos cuya categoría también está activa. Si {@code categorySlug}
	 * no existe o la categoría está inactiva, devuelve una lista vacía.
	 */
	List<Product> findPublic(String categorySlug, Boolean featured);

	ProductPage findAdmin(
			String query, UUID categoryId, String status, Boolean featured,
			int page, int pageSize, String sort);

	Optional<Product> findById(UUID id);

	/** Igual que {@link #findById} pero solo si el producto y su categoría están activos. */
	Optional<Product> findPublicById(UUID id);

	boolean categoryExists(UUID categoryId);

	Product create(
			String name, UUID categoryId, String description, String size,
			BigDecimal priceFrom, String imageUrl, String imagePath,
			boolean featured, boolean active);

	Product update(Product product);

	void deleteById(UUID id);

	record ProductPage(List<Product> data, long total) {
	}
}
