package com.darkbit.bakerysales.categories.api;

import com.darkbit.bakerysales.categories.application.CategoryExceptions.InvalidInput;
import com.darkbit.bakerysales.categories.application.CategoryRepository.CategoryPage;
import com.darkbit.bakerysales.categories.application.CategoryService;
import com.darkbit.bakerysales.categories.domain.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

	private final CategoryService service;

	public CategoryController(CategoryService service) {
		this.service = service;
	}

	@GetMapping("/categories")
	public DataResponse<List<PublicCategoryResponse>> listActive() {
		return new DataResponse<>(service.listActive().stream()
			.map(PublicCategoryResponse::from)
			.toList());
	}

	@GetMapping("/admin/categories")
	public ListResponse<CategoryResponse> listAdmin(
			@RequestParam(defaultValue = "all") String status,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "name") String sort) {
		CategoryPage result = service.listAdmin(status, q, page, pageSize, sort);
		int totalPages = (int) ((result.total() + pageSize - 1) / pageSize);
		return new ListResponse<>(
			result.data().stream().map(CategoryResponse::from).toList(),
			new PageMeta(page, pageSize, result.total(), totalPages));
	}

	@GetMapping("/admin/categories/{id}")
	public DataResponse<CategoryResponse> get(@PathVariable UUID id) {
		return new DataResponse<>(CategoryResponse.from(service.get(id)));
	}

	@PostMapping("/admin/categories")
	public ResponseEntity<DataResponse<CategoryResponse>> create(
			@Valid @RequestBody CreateCategoryRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		Category created = service.create(
			request.name(), request.description(), request.active());
		URI location = URI.create("/api/v1/admin/categories/" + created.id());
		return ResponseEntity.created(location)
			.body(new DataResponse<>(CategoryResponse.from(created)));
	}

	@PatchMapping("/admin/categories/{id}")
	public DataResponse<CategoryResponse> update(
			@PathVariable UUID id,
			@RequestBody(required = false) PatchCategoryRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		return new DataResponse<>(CategoryResponse.from(service.update(
			id, request.name(), request.description(), request.active())));
	}

	@DeleteMapping("/admin/categories/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	private InvalidInput invalidBody() {
		return new InvalidInput(Map.of("body", List.of("El cuerpo de la solicitud es obligatorio.")));
	}

	public record CreateCategoryRequest(
			@NotBlank(message = "El nombre es obligatorio.")
			@Size(min = 3, max = 50, message = "Debe tener entre 3 y 50 caracteres.")
			String name,
			@Size(max = 160, message = "No puede superar los 160 caracteres.")
			String description,
			@NotNull(message = "active es obligatorio.")
			Boolean active) {
	}

	public record PatchCategoryRequest(String name, String description, Boolean active) {
	}

	/** Vista pública: sin auditoría; `productCount` cuenta solo productos activos. */
	public record PublicCategoryResponse(
			UUID id,
			String name,
			String slug,
			String description,
			boolean active,
			int productCount) {
		private static PublicCategoryResponse from(Category category) {
			return new PublicCategoryResponse(
				category.id(), category.name(), category.slug(),
				category.description(), category.active(), category.productCount());
		}
	}

	public record CategoryResponse(
			UUID id,
			String name,
			String slug,
			String description,
			boolean active,
			int productCount,
			Instant createdAt,
			Instant updatedAt) {
		private static CategoryResponse from(Category category) {
			return new CategoryResponse(
				category.id(), category.name(), category.slug(), category.description(),
				category.active(), category.productCount(),
				category.createdAt(), category.updatedAt());
		}
	}

	public record DataResponse<T>(T data) {
	}

	public record ListResponse<T>(List<T> data, PageMeta meta) {
	}

	public record PageMeta(int page, int pageSize, long total, int totalPages) {
	}
}
