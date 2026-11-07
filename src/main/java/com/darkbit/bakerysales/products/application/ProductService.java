package com.darkbit.bakerysales.products.application;

import static com.darkbit.bakerysales.products.application.ProductExceptions.BadRequest;
import static com.darkbit.bakerysales.products.application.ProductExceptions.InvalidInput;
import static com.darkbit.bakerysales.products.application.ProductExceptions.NotFound;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.darkbit.bakerysales.products.application.ProductRepository.ProductPage;
import com.darkbit.bakerysales.products.domain.Product;
import com.darkbit.bakerysales.storage.application.ImageCleanupService;
import com.darkbit.bakerysales.storage.application.PublicImageUrlResolver;

@Service
public class ProductService {

	private static final Set<String> STATUSES = Set.of("active", "inactive", "all");
	private static final Set<String> SORT_FIELDS =
		Set.of("name", "priceFrom", "updatedAt", "createdAt");
	private static final BigDecimal MAX_PRICE = new BigDecimal("10000");
	private static final String IMAGE_ERROR =
		"La imagen debe ser la URL pública devuelta por /admin/uploads/sign.";
	private static final String IMAGE_REQUIRED_ERROR =
		"La imagen no se puede quitar; envía una nueva URL para reemplazarla.";

	private final ProductRepository repository;
	private final PublicImageUrlResolver imageResolver;
	private final ImageCleanupService imageCleanup;

	public ProductService(
			ProductRepository repository,
			PublicImageUrlResolver imageResolver,
			ImageCleanupService imageCleanup) {
		this.repository = repository;
		this.imageResolver = imageResolver;
		this.imageCleanup = imageCleanup;
	}

	@Transactional(readOnly = true)
	public List<Product> listPublic(String categorySlug, Boolean featured) {
		String slug = categorySlug == null ? null : categorySlug.trim();
		return repository.findPublic(
			slug == null || slug.isBlank() ? null : slug,
			featured);
	}

	@Transactional(readOnly = true)
	public Product getPublic(UUID id) {
		return repository.findPublicById(id).orElseThrow(ProductExceptions.NotFound::new);
	}

	@Transactional(readOnly = true)
	public ProductPage listAdmin(
			String status, String query, UUID categoryId, Boolean featured,
			int page, int pageSize, String sort) {
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
			sort = "-updatedAt";
		}
		String sortField = sort.startsWith("-") ? sort.substring(1) : sort;
		if (!SORT_FIELDS.contains(sortField)) {
			throw new BadRequest("El parámetro sort no contiene un campo permitido.");
		}
		return repository.findAdmin(
			normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery,
			categoryId, status, featured, page, pageSize, sort);
	}

	@Transactional(readOnly = true)
	public Product get(UUID id) {
		return repository.findById(id).orElseThrow(NotFound::new);
	}

	@Transactional
	public Product create(
			String name, UUID categoryId, String description, String size,
			BigDecimal priceFrom, String image, boolean featured, boolean active) {
		Map<String, List<String>> errors = new HashMap<>();
		validate(name, description, size, priceFrom, errors);
		validateCategory(categoryId, errors);
		String imageUrl = normalizeImage(image);
		String imagePath = resolveImagePath(imageUrl, errors);
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}
		return repository.create(
			name.trim(), categoryId, description.trim(), size.trim(),
			priceFrom, imageUrl, imagePath, featured, active);
	}

	@Transactional
	public Product update(
			UUID id, String name, UUID categoryId, String description, String size,
			BigDecimal priceFrom, ImageChange imageChange, Boolean featured, Boolean active) {
		Product existing = get(id);
		if (name == null && categoryId == null && description == null && size == null
				&& priceFrom == null && !imageChange.present()
				&& featured == null && active == null) {
			throw new InvalidInput(Map.of("body",
				List.of("Debe enviar al menos un campo para actualizar.")));
		}

		String updatedName = name == null ? existing.name() : name.trim();
		String updatedDescription =
			description == null ? existing.description() : description.trim();
		String updatedSize = size == null ? existing.size() : size.trim();
		BigDecimal updatedPrice = priceFrom == null ? existing.priceFrom() : priceFrom;
		UUID updatedCategoryId = categoryId == null ? existing.categoryId() : categoryId;

		Map<String, List<String>> errors = new HashMap<>();
		validate(updatedName, updatedDescription, updatedSize, updatedPrice, errors);
		if (categoryId != null && !categoryId.equals(existing.categoryId())) {
			validateCategory(categoryId, errors);
		}

		String imageUrl = existing.imageUrl();
		String imagePath = existing.imagePath();
		if (imageChange.present()) {
			if (imageChange.url() == null) {
				errors.put("image", List.of(IMAGE_REQUIRED_ERROR));
			}
			else {
				imageUrl = normalizeImage(imageChange.url());
				imagePath = resolveImagePath(imageUrl, errors);
			}
		}
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}

		Product updated = repository.update(new Product(
			existing.id(), updatedName, updatedCategoryId,
			existing.categoryName(), existing.categorySlug(),
			updatedDescription, updatedSize, updatedPrice,
			imageUrl, imagePath,
			featured == null ? existing.featured() : featured,
			active == null ? existing.active() : active,
			existing.createdAt(), existing.updatedAt()));
		if (!Objects.equals(existing.imagePath(), updated.imagePath())) {
			imageCleanup.deleteAfterCommit(existing.imagePath());
		}
		return updated;
	}

	@Transactional
	public void delete(UUID id) {
		Product existing = get(id);
		repository.deleteById(id);
		imageCleanup.deleteAfterCommit(existing.imagePath());
	}

	private void validateCategory(UUID categoryId, Map<String, List<String>> errors) {
		if (categoryId == null) {
			errors.put("categoryId", List.of("La categoría es obligatoria."));
			return;
		}
		if (!repository.categoryExists(categoryId)) {
			errors.put("categoryId", List.of("La categoría seleccionada no existe."));
		}
	}

	private void validate(
			String name, String description, String size, BigDecimal priceFrom,
			Map<String, List<String>> errors) {
		if (name == null || name.trim().length() < 3 || name.trim().length() > 80) {
			errors.put("name", List.of("Debe tener entre 3 y 80 caracteres."));
		}
		if (description == null
				|| description.trim().length() < 10 || description.trim().length() > 240) {
			errors.put("description", List.of("Debe tener entre 10 y 240 caracteres."));
		}
		if (size == null || size.trim().length() < 2 || size.trim().length() > 60) {
			errors.put("size", List.of("Debe tener entre 2 y 60 caracteres."));
		}
		validatePrice(priceFrom, errors);
	}

	private void validatePrice(BigDecimal priceFrom, Map<String, List<String>> errors) {
		if (priceFrom == null) {
			errors.put("priceFrom", List.of("El precio es obligatorio."));
			return;
		}
		if (priceFrom.signum() <= 0) {
			errors.put("priceFrom", List.of("El precio debe ser mayor a 0."));
			return;
		}
		if (priceFrom.compareTo(MAX_PRICE) > 0) {
			errors.put("priceFrom", List.of("El precio no puede superar los 10000."));
			return;
		}
		if (priceFrom.stripTrailingZeros().scale() > 2) {
			errors.put("priceFrom", List.of("El precio admite como máximo 2 decimales."));
		}
	}

	private String normalizeImage(String image) {
		if (image == null || image.isBlank()) {
			return null;
		}
		return image.trim();
	}

	private String resolveImagePath(String imageUrl, Map<String, List<String>> errors) {
		if (imageUrl == null) {
			errors.put("image", List.of(IMAGE_ERROR));
			return null;
		}
		return imageResolver.resolvePath(imageUrl).orElseGet(() -> {
			errors.put("image", List.of(IMAGE_ERROR));
			return null;
		});
	}

	public record ImageChange(boolean present, String url) {

		public static ImageChange absent() {
			return new ImageChange(false, null);
		}

		public static ImageChange clear() {
			return new ImageChange(true, null);
		}

		public static ImageChange set(String url) {
			return new ImageChange(true, url);
		}
	}
}
