package com.darkbit.bakerysales.testimonials.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.storage.application.ImageCleanupService;
import com.darkbit.bakerysales.storage.application.PublicImageUrlResolver;
import com.darkbit.bakerysales.storage.infrastructure.SupabaseProperties;
import com.darkbit.bakerysales.testimonials.application.TestimonialExceptions.BadRequest;
import com.darkbit.bakerysales.testimonials.application.TestimonialExceptions.InvalidInput;
import com.darkbit.bakerysales.testimonials.application.TestimonialService.ImageChange;
import com.darkbit.bakerysales.testimonials.domain.Testimonial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestimonialServiceTests {

	private static final String PUBLIC_IMAGE =
		"https://project.supabase.co/storage/v1/object/public/media/testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg";
	private static final String REPLACEMENT_IMAGE =
		"https://project.supabase.co/storage/v1/object/public/media/testimonials/0f8b1c22-1111-4a2b-9c3d-123456789abc.webp";
	private static final String PREVIOUS_IMAGE_PATH =
		"testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg";

	@Mock
	private TestimonialRepository repository;

	@Mock
	private ImageCleanupService imageCleanup;

	private TestimonialService service;

	@BeforeEach
	void setUp() {
		service = new TestimonialService(repository, resolver(), imageCleanup);
	}

	@Test
	void createDerivesImagePathFromPublicUrl() {
		when(repository.create("Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.", PUBLIC_IMAGE,
			"testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg", true))
			.thenReturn(testimonial());

		service.create("  Lucía Pérez ", " Cumpleaños de mi hija ",
			5, " La torta llegó perfecta y el sabor encantó a todos. ", PUBLIC_IMAGE, true);

		verify(repository).create("Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.", PUBLIC_IMAGE,
			PREVIOUS_IMAGE_PATH, true);
	}

	@Test
	void createAcceptsMissingImage() {
		when(repository.create("Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.", null, null, false))
			.thenReturn(testimonial());

		service.create("Lucía Pérez", "Cumpleaños de mi hija",
			5, "La torta llegó perfecta y el sabor encantó a todos.", null, false);

		verify(repository).create("Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.", null, null, false);
	}

	@Test
	void createRejectsExternalImageUrl() {
		InvalidInput exception = assertThrows(InvalidInput.class, () -> service.create(
			"Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.",
			"https://otro-sitio.com/foto.jpg", true));

		assertEquals(List.of(
			"La imagen debe ser la URL pública devuelta por /admin/uploads/sign o null."),
			exception.details().get("image"));
		verify(repository, never()).create(
			anyString(), anyString(), anyInt(), anyString(), any(), any(), anyBoolean());
	}

	@Test
	void createRejectsInvalidFields() {
		InvalidInput exception = assertThrows(InvalidInput.class,
			() -> service.create("Lu", "Cumpleaños", 9, "muy corto", null, true));

		assertEquals(List.of("Debe tener entre 3 y 60 caracteres."), exception.details().get("name"));
		assertEquals(List.of("La calificación debe ser un entero entre 1 y 5."), exception.details().get("rating"));
		assertEquals(List.of("Debe tener entre 20 y 400 caracteres."), exception.details().get("text"));
	}

	@Test
	void updateKeepsImageWhenFieldIsAbsent() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));
		when(repository.update(any(Testimonial.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(id, "Lucía Pérez", null, null, null, ImageChange.absent(), null);

		ArgumentCaptor<Testimonial> captor = ArgumentCaptor.forClass(Testimonial.class);
		verify(repository).update(captor.capture());
		assertEquals(PUBLIC_IMAGE, captor.getValue().imageUrl());
		assertEquals("testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg",
			captor.getValue().imagePath());
	}

	@Test
	void updateClearsImageWhenRequested() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));
		when(repository.update(any(Testimonial.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(id, "Lucía Pérez", null, null, null, ImageChange.clear(), null);

		ArgumentCaptor<Testimonial> captor = ArgumentCaptor.forClass(Testimonial.class);
		verify(repository).update(captor.capture());
		assertNull(captor.getValue().imageUrl());
		assertNull(captor.getValue().imagePath());
	}

	@Test
	void updateRejectsEmptyBody() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));

		InvalidInput exception = assertThrows(InvalidInput.class,
			() -> service.update(id, null, null, null, null, ImageChange.absent(), null));

		assertEquals(List.of("Debe enviar al menos un campo para actualizar."),
			exception.details().get("body"));
		verify(repository, never()).update(any(Testimonial.class));
	}

	@Test
	void updateDeletesPreviousImageWhenReplaced() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));
		when(repository.update(any(Testimonial.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(id, null, null, null, null, ImageChange.set(REPLACEMENT_IMAGE), null);

		verify(imageCleanup).deleteAfterCommit(PREVIOUS_IMAGE_PATH);
	}

	@Test
	void updateDeletesPreviousImageWhenCleared() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));
		when(repository.update(any(Testimonial.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(id, null, null, null, null, ImageChange.clear(), null);

		verify(imageCleanup).deleteAfterCommit(PREVIOUS_IMAGE_PATH);
	}

	@Test
	void updateKeepsImageObjectWhenPathIsUnchanged() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));
		when(repository.update(any(Testimonial.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(id, "Rosario Quispe", null, null, null, ImageChange.absent(), null);

		verify(imageCleanup, never()).deleteAfterCommit(anyString());
	}

	@Test
	void deleteRemovesImageObject() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000001");
		when(repository.findById(id)).thenReturn(Optional.of(testimonial(id)));

		service.delete(id);

		verify(repository).deleteById(id);
		verify(imageCleanup).deleteAfterCommit(PREVIOUS_IMAGE_PATH);
	}

	@Test
	void deleteWithoutImageDoesNotTouchStorage() {
		UUID id = UUID.fromString("a9e20000-0000-4000-8000-000000000002");
		when(repository.findById(id)).thenReturn(Optional.of(testimonialWithoutImage(id)));

		service.delete(id);

		verify(repository).deleteById(id);
		verify(imageCleanup, never()).deleteAfterCommit(anyString());
	}

	@Test
	void listAdminRejectsUnsupportedSort() {
		assertThrows(BadRequest.class,
			() -> service.listAdmin("all", null, null, 1, 20, "text;drop table testimonials"));
	}

	@Test
	void listPublishedRejectsOutOfRangeLimit() {
		assertThrows(BadRequest.class, () -> service.listPublished(21));
	}

	private PublicImageUrlResolver resolver() {
		return new PublicImageUrlResolver(new SupabaseProperties(
			"https://project.supabase.co",
			new SupabaseProperties.Storage("media", "service-role-key",
				Duration.ofSeconds(300), DataSize.ofMegabytes(5))));
	}

	private Testimonial testimonial() {
		return testimonial(UUID.fromString("a9e20000-0000-4000-8000-000000000001"));
	}

	private Testimonial testimonial(UUID id) {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Testimonial(id, "Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.",
			PUBLIC_IMAGE, PREVIOUS_IMAGE_PATH,
			true, now, now);
	}

	private Testimonial testimonialWithoutImage(UUID id) {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Testimonial(id, "Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.",
			null, null, true, now, now);
	}
}
