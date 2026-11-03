package com.darkbit.bakerysales.faqs.application;

import static com.darkbit.bakerysales.faqs.application.FaqExceptions.BadRequest;
import static com.darkbit.bakerysales.faqs.application.FaqExceptions.InvalidInput;
import static com.darkbit.bakerysales.faqs.application.FaqExceptions.NotFound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.darkbit.bakerysales.faqs.application.FaqRepository.FaqPage;
import com.darkbit.bakerysales.faqs.domain.Faq;

@Service
public class FaqService {

	private final FaqRepository repository;

	public FaqService(FaqRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<Faq> listPublished() {
		return repository.findPublished();
	}

	@Transactional(readOnly = true)
	public FaqPage listAdmin(String status, String query, int page, int pageSize, String sort) {
		if (status == null || !Set.of("published", "draft", "all").contains(status)) {
			throw new BadRequest("El parámetro status debe ser published, draft o all.");
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
			sort = "order";
		}
		String sortField = sort.startsWith("-") ? sort.substring(1) : sort;
		if (!Set.of("order", "question", "createdAt", "updatedAt").contains(sortField)) {
			throw new BadRequest("El parámetro sort no contiene un campo permitido.");
		}
		return repository.findAdmin(status,
			normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery,
			page, pageSize, sort);
	}

	@Transactional(readOnly = true)
	public Faq get(UUID id) {
		return repository.findById(id).orElseThrow(NotFound::new);
	}

	@Transactional
	public Faq create(String question, String answer, int order, boolean published) {
		validate(question, answer, order);
		return repository.create(question.trim(), answer.trim(), order, published);
	}

	@Transactional
	public Faq update(UUID id, String question, String answer, Integer order, Boolean published) {
		Faq existing = get(id);
		String updatedQuestion = question == null ? existing.question() : question.trim();
		String updatedAnswer = answer == null ? existing.answer() : answer.trim();
		int updatedOrder = order == null ? existing.order() : order;
		validate(updatedQuestion, updatedAnswer, updatedOrder);
		if (question == null && answer == null && order == null && published == null) {
			throw invalid("body", "Debe enviar al menos un campo para actualizar.");
		}
		return repository.update(new Faq(
			id,
			updatedQuestion,
			updatedAnswer,
			updatedOrder,
			published == null ? existing.published() : published,
			existing.createdAt(),
			existing.updatedAt()));
	}

	@Transactional
	public void delete(UUID id) {
		get(id);
		repository.deleteById(id);
	}

	@Transactional
	public List<Faq> reorder(List<OrderChange> items) {
		if (items == null || items.isEmpty()) {
			throw invalid("items", "Debe enviar al menos una FAQ.");
		}
		Set<UUID> ids = new HashSet<>();
		for (int index = 0; index < items.size(); index++) {
			OrderChange item = items.get(index);
			if (item == null || item.id() == null) {
				throw invalid("items[" + index + "].id", "El id es obligatorio.");
			}
			if (item.order() < 1 || item.order() > 999) {
				throw invalid("items[" + index + "].order", "El orden debe estar entre 1 y 999.");
			}
			if (!ids.add(item.id())) {
				throw invalid("items[" + index + "].id", "No se puede repetir el id.");
			}
		}

		List<Faq> existingFaqs = new ArrayList<>();
		for (OrderChange item : items) {
			Faq faq = repository.findById(item.id()).orElse(null);
			if (faq == null) {
				throw invalid("items", "Una o más FAQs no existen.");
			}
			existingFaqs.add(faq);
		}

		List<Faq> updated = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			Faq faq = existingFaqs.get(index);
			OrderChange item = items.get(index);
			updated.add(repository.update(new Faq(
				faq.id(), faq.question(), faq.answer(), item.order(), faq.published(),
				faq.createdAt(), faq.updatedAt())));
		}
		updated.sort(java.util.Comparator.comparingInt(Faq::order)
			.thenComparing(Faq::createdAt)
			.thenComparing(Faq::id));
		return updated;
	}

	private void validate(String question, String answer, int order) {
		Map<String, List<String>> errors = new HashMap<>();
		if (question == null || question.trim().length() < 8 || question.trim().length() > 140) {
			errors.put("question", List.of("Debe tener entre 8 y 140 caracteres."));
		}
		if (answer == null || answer.trim().length() < 20 || answer.trim().length() > 600) {
			errors.put("answer", List.of("Debe tener entre 20 y 600 caracteres."));
		}
		if (order < 1 || order > 999) {
			errors.put("order", List.of("El orden debe estar entre 1 y 999."));
		}
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}
	}

	private InvalidInput invalid(String field, String message) {
		return new InvalidInput(Map.of(field, List.of(message)));
	}

	public record OrderChange(UUID id, int order) {
	}
}
