package com.darkbit.bakerysales.products.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.darkbit.bakerysales.products.application.ProductExceptions.InvalidInput;
import com.darkbit.bakerysales.products.application.ProductExceptions.NotFound;
import com.darkbit.bakerysales.products.application.ProductRepository.ProductPage;
import com.darkbit.bakerysales.products.application.ProductService;
import com.darkbit.bakerysales.products.application.ProductService.ImageChange;
import com.darkbit.bakerysales.products.domain.Product;
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
	controllers = ProductController.class,
	properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
	})
@Import({ SecurityConfiguration.class, ProductExceptionHandler.class })
class ProductControllerTests {

	private static final String IMAGE_URL = "https://project.supabase.co/storage/v1/object/public"
		+ "/media/products/8f14e45f.webp";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService service;

	@Test
	void publicProductsDoNotRequireAuthentication() throws Exception {
		when(service.listPublic(isNull(), isNull())).thenReturn(List.of(product()));

		mockMvc.perform(get("/api/v1/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].name").value("Torta Tres Leches"))
			.andExpect(jsonPath("$.data[0].category.slug").value("tortas-clasicas"))
			.andExpect(jsonPath("$.data[0].category.id").doesNotExist())
			.andExpect(jsonPath("$.data[0].active").doesNotExist())
			.andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
	}

	@Test
	void publicProductsAcceptCategoryAndFeaturedFilters() throws Exception {
		when(service.listPublic("tortas-clasicas", true)).thenReturn(List.of(product()));

		mockMvc.perform(get("/api/v1/products")
				.param("category", "tortas-clasicas")
				.param("featured", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].featured").value(true));
	}

	@Test
	void adminListRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/admin/products"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void adminListReturnsPageMeta() throws Exception {
		when(service.listAdmin(anyString(), any(), any(), any(), anyInt(), anyInt(),
			anyString())).thenReturn(new ProductPage(List.of(product()), 1));

		mockMvc.perform(get("/api/v1/admin/products").with(jwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].categoryId").value(product().categoryId().toString()))
			.andExpect(jsonPath("$.data[0].category.id").value(product().categoryId().toString()))
			.andExpect(jsonPath("$.data[0].active").value(true))
			.andExpect(jsonPath("$.meta.total").value(1));
	}

	@Test
	void getReturnsNotFoundWhenTheProductDoesNotExist() throws Exception {
		when(service.get(product().id())).thenThrow(new NotFound());

		mockMvc.perform(get("/api/v1/admin/products/{id}", product().id()).with(jwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void getPublicReturnsTheVisibleProduct() throws Exception {
		when(service.getPublic(product().id())).thenReturn(product());

		mockMvc.perform(get("/api/v1/products/{id}", product().id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("Torta Tres Leches"))
			.andExpect(jsonPath("$.data.category.slug").value("tortas-clasicas"))
			.andExpect(jsonPath("$.data.category.id").doesNotExist())
			.andExpect(jsonPath("$.data.active").doesNotExist());
	}

	@Test
	void getPublicReturnsNotFoundWhenTheProductIsHidden() throws Exception {
		when(service.getPublic(product().id())).thenThrow(new NotFound());

		mockMvc.perform(get("/api/v1/products/{id}", product().id()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void createReturnsCreatedWithLocation() throws Exception {
		when(service.create(anyString(), any(), anyString(), anyString(), any(),
			anyString(), anyBoolean(), anyBoolean())).thenReturn(product());

		mockMvc.perform(post("/api/v1/admin/products")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCreateBody()))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location",
				"/api/v1/admin/products/" + product().id()))
			.andExpect(jsonPath("$.data.priceFrom").value(85.00));
	}

	@Test
	void createRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/api/v1/admin/products")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "To", "description": "corta" }
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.details.name").exists())
			.andExpect(jsonPath("$.error.details.description").exists())
			.andExpect(jsonPath("$.error.details.categoryId").exists())
			.andExpect(jsonPath("$.error.details.size").exists())
			.andExpect(jsonPath("$.error.details.priceFrom").exists())
			.andExpect(jsonPath("$.error.details.image").exists())
			.andExpect(jsonPath("$.error.details.active").exists());

		verify(service, never()).create(anyString(), any(), anyString(), anyString(),
			any(), anyString(), anyBoolean(), anyBoolean());
	}

	@Test
	void createRejectsPriceWithMoreThanTwoDecimals() throws Exception {
		mockMvc.perform(post("/api/v1/admin/products")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCreateBody().replace("85.00", "85.123")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.details.priceFrom").exists());
	}

	@Test
	void patchRejectsClearingTheImage() throws Exception {
		when(service.update(any(), any(), any(), any(), any(), any(),
			eq(ImageChange.clear()), any(), any()))
			.thenThrow(new InvalidInput(Map.of("image",
				List.of("La imagen no se puede quitar."))));

		mockMvc.perform(patch("/api/v1/admin/products/{id}", product().id())
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "image": null }
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.details.image").exists());
	}

	@Test
	void deleteReturnsNoContent() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/products/{id}", product().id()).with(jwt()))
			.andExpect(status().isNoContent());

		verify(service).delete(product().id());
	}

	private String validCreateBody() {
		return """
			{
			  "name": "Torta Selva Negra",
			  "categoryId": "%s",
			  "description": "Bizcocho de chocolate, crema chantilly y cerezas.",
			  "size": "15 porciones",
			  "priceFrom": 85.00,
			  "image": "%s",
			  "featured": false,
			  "active": true
			}
			""".formatted(product().categoryId(), IMAGE_URL);
	}

	private Product product() {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Product(
			UUID.fromString("3f6a2b1e-8c4d-4e9a-9b7f-1a2b3c4d5e6f"),
			"Torta Tres Leches",
			UUID.fromString("c1a70000-0000-4000-8000-000000000001"),
			"Tortas clásicas", "tortas-clasicas",
			"Bizcocho húmedo bañado en tres leches.", "20 porciones",
			new BigDecimal("85.00"), IMAGE_URL, "products/8f14e45f.webp",
			true, true, now, now);
	}
}
