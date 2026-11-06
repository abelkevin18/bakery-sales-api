package com.darkbit.bakerysales.testimonials.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.darkbit.bakerysales.security.SecurityConfiguration;
import com.darkbit.bakerysales.testimonials.application.TestimonialService;
import com.darkbit.bakerysales.testimonials.application.TestimonialService.ImageChange;
import com.darkbit.bakerysales.testimonials.domain.Testimonial;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
	controllers = TestimonialController.class,
	properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
	})
@Import({ SecurityConfiguration.class, TestimonialExceptionHandler.class })
class TestimonialControllerTests {

	private static final String PUBLIC_IMAGE =
		"https://project.supabase.co/storage/v1/object/public/media/testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TestimonialService service;

	@Test
	void publicTestimonialsDoNotRequireAuthentication() throws Exception {
		when(service.listPublished(6)).thenReturn(List.of(testimonial()));

		mockMvc.perform(get("/api/v1/testimonials"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(testimonial().id().toString()))
			.andExpect(jsonPath("$.data[0].image").value(PUBLIC_IMAGE))
			.andExpect(jsonPath("$.data[0].rating").value(5));
	}

	@Test
	void adminTestimonialRoutesRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/admin/testimonials"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void createTestimonialReturnsCreatedLocation() throws Exception {
		when(service.create("Lucía Pérez", "Cumpleaños de mi hija", 5,
			"La torta llegó perfecta y el sabor encantó a todos.", PUBLIC_IMAGE, true))
			.thenReturn(testimonial());

		mockMvc.perform(post("/api/v1/admin/testimonials")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Lucía Pérez",
					  "occasion": "Cumpleaños de mi hija",
					  "rating": 5,
					  "text": "La torta llegó perfecta y el sabor encantó a todos.",
					  "image": "%s",
					  "published": true
					}
					""".formatted(PUBLIC_IMAGE)))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location",
				"/api/v1/admin/testimonials/" + testimonial().id()))
			.andExpect(jsonPath("$.data.published").value(true));
	}

	@Test
	void createTestimonialReturnsFieldValidationDetails() throws Exception {
		mockMvc.perform(post("/api/v1/admin/testimonials")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Lu",
					  "occasion": "Cu",
					  "rating": 9,
					  "text": "corto",
					  "published": true
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.details.name").exists())
			.andExpect(jsonPath("$.error.details.occasion").exists())
			.andExpect(jsonPath("$.error.details.rating").exists())
			.andExpect(jsonPath("$.error.details.text").exists());

		verify(service, never()).create(anyString(), anyString(), anyInt(), anyString(), any(), anyBoolean());
	}

	@Test
	void patchWithNullImageClearsIt() throws Exception {
		UUID id = testimonial().id();
		when(service.update(eq(id), isNull(), isNull(), isNull(), isNull(),
			eq(ImageChange.clear()), isNull()))
			.thenReturn(testimonial());

		mockMvc.perform(patch("/api/v1/admin/testimonials/{id}", id)
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "image": null }
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(id.toString()));
	}

	@Test
	void patchWithoutImageLeavesItUntouched() throws Exception {
		UUID id = testimonial().id();
		when(service.update(eq(id), eq("Lucía Pérez"), isNull(), isNull(), isNull(),
			eq(ImageChange.absent()), isNull()))
			.thenReturn(testimonial());

		mockMvc.perform(patch("/api/v1/admin/testimonials/{id}", id)
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "Lucía Pérez" }
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void patchRejectsNonTextImage() throws Exception {
		UUID id = testimonial().id();

		mockMvc.perform(patch("/api/v1/admin/testimonials/{id}", id)
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "image": 123 }
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.details.image").exists());

		verify(service, never()).update(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void deleteTestimonialReturnsNoContent() throws Exception {
		UUID id = testimonial().id();

		mockMvc.perform(delete("/api/v1/admin/testimonials/{id}", id).with(jwt()))
			.andExpect(status().isNoContent());

		verify(service).delete(id);
	}

	private Testimonial testimonial() {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Testimonial(
			UUID.fromString("a9e20000-0000-4000-8000-000000000001"),
			"Lucía Pérez",
			"Cumpleaños de mi hija",
			5,
			"La torta llegó perfecta y el sabor encantó a todos.",
			PUBLIC_IMAGE,
			"testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg",
			true, now, now);
	}
}
