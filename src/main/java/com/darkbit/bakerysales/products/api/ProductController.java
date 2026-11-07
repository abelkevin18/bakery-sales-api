package com.darkbit.bakerysales.products.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.darkbit.bakerysales.products.application.ProductExceptions.InvalidInput;
import com.darkbit.bakerysales.products.application.ProductRepository.ProductPage;
import com.darkbit.bakerysales.products.application.ProductService;
import com.darkbit.bakerysales.products.application.ProductService.ImageChange;
import com.darkbit.bakerysales.products.domain.Product;
import tools.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
public class ProductController {

	private static final String IMAGE_ERROR =
		"La imagen debe ser la URL pública devuelta por /admin/uploads/sign.";

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@GetMapping("/products")
	public DataResponse<List<PublicProductResponse>> listPublic(
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Boolean featured) {
		return new DataResponse<>(service.listPublic(category, featured).stream()
			.map(PublicProductResponse::from)
			.toList());
	}

	@GetMapping("/products/{id}")
	public DataResponse<PublicProductResponse> getPublic(@PathVariable UUID id) {
		return new DataResponse<>(PublicProductResponse.from(service.getPublic(id)));
	}

	@GetMapping("/admin/products")
	public ListResponse<ProductResponse> listAdmin(
			@RequestParam(defaultValue = "all") String status,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) Boolean featured,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "-updatedAt") String sort) {
		ProductPage result =
			service.listAdmin(status, q, categoryId, featured, page, pageSize, sort);
		int totalPages = (int) ((result.total() + pageSize - 1) / pageSize);
		return new ListResponse<>(
			result.data().stream().map(ProductResponse::from).toList(),
			new PageMeta(page, pageSize, result.total(), totalPages));
	}

	@GetMapping("/admin/products/{id}")
	public DataResponse<ProductResponse> get(@PathVariable UUID id) {
		return new DataResponse<>(ProductResponse.from(service.get(id)));
	}

	@PostMapping("/admin/products")
	public ResponseEntity<DataResponse<ProductResponse>> create(
			@Valid @RequestBody CreateProductRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		Product created = service.create(
			request.name(), request.categoryId(), request.description(), request.size(),
			request.priceFrom(), request.image(), request.featured(), request.active());
		URI location = URI.create("/api/v1/admin/products/" + created.id());
		return ResponseEntity.created(location)
			.body(new DataResponse<>(ProductResponse.from(created)));
	}

	@PatchMapping("/admin/products/{id}")
	public DataResponse<ProductResponse> update(
			@PathVariable UUID id,
			@RequestBody(required = false) PatchProductRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		return new DataResponse<>(ProductResponse.from(service.update(
			id, request.name(), request.categoryId(), request.description(), request.size(),
			request.priceFrom(), imageChange(request.image()),
			request.featured(), request.active())));
	}

	@DeleteMapping("/admin/products/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	private ImageChange imageChange(JsonNode image) {
		if (image == null) {
			return ImageChange.absent();
		}
		if (image.isNull()) {
			return ImageChange.clear();
		}
		if (!image.isString()) {
			throw new InvalidInput(Map.of("image", List.of(IMAGE_ERROR)));
		}
		return ImageChange.set(image.asString());
	}

	private InvalidInput invalidBody() {
		return new InvalidInput(Map.of("body",
			List.of("El cuerpo de la solicitud es obligatorio.")));
	}

	public record CreateProductRequest(
			@NotBlank(message = "El nombre es obligatorio.")
			@Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres.")
			String name,
			@NotNull(message = "La categoría es obligatoria.")
			UUID categoryId,
			@NotBlank(message = "La descripción es obligatoria.")
			@Size(min = 10, max = 240, message = "Debe tener entre 10 y 240 caracteres.")
			String description,
			@NotBlank(message = "El tamaño es obligatorio.")
			@Size(min = 2, max = 60, message = "Debe tener entre 2 y 60 caracteres.")
			String size,
			@NotNull(message = "El precio es obligatorio.")
			@DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0.")
			@DecimalMax(value = "10000", message = "El precio no puede superar los 10000.")
			@Digits(integer = 5, fraction = 2,
				message = "El precio admite como máximo 2 decimales.")
			BigDecimal priceFrom,
			@NotBlank(message = "La imagen es obligatoria.")
			String image,
			@NotNull(message = "featured es obligatorio.")
			Boolean featured,
			@NotNull(message = "active es obligatorio.")
			Boolean active) {
	}

	public record PatchProductRequest(
			String name,
			UUID categoryId,
			String description,
			String size,
			BigDecimal priceFrom,
			JsonNode image,
			Boolean featured,
			Boolean active) {
	}

	public record CategoryRef(UUID id, String name, String slug) {
	}

	/** En público basta el slug (clave de filtro) y el nombre visible. */
	public record PublicCategoryRef(String slug, String name) {
	}

	/** Vista pública: sin `active` ni auditoría, y sin `categoryId` (basta el slug). */
	public record PublicProductResponse(
			UUID id,
			String name,
			PublicCategoryRef category,
			String description,
			String size,
			BigDecimal priceFrom,
			String image,
			boolean featured) {
		private static PublicProductResponse from(Product product) {
			return new PublicProductResponse(
				product.id(),
				product.name(),
				new PublicCategoryRef(product.categorySlug(), product.categoryName()),
				product.description(),
				product.size(),
				product.priceFrom(),
				product.imageUrl(),
				product.featured());
		}
	}

	public record ProductResponse(
			UUID id,
			String name,
			UUID categoryId,
			CategoryRef category,
			String description,
			String size,
			BigDecimal priceFrom,
			String image,
			boolean featured,
			boolean active,
			Instant createdAt,
			Instant updatedAt) {
		private static ProductResponse from(Product product) {
			return new ProductResponse(
				product.id(),
				product.name(),
				product.categoryId(),
				new CategoryRef(product.categoryId(), product.categoryName(),
					product.categorySlug()),
				product.description(),
				product.size(),
				product.priceFrom(),
				product.imageUrl(),
				product.featured(),
				product.active(),
				product.createdAt(),
				product.updatedAt());
		}
	}

	public record DataResponse<T>(T data) {
	}

	public record ListResponse<T>(List<T> data, PageMeta meta) {
	}

	public record PageMeta(int page, int pageSize, long total, int totalPages) {
	}
}
