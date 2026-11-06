package com.darkbit.bakerysales.storage.application;

import java.time.Duration;

import com.darkbit.bakerysales.storage.application.StorageExceptions.Conflict;
import com.darkbit.bakerysales.storage.application.StorageExceptions.InvalidInput;
import com.darkbit.bakerysales.storage.application.StorageExceptions.PayloadTooLarge;
import com.darkbit.bakerysales.storage.application.StorageExceptions.UnsupportedMediaType;
import com.darkbit.bakerysales.storage.application.StorageService.SignedImage;
import com.darkbit.bakerysales.storage.infrastructure.JdbcImageUsageRepository;
import com.darkbit.bakerysales.storage.infrastructure.SupabaseProperties;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageServiceTests {

	private final StorageGateway gateway = mock(StorageGateway.class);
	private final JdbcImageUsageRepository usageRepository = mock(JdbcImageUsageRepository.class);
	private final SupabaseProperties properties = new SupabaseProperties(
		"https://project.supabase.co/",
		new SupabaseProperties.Storage("media", "service-role-key",
			Duration.ofSeconds(300), DataSize.ofMegabytes(5)));
	private final StorageService service = new StorageService(gateway, usageRepository, properties);

	@Test
	void signUploadBuildsPathAndPublicUrl() {
		when(gateway.createSignedUpload(anyString(), any()))
			.thenReturn(new StorageGateway.SignedUpload(
				"https://project.supabase.co/storage/v1/object/upload/sign/media/products/x.webp?token=abc",
				"abc"));

		SignedImage signed = service.signUpload("products", "image/webp", 1024L);

		assertThat(signed.path()).startsWith("products/").endsWith(".webp");
		assertThat(signed.publicUrl())
			.isEqualTo("https://project.supabase.co/storage/v1/object/public/media/" + signed.path());
		assertThat(signed.uploadUrl()).contains("/object/upload/sign/media/");
		assertThat(signed.expiresIn()).isEqualTo(300);
	}

	@Test
	void signUploadAcceptsContentTypeWithCharset() {
		when(gateway.createSignedUpload(anyString(), any()))
			.thenReturn(new StorageGateway.SignedUpload("https://example.com/signed", "abc"));

		SignedImage signed = service.signUpload("testimonials", "image/png; charset=binary", 10L);

		assertThat(signed.path()).startsWith("testimonials/").endsWith(".png");
	}

	@Test
	void signUploadRejectsUnknownFolder() {
		assertThatThrownBy(() -> service.signUpload("avatars", "image/png", 10L))
			.isInstanceOf(InvalidInput.class);

		verify(gateway, never()).createSignedUpload(anyString(), any());
	}

	@Test
	void signUploadRejectsUnsupportedContentType() {
		assertThatThrownBy(() -> service.signUpload("products", "image/gif", 10L))
			.isInstanceOf(UnsupportedMediaType.class);
	}

	@Test
	void signUploadRejectsOversizedFile() {
		assertThatThrownBy(() -> service.signUpload("products", "image/jpeg", 6L * 1024 * 1024))
			.isInstanceOf(PayloadTooLarge.class);
	}

	@Test
	void deleteRejectsInvalidPath() {
		assertThatThrownBy(() -> service.delete("../secret.txt"))
			.isInstanceOf(InvalidInput.class);

		verify(gateway, never()).delete(anyString());
	}

	@Test
	void deleteRejectsImageInUse() {
		when(usageRepository.isReferenced("products/abc.webp")).thenReturn(true);

		assertThatThrownBy(() -> service.delete("products/abc.webp"))
			.isInstanceOf(Conflict.class);

		verify(gateway, never()).delete(anyString());
	}

	@Test
	void deleteRemovesUnusedImage() {
		service.delete("products/abc.webp");

		verify(gateway).delete("products/abc.webp");
	}
}
