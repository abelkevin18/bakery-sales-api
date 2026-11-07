package com.darkbit.bakerysales.categories.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.categories.application.CategoryExceptions.BadRequest;
import com.darkbit.bakerysales.categories.application.CategoryExceptions.Conflict;
import com.darkbit.bakerysales.categories.application.CategoryExceptions.InvalidInput;
import com.darkbit.bakerysales.categories.domain.Category;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

	private static final UUID ID = UUID.fromString("c1a70000-0000-4000-8000-000000000001");
	private static final String SLUG = "tortas-clasicas";

	@Mock
	private CategoryRepository repository;

	private CategoryService service;

	@BeforeEach
	void setUp() {
		service = new CategoryService(repository, new SlugGenerator());
	}

	@Test
	void createGeneratesSlugFromName() {
		when(repository.slugExists(SLUG, null)).thenReturn(false);
		when(repository.create("Tortas Clásicas", SLUG,
			"Nuestros sabores de siempre.", true)).thenReturn(category());

		service.create("  Tortas Clásicas ", " Nuestros sabores de siempre. ", true);

		verify(repository).create("Tortas Clásicas", SLUG,
			"Nuestros sabores de siempre.", true);
	}

	@Test
	void createAppendsSuffixWhenSlugIsTaken() {
		when(repository.slugExists(SLUG, null)).thenReturn(true);
		when(repository.slugExists(SLUG + "-2", null)).thenReturn(false);
		when(repository.create(anyString(), anyString(), anyString(), anyBoolean()))
			.thenReturn(category());

		service.create("Tortas Clásicas", null, true);

		ArgumentCaptor<String> slug = ArgumentCaptor.forClass(String.class);
		verify(repository).create(anyString(), slug.capture(), anyString(), anyBoolean());
		assertEquals("tortas-clasicas-2", slug.getValue());
	}

	@Test
	void createDefaultsDescriptionToEmptyString() {
		when(repository.slugExists(SLUG, null)).thenReturn(false);
		when(repository.create("Tortas Clásicas", SLUG, "", true)).thenReturn(category());

		service.create("Tortas Clásicas", null, true);

		verify(repository).create("Tortas Clásicas", SLUG, "", true);
	}

	@Test
	void createRejectsInvalidName() {
		InvalidInput exception = assertThrows(InvalidInput.class,
			() -> service.create("To", null, true));

		assertEquals(List.of("Debe tener entre 3 y 50 caracteres."),
			exception.details().get("name"));
		verify(repository, never()).create(anyString(), anyString(), anyString(), anyBoolean());
	}

	@Test
	void updateKeepsSlugWhenNameChanges() {
		when(repository.findById(ID)).thenReturn(Optional.of(category()));
		when(repository.update(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(ID, "Tortas Tradicionales", null, null);

		ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
		verify(repository).update(captor.capture());
		assertEquals("Tortas Tradicionales", captor.getValue().name());
		assertEquals(SLUG, captor.getValue().slug());
	}

	@Test
	void updateRejectsEmptyBody() {
		when(repository.findById(ID)).thenReturn(Optional.of(category()));

		InvalidInput exception = assertThrows(InvalidInput.class,
			() -> service.update(ID, null, null, null));

		assertEquals(List.of("Debe enviar al menos un campo para actualizar."),
			exception.details().get("body"));
		verify(repository, never()).update(any(Category.class));
	}

	@Test
	void deleteRejectsCategoryWithProducts() {
		when(repository.findById(ID)).thenReturn(Optional.of(category()));
		when(repository.countProducts(ID)).thenReturn(6L);

		Conflict exception = assertThrows(Conflict.class, () -> service.delete(ID));

		assertEquals(List.of("Mueve o elimina sus 6 productos primero."),
			exception.details().get("products"));
		verify(repository, never()).deleteById(ID);
	}

	@Test
	void deleteRemovesCategoryWithoutProducts() {
		when(repository.findById(ID)).thenReturn(Optional.of(category()));
		when(repository.countProducts(ID)).thenReturn(0L);

		service.delete(ID);

		verify(repository).deleteById(ID);
	}

	@Test
	void listAdminRejectsUnsupportedStatus() {
		assertThrows(BadRequest.class,
			() -> service.listAdmin("archived", null, 1, 20, "name"));
	}

	@Test
	void listAdminRejectsUnsupportedSort() {
		assertThrows(BadRequest.class,
			() -> service.listAdmin("all", null, 1, 20, "slug; drop table categories"));
	}

	private Category category() {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Category(ID, "Tortas Clásicas", SLUG,
			"Nuestros sabores de siempre.", true, 6, now, now);
	}
}
