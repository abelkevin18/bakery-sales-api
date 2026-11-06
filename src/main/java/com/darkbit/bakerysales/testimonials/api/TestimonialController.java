package com.darkbit.bakerysales.testimonials.api;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.darkbit.bakerysales.testimonials.application.TestimonialExceptions.InvalidInput;
import com.darkbit.bakerysales.testimonials.application.TestimonialRepository.TestimonialPage;
import com.darkbit.bakerysales.testimonials.application.TestimonialService;
import com.darkbit.bakerysales.testimonials.application.TestimonialService.ImageChange;
import com.darkbit.bakerysales.testimonials.domain.Testimonial;
import tools.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class TestimonialController {

	private final TestimonialService service;

	public TestimonialController(TestimonialService service) {
		this.service = service;
	}

	@GetMapping("/testimonials")
	public DataResponse<List<TestimonialResponse>> listPublished(
			@RequestParam(defaultValue = "6") int limit) {
		return new DataResponse<>(service.listPublished(limit).stream()
			.map(TestimonialResponse::from)
			.toList());
	}

	@GetMapping("/admin/testimonials")
	public ListResponse<TestimonialResponse> listAdmin(
			@RequestParam(defaultValue = "all") String status,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Integer rating,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "-createdAt") String sort) {
		TestimonialPage result = service.listAdmin(status, q, rating, page, pageSize, sort);
		int totalPages = (int) ((result.total() + pageSize - 1) / pageSize);
		return new ListResponse<>(
			result.data().stream().map(TestimonialResponse::from).toList(),
			new PageMeta(page, pageSize, result.total(), totalPages));
	}

	@GetMapping("/admin/testimonials/{id}")
	public DataResponse<TestimonialResponse> get(@PathVariable UUID id) {
		return new DataResponse<>(TestimonialResponse.from(service.get(id)));
	}

	@PostMapping("/admin/testimonials")
	public ResponseEntity<DataResponse<TestimonialResponse>> create(
			@Valid @RequestBody CreateTestimonialRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		Testimonial created = service.create(
			request.name(), request.occasion(), request.rating(), request.text(),
			request.image(), request.published());
		URI location = URI.create("/api/v1/admin/testimonials/" + created.id());
		return ResponseEntity.created(location)
			.body(new DataResponse<>(TestimonialResponse.from(created)));
	}

	@PatchMapping("/admin/testimonials/{id}")
	public DataResponse<TestimonialResponse> update(
			@PathVariable UUID id,
			@RequestBody(required = false) PatchTestimonialRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		return new DataResponse<>(TestimonialResponse.from(service.update(
			id, request.name(), request.occasion(), request.rating(), request.text(),
			imageChange(request.image()), request.published())));
	}

	@DeleteMapping("/admin/testimonials/{id}")
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
			throw new InvalidInput(Map.of("image",
				List.of("La imagen debe ser la URL pública devuelta por /admin/uploads/sign o null.")));
		}
		return ImageChange.set(image.asString());
	}

	private InvalidInput invalidBody() {
		return new InvalidInput(Map.of("body", List.of("El cuerpo de la solicitud es obligatorio.")));
	}

	public record CreateTestimonialRequest(
			@NotBlank(message = "El nombre es obligatorio.")
			@Size(min = 3, max = 60, message = "Debe tener entre 3 y 60 caracteres.")
			String name,
			@NotBlank(message = "La ocasión es obligatoria.")
			@Size(min = 3, max = 60, message = "Debe tener entre 3 y 60 caracteres.")
			String occasion,
			@NotNull(message = "La calificación es obligatoria.")
			@Min(value = 1, message = "La calificación debe ser un entero entre 1 y 5.")
			@Max(value = 5, message = "La calificación debe ser un entero entre 1 y 5.")
			Integer rating,
			@NotBlank(message = "El texto es obligatorio.")
			@Size(min = 20, max = 400, message = "Debe tener entre 20 y 400 caracteres.")
			String text,
			String image,
			@NotNull(message = "published es obligatorio.")
			Boolean published) {
	}

	public record PatchTestimonialRequest(
			String name,
			String occasion,
			Integer rating,
			String text,
			JsonNode image,
			Boolean published) {
	}

	public record TestimonialResponse(
			UUID id,
			String name,
			String occasion,
			int rating,
			String text,
			String image,
			boolean published,
			java.time.Instant createdAt,
			java.time.Instant updatedAt) {
		private static TestimonialResponse from(Testimonial testimonial) {
			return new TestimonialResponse(
				testimonial.id(),
				testimonial.name(),
				testimonial.occasion(),
				testimonial.rating(),
				testimonial.text(),
				testimonial.imageUrl(),
				testimonial.published(),
				testimonial.createdAt(),
				testimonial.updatedAt());
		}
	}

	public record DataResponse<T>(T data) {
	}

	public record ListResponse<T>(List<T> data, PageMeta meta) {
	}

	public record PageMeta(int page, int pageSize, long total, int totalPages) {
	}
}
