package com.darkbit.bakerysales.categories.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.darkbit.bakerysales.categories.application.CategoryExceptions.Conflict;
import com.darkbit.bakerysales.categories.application.CategoryExceptions.NotFound;
import com.darkbit.bakerysales.categories.application.CategoryRepository.CategoryPage;
import com.darkbit.bakerysales.categories.application.CategoryService;
import com.darkbit.bakerysales.categories.domain.Category;
import com.darkbit.bakerysales.security.SecurityConfiguration;

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
	controllers = CategoryController.class,
	properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
	})
@Import({ SecurityConfiguration.class, CategoryExceptionHandler.class })
class CategoryControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CategoryService service;

	@Test
	void publicCategoriesDoNotRequireAuthentication() throws Exception {
		when(service.listActive()).thenReturn(List.of(category()));

		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(category().id().toString()))
			.andExpect(jsonPath("$.data[0].slug").value("tortas-clasicas"))
			.andExpect(jsonPath("$.data[0].productCount").value(6))
			.andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
	}

	@Test
	void adminListRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/admin/categories"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void adminListReturnsPageMeta() throws Exception {
		when(service.listAdmin(eq("all"), isNull(), eq(1), eq(20), eq("name")))
			.thenReturn(new CategoryPage(List.of(category()), 1));

		mockMvc.perform(get("/api/v1/admin/categories").with(jwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].slug").value("tortas-clasicas"))
			.andExpect(jsonPath("$.data[0].createdAt").exists())
			.andExpect(jsonPath("$.meta.total").value(1))
			.andExpect(jsonPath("$.meta.totalPages").value(1));
	}

	@Test
	void getReturnsNotFoundWhenMissing() throws Exception {
		when(service.get(any(UUID.class))).thenThrow(new NotFound());

		mockMvc.perform(get("/api/v1/admin/categories/{id}", UUID.randomUUID()).with(jwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void createReturnsCreatedWithLocation() throws Exception {
		when(service.create("Tortas Clásicas", null, true)).thenReturn(category());

		mockMvc.perform(post("/api/v1/admin/categories")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "Tortas Clásicas", "active": true }
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location",
				"/api/v1/admin/categories/" + category().id()))
			.andExpect(jsonPath("$.data.slug").value("tortas-clasicas"));
	}

	@Test
	void createRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/api/v1/admin/categories")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "To" }
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.details.name").exists())
			.andExpect(jsonPath("$.error.details.active").exists());

		verify(service, never()).create(anyString(), any(), anyBoolean());
	}

	@Test
	void createRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/admin/categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "Tortas Clásicas", "active": true }
					"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void patchIgnoresSlugAndKeepsIt() throws Exception {
		when(service.update(any(UUID.class), eq("Tortas Tradicionales"), isNull(), isNull()))
			.thenReturn(category());

		mockMvc.perform(patch("/api/v1/admin/categories/{id}", category().id())
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "Tortas Tradicionales" }
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.slug").value("tortas-clasicas"));
	}

	@Test
	void deleteReturnsConflictWhenCategoryHasProducts() throws Exception {
		org.mockito.Mockito.doThrow(new Conflict("La categoría tiene productos asociados.",
			Map.of("products", List.of("Mueve o elimina sus 6 productos primero."))))
			.when(service).delete(category().id());

		mockMvc.perform(delete("/api/v1/admin/categories/{id}", category().id()).with(jwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFLICT"))
			.andExpect(jsonPath("$.error.details.products[0]")
				.value("Mueve o elimina sus 6 productos primero."));
	}

	@Test
	void deleteReturnsNoContent() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/categories/{id}", category().id()).with(jwt()))
			.andExpect(status().isNoContent());

		verify(service).delete(category().id());
	}

	private Category category() {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Category(
			UUID.fromString("c1a70000-0000-4000-8000-000000000001"),
			"Tortas Clásicas", "tortas-clasicas",
			"Nuestros sabores de siempre.", true, 6, now, now);
	}
}
