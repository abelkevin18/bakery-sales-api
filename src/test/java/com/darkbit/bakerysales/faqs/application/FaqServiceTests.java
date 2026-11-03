package com.darkbit.bakerysales.faqs.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.faqs.application.FaqExceptions.BadRequest;
import com.darkbit.bakerysales.faqs.application.FaqExceptions.InvalidInput;
import com.darkbit.bakerysales.faqs.domain.Faq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class FaqServiceTests {

	@Mock
	private FaqRepository repository;

	@InjectMocks
	private FaqService service;

	@Test
	void createTrimsTextBeforePersisting() {
		Faq saved = faq("Pregunta válida", "Una respuesta que cumple el mínimo requerido.", 2);
		when(repository.create("Pregunta válida", "Una respuesta que cumple el mínimo requerido.", 2, true))
			.thenReturn(saved);

		Faq result = service.create("  Pregunta válida  ",
			"  Una respuesta que cumple el mínimo requerido.  ", 2, true);

		assertEquals(saved, result);
		verify(repository).create("Pregunta válida", "Una respuesta que cumple el mínimo requerido.", 2, true);
	}

	@Test
	void listAdminRejectsUnsupportedSortBeforeAccessingRepository() {
		assertThrows(BadRequest.class,
			() -> service.listAdmin("all", null, 1, 20, "question;drop table faqs"));
	}

	@Test
	void createRejectsInvalidFieldsWithFieldDetails() {
		InvalidInput exception = assertThrows(InvalidInput.class,
			() -> service.create("short", "tiny", 1000, true));

		assertEquals(List.of("Debe tener entre 8 y 140 caracteres."), exception.details().get("question"));
		assertEquals(List.of("Debe tener entre 20 y 600 caracteres."), exception.details().get("answer"));
		assertEquals(List.of("El orden debe estar entre 1 y 999."), exception.details().get("order"));
	}

	@Test
	void reorderUpdatesAllItemsAndReturnsSortedRows() {
		UUID firstId = UUID.fromString("f3b10000-0000-4000-8000-000000000001");
		UUID secondId = UUID.fromString("f3b10000-0000-4000-8000-000000000002");
		Faq first = faq(firstId, 10);
		Faq second = faq(secondId, 20);
		when(repository.findById(firstId)).thenReturn(Optional.of(first));
		when(repository.findById(secondId)).thenReturn(Optional.of(second));
		when(repository.update(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<Faq> result = service.reorder(List.of(
			new FaqService.OrderChange(secondId, 1),
			new FaqService.OrderChange(firstId, 2)));

		assertEquals(List.of(secondId, firstId), result.stream().map(Faq::id).toList());
		verify(repository, times(2)).update(any(Faq.class));
	}

	@Test
	void reorderRejectsUnknownFaqBeforeUpdatingAnyRows() {
		UUID existingId = UUID.fromString("f3b10000-0000-4000-8000-000000000001");
		UUID missingId = UUID.fromString("f3b10000-0000-4000-8000-000000000099");
		when(repository.findById(existingId)).thenReturn(Optional.of(faq(existingId, 10)));
		when(repository.findById(missingId)).thenReturn(Optional.empty());

		assertThrows(InvalidInput.class, () -> service.reorder(List.of(
			new FaqService.OrderChange(existingId, 1),
			new FaqService.OrderChange(missingId, 2))));

		verify(repository, org.mockito.Mockito.never()).update(any(Faq.class));
	}

	private Faq faq(String question, String answer, int order) {
		Instant timestamp = Instant.parse("2026-10-27T12:00:00Z");
		return new Faq(UUID.fromString("f3b10000-0000-4000-8000-000000000001"),
			question, answer, order, true, timestamp, timestamp);
	}

	private Faq faq(UUID id, int order) {
		Instant timestamp = Instant.parse("2026-10-27T12:00:00Z");
		return new Faq(id, "Pregunta frecuente válida",
			"Una respuesta que cumple el mínimo requerido.", order, true, timestamp, timestamp);
	}

}
