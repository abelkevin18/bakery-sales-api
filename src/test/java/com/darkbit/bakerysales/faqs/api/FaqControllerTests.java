package com.darkbit.bakerysales.faqs.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.darkbit.bakerysales.faqs.application.FaqService;
import com.darkbit.bakerysales.faqs.domain.Faq;
import com.darkbit.bakerysales.security.SecurityConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
	controllers = FaqController.class,
	properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
	})
@Import({ SecurityConfiguration.class, FaqExceptionHandler.class })
class FaqControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FaqService service;

	@Test
	void publicFaqsReturnOnlyPublishedListWithoutAuthentication() throws Exception {
		Faq faq = faq();
		when(service.listPublished()).thenReturn(List.of(faq));

		mockMvc.perform(get("/api/v1/faqs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(faq.id().toString()))
			.andExpect(jsonPath("$.data[0].order").value(1))
			.andExpect(jsonPath("$.data[0].createdAt").exists());
	}

	@Test
	void adminFaqRoutesRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/admin/faqs"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void createFaqValidatesInputAndReturnsCreatedLocation() throws Exception {
		Faq faq = faq();
		when(service.create("¿Con cuánta anticipación debo pedir?", "Recomendamos hacer el pedido con varios días de anticipación.", 1, true))
			.thenReturn(faq);

		mockMvc.perform(post("/api/v1/admin/faqs")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question": "¿Con cuánta anticipación debo pedir?",
					  "answer": "Recomendamos hacer el pedido con varios días de anticipación.",
					  "order": 1,
					  "published": true
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/v1/admin/faqs/" + faq.id()))
			.andExpect(jsonPath("$.data.published").value(true));
	}

	@Test
	void createFaqReturnsFieldValidationDetails() throws Exception {
		mockMvc.perform(post("/api/v1/admin/faqs")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question": "FAQ",
					  "answer": "short",
					  "order": 0,
					  "published": true
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.details.question").exists())
			.andExpect(jsonPath("$.error.details.answer").exists())
			.andExpect(jsonPath("$.error.details.order").exists());

		verify(service, org.mockito.Mockito.never())
			.create(anyString(), anyString(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
	}

	private Faq faq() {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Faq(UUID.fromString("f3b10000-0000-4000-8000-000000000001"),
			"¿Con cuánta anticipación debo pedir?",
			"Recomendamos hacer el pedido con varios días de anticipación.",
			1, true, now, now);
	}
}
