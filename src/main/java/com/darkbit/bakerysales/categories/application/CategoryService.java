package com.darkbit.bakerysales.categories.application;

import static com.darkbit.bakerysales.categories.application.CategoryExceptions.BadRequest;
import static com.darkbit.bakerysales.categories.application.CategoryExceptions.Conflict;
import static com.darkbit.bakerysales.categories.application.CategoryExceptions.InvalidInput;
import static com.darkbit.bakerysales.categories.application.CategoryExceptions.NotFound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.darkbit.bakerysales.categories.application.CategoryRepository.CategoryPage;
import com.darkbit.bakerysales.categories.domain.Category;

@Service
public class CategoryService {

	private static final Set<String> STATUSES = Set.of("active", "inactive", "all");
	private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");
	private static final int MAX_SLUG_ATTEMPTS = 50;

	private final CategoryRepository repository;
	private final SlugGenerator slugGenerator;

	public CategoryService(CategoryRepository repository, SlugGenerator slugGenerator) {
		this.repository = repository;
		this.slugGenerator = slugGenerator;
	}

	@Transactional(readOnly = true)
	public List<Category> listActive() {
		return repository.findActive();
	}

	@Transactional(readOnly = true)
	public CategoryPage listAdmin(
			String status, String query, int page, int pageSize, String sort) {
		if (status == null || !STATUSES.contains(status)) {
			throw new BadRequest("El parámetro status debe ser active, inactive o all.");
		}
		if (page < 1) {
			throw new BadRequest("page debe ser mayor o igual a 1.");
		}
		if (pageSize < 1 || pageSize > 100) {
			throw new BadRequest("pageSize debe estar entre 1 y 100.");
		}
		String normalizedQuery = query == null ? null : query.trim();
		if (normalizedQuery != null && normalizedQuery.length() > 100) {
			throw new BadRequest("q no puede superar los 100 caracteres.");
		}
		if (sort == null || sort.isBlank()) {
			sort = "name";
		}
		String sortField = sort.startsWith("-") ? sort.substring(1) : sort;
		if (!SORT_FIELDS.contains(sortField)) {
			throw new BadRequest("El parámetro sort no contiene un campo permitido.");
		}
		return repository.findAdmin(status,
			normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery,
			page, pageSize, sort);
	}

	@Transactional(readOnly = true)
	public Category get(UUID id) {
		return repository.findById(id).orElseThrow(NotFound::new);
	}

	@Transactional
	public Category create(String name, String description, boolean active) {
		Map<String, List<String>> errors = new HashMap<>();
		validate(name, description, errors);
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}
		String slug = uniqueSlug(slugGenerator.generate(name));
		return repository.create(
			name.trim(), slug, normalizeDescription(description), active);
	}

	/**
	 * El slug no se recalcula: cambiarlo rompería enlaces y filtros existentes.
	 * Si se necesita corregir, debe hacerse manualmente y con una redirección.
	 */
	@Transactional
	public Category update(UUID id, String name, String description, Boolean active) {
		Category existing = get(id);
		if (name == null && description == null && active == null) {
			throw new InvalidInput(Map.of("body",
				List.of("Debe enviar al menos un campo para actualizar.")));
		}
		String updatedName = name == null ? existing.name() : name.trim();
		String updatedDescription =
			description == null ? existing.description() : description.trim();

		Map<String, List<String>> errors = new HashMap<>();
		validate(updatedName, updatedDescription, errors);
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}

		return repository.update(new Category(
			existing.id(),
			updatedName,
			existing.slug(),
			updatedDescription,
			active == null ? existing.active() : active,
			existing.productCount(),
			existing.createdAt(),
			existing.updatedAt()));
	}

	@Transactional
	public void delete(UUID id) {
		get(id);
		long products = repository.countProducts(id);
		if (products > 0) {
			throw new Conflict("La categoría tiene productos asociados.", Map.of(
				"products", List.of("Mueve o elimina sus " + products + " productos primero.")));
		}
		repository.deleteById(id);
	}

	private String uniqueSlug(String base) {
		String candidate = base;
		for (int attempt = 2; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
			if (!repository.slugExists(candidate, null)) {
				return candidate;
			}
			candidate = base + "-" + attempt;
		}
		throw new Conflict("No se pudo generar un slug único para la categoría.",
			Map.of("slug", List.of("Ya existe una categoría con un nombre muy similar.")));
	}

	private void validate(
			String name, String description, Map<String, List<String>> errors) {
		if (name == null || name.trim().length() < 3 || name.trim().length() > 50) {
			errors.put("name", List.of("Debe tener entre 3 y 50 caracteres."));
		}
		if (description != null && description.trim().length() > 160) {
			errors.put("description", List.of("No puede superar los 160 caracteres."));
		}
	}

	private String normalizeDescription(String description) {
		return description == null ? "" : description.trim();
	}
}
