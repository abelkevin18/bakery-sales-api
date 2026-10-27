package com.darkbit.bakerysales.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
	controllers = ApiMeController.class,
	properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
	})
@Import(SecurityConfiguration.class)
class SecurityConfigurationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiMeRequiresAuthenticationAndReturnsGenericJsonError() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void apiMeReturnsClaimsFromAuthenticatedJwt() throws Exception {
		mockMvc.perform(get("/api/me").with(jwt()
				.jwt(token -> token.subject("user-123").claim("email", "admin@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("user-123"))
			.andExpect(jsonPath("$.email").value("admin@example.com"));
	}

	@Test
	void publicCatalogGetDoesNotRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/products"))
			.andExpect(status().isNotFound());
	}
}
