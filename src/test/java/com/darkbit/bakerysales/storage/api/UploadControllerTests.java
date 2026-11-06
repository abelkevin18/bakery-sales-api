package com.darkbit.bakerysales.storage.api;

import com.darkbit.bakerysales.security.SecurityConfiguration;
import com.darkbit.bakerysales.storage.application.StorageExceptions.Conflict;
import com.darkbit.bakerysales.storage.application.StorageExceptions.PayloadTooLarge;
import com.darkbit.bakerysales.storage.application.StorageExceptions.UnsupportedMediaType;
import com.darkbit.bakerysales.storage.application.StorageService;
import com.darkbit.bakerysales.storage.application.StorageService.SignedImage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
	controllers = UploadController.class,
	properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
	})
@Import({ SecurityConfiguration.class, UploadExceptionHandler.class })
class UploadControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StorageService service;

	@Test
	void signUploadRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/admin/uploads/sign")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "folder": "products", "contentType": "image/webp", "size": 1024 }
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		verify(service, never()).signUpload(anyString(), anyString(), anyLong());
	}

	@Test
	void signUploadReturnsSignedUrls() throws Exception {
		when(service.signUpload("products", "image/webp", 1024L)).thenReturn(new SignedImage(
			"products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp",
			"https://project.supabase.co/storage/v1/object/upload/sign/media/products/x.webp?token=abc",
			"https://project.supabase.co/storage/v1/object/public/media/products/x.webp",
			300));

		mockMvc.perform(post("/api/v1/admin/uploads/sign")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "folder": "products", "contentType": "image/webp", "size": 1024 }
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.path").value("products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp"))
			.andExpect(jsonPath("$.data.uploadUrl").exists())
			.andExpect(jsonPath("$.data.publicUrl").exists())
			.andExpect(jsonPath("$.data.expiresIn").value(300));
	}

	@Test
	void signUploadValidatesRequiredFields() throws Exception {
		mockMvc.perform(post("/api/v1/admin/uploads/sign")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.details.folder").exists())
			.andExpect(jsonPath("$.error.details.contentType").exists());
	}

	@Test
	void signUploadMapsUnsupportedMediaType() throws Exception {
		when(service.signUpload("products", "image/gif", 1024L))
			.thenThrow(new UnsupportedMediaType("El archivo debe ser JPEG, PNG o WebP."));

		mockMvc.perform(post("/api/v1/admin/uploads/sign")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "folder": "products", "contentType": "image/gif", "size": 1024 }
					"""))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void signUploadMapsPayloadTooLarge() throws Exception {
		when(service.signUpload("products", "image/png", 9_000_000L))
			.thenThrow(new PayloadTooLarge("La imagen no puede superar los 5 MB."));

		mockMvc.perform(post("/api/v1/admin/uploads/sign")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "folder": "products", "contentType": "image/png", "size": 9000000 }
					"""))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
	}

	@Test
	void deleteUploadReturnsNoContent() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/uploads")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "path": "products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp" }
					"""))
			.andExpect(status().isNoContent());

		verify(service).delete("products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp");
	}

	@Test
	void deleteUploadReturnsConflictWhenImageIsInUse() throws Exception {
		doThrow(new Conflict("La imagen está siendo usada por un registro existente."))
			.when(service).delete("products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp");

		mockMvc.perform(delete("/api/v1/admin/uploads")
				.with(jwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "path": "products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp" }
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFLICT"));
	}

	@Test
	void deleteUploadRejectsMissingPath() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/uploads").with(jwt()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		verify(service, never()).delete(any());
	}
}
