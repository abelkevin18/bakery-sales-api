package com.darkbit.bakerysales.categories.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.categories.domain.Category;

public interface CategoryRepository {

	/** Categorías activas con el conteo de productos activos. */
	List<Category> findActive();

	/** Categorías según status con el conteo total de productos. */
	CategoryPage findAdmin(String status, String query, int page, int pageSize, String sort);

	Optional<Category> findById(UUID id);

	boolean slugExists(String slug, UUID excludeId);

	Category create(String name, String slug, String description, boolean active);

	Category update(Category category);

	void deleteById(UUID id);

	long countProducts(UUID categoryId);

	record CategoryPage(List<Category> data, long total) {
	}
}
