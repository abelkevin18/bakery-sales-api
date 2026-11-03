package com.darkbit.bakerysales.faqs.api;

import com.darkbit.bakerysales.faqs.application.FaqExceptions.InvalidInput;
import com.darkbit.bakerysales.faqs.application.FaqRepository.FaqPage;
import com.darkbit.bakerysales.faqs.application.FaqService;
import com.darkbit.bakerysales.faqs.application.FaqService.OrderChange;
import com.darkbit.bakerysales.faqs.domain.Faq;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FaqController {

	private final FaqService service;

	public FaqController(FaqService service) {
		this.service = service;
	}

	@GetMapping("/faqs")
	public DataResponse<List<FaqResponse>> listPublished() {
		return new DataResponse<>(service.listPublished().stream().map(FaqResponse::from).toList());
	}

	@GetMapping("/admin/faqs")
	public ListResponse<FaqResponse> listAdmin(
			@RequestParam(defaultValue = "all") String status,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "order") String sort) {
		FaqPage result = service.listAdmin(status, q, page, pageSize, sort);
		int totalPages = (int) ((result.total() + pageSize - 1) / pageSize);
		return new ListResponse<>(
			result.data().stream().map(FaqResponse::from).toList(),
			new PageMeta(page, pageSize, result.total(), totalPages));
	}

	@GetMapping("/admin/faqs/{id}")
	public DataResponse<FaqResponse> get(@PathVariable UUID id) {
		return new DataResponse<>(FaqResponse.from(service.get(id)));
	}

	@PostMapping("/admin/faqs")
	public ResponseEntity<DataResponse<FaqResponse>> create(@Valid @RequestBody CreateFaqRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		Faq created = service.create(
			request.question(), request.answer(), request.order(), request.published());
		URI location = URI.create("/api/v1/admin/faqs/" + created.id());
		return ResponseEntity.created(location).body(new DataResponse<>(FaqResponse.from(created)));
	}

	@PatchMapping("/admin/faqs/{id}")
	public DataResponse<FaqResponse> update(
			@PathVariable UUID id,
			@RequestBody PatchFaqRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		return new DataResponse<>(FaqResponse.from(service.update(
			id, request.question(), request.answer(), request.order(), request.published())));
	}

	@DeleteMapping("/admin/faqs/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/admin/faqs/order")
	public DataResponse<List<FaqResponse>> reorder(@Valid @RequestBody ReorderFaqRequest request) {
		if (request == null) {
			throw invalidBody();
		}
		List<OrderChange> changes = request.items().stream()
			.map(item -> item == null
				? null
				: new OrderChange(item.id(), item.order() == null ? 0 : item.order()))
			.toList();
		return new DataResponse<>(service.reorder(changes).stream().map(FaqResponse::from).toList());
	}

	private InvalidInput invalidBody() {
		return new InvalidInput(Map.of("body", List.of("El cuerpo de la solicitud es obligatorio.")));
	}

	public record CreateFaqRequest(
			@NotBlank(message = "La pregunta es obligatoria.")
			@Size(min = 8, max = 140, message = "Debe tener entre 8 y 140 caracteres.")
			String question,
			@NotBlank(message = "La respuesta es obligatoria.")
			@Size(min = 20, max = 600, message = "Debe tener entre 20 y 600 caracteres.")
			String answer,
			@NotNull(message = "El orden es obligatorio.")
			@Min(value = 1, message = "El orden debe estar entre 1 y 999.")
			@Max(value = 999, message = "El orden debe estar entre 1 y 999.")
			Integer order,
			@NotNull(message = "published es obligatorio.")
			Boolean published) {
	}

	public record PatchFaqRequest(String question, String answer, Integer order, Boolean published) {
	}

	public record ReorderFaqRequest(
			@NotNull(message = "Debe enviar la lista items.")
			@Size(min = 1, message = "Debe enviar al menos una FAQ.")
			List<@Valid ReorderFaqItem> items) {
	}

	public record ReorderFaqItem(
			@NotNull(message = "El id es obligatorio.")
			UUID id,
			@NotNull(message = "El orden es obligatorio.")
			@Min(value = 1, message = "El orden debe estar entre 1 y 999.")
			@Max(value = 999, message = "El orden debe estar entre 1 y 999.")
			Integer order) {
	}

	public record FaqResponse(
			UUID id,
			String question,
			String answer,
			int order,
			boolean published,
			java.time.Instant createdAt,
			java.time.Instant updatedAt) {
		private static FaqResponse from(Faq faq) {
			return new FaqResponse(faq.id(), faq.question(), faq.answer(), faq.order(),
				faq.published(), faq.createdAt(), faq.updatedAt());
		}
	}

	public record DataResponse<T>(T data) {
	}

	public record ListResponse<T>(List<T> data, PageMeta meta) {
	}

	public record PageMeta(int page, int pageSize, long total, int totalPages) {
	}
}
