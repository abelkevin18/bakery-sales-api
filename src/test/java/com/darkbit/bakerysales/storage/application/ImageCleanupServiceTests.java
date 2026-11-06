package com.darkbit.bakerysales.storage.application;

import com.darkbit.bakerysales.storage.application.StorageExceptions.StorageUnavailable;
import com.darkbit.bakerysales.storage.infrastructure.JdbcImageUsageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageCleanupServiceTests {

	private static final String PATH = "testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg";

	@Mock
	private StorageGateway gateway;

	@Mock
	private JdbcImageUsageRepository usageRepository;

	private ImageCleanupService service;

	@BeforeEach
	void setUp() {
		service = new ImageCleanupService(gateway, usageRepository);
	}

	@Test
	void deletesObjectWhenNothingReferencesIt() {
		when(usageRepository.isReferenced(PATH)).thenReturn(false);

		assertTrue(service.deleteIfUnused(PATH));

		verify(gateway).delete(PATH);
	}

	@Test
	void keepsObjectWhenStillReferenced() {
		when(usageRepository.isReferenced(PATH)).thenReturn(true);

		assertFalse(service.deleteIfUnused(PATH));

		verify(gateway, never()).delete(anyString());
	}

	@Test
	void ignoresBlankPath() {
		assertFalse(service.deleteIfUnused(null));
		assertFalse(service.deleteIfUnused("  "));

		verifyNoInteractions(usageRepository, gateway);
	}

	@Test
	void swallowsStorageFailures() {
		when(usageRepository.isReferenced(PATH)).thenReturn(false);
		doThrow(new StorageUnavailable("Supabase no respondió.")).when(gateway).delete(PATH);

		assertFalse(service.deleteIfUnused(PATH));
	}

	@Test
	void runsImmediatelyWithoutTransaction() {
		when(usageRepository.isReferenced(PATH)).thenReturn(false);

		service.deleteAfterCommit(PATH);

		verify(gateway).delete(PATH);
	}
}
