package com.darkbit.bakerysales.products.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.products.application.ProductExceptions.BadRequest;
import com.darkbit.bakerysales.products.application.ProductExceptions.InvalidInput;
import com.darkbit.bakerysales.products.application.ProductExceptions.NotFound;
import com.darkbit.bakerysales.products.application.ProductService.ImageChange;
import com.darkbit.bakerysales.products.domain.Product;
import com.darkbit.bakerysales.storage.application.ImageCleanupService;
import com.darkbit.bakerysales.storage.application.PublicImageUrlResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

	private static final UUID ID = UUID.fromString("3f6a2b1e-8c4d-4e9a-9b7f-1a2b3c4d5e6f");
	private static final UUID CATEGORY_ID = UUID.fromString("c1a70000-0000-4000-8000-000000000001");
	private static final String IMAGE_URL = "https://project.supabase.co/storage/v1/object/public"
		+ "/media/products/8f14e45f.webp";
	private static final String IMAGE_PATH = "products/8f14e45f.webp";
	private static final String NEW_IMAGE_URL = "https://project.supabase.co/storage/v1/object/public"
		+ "/media/products/2b9c7d10.webp";
	private static final String NEW_IMAGE_PATH = "products/2b9c7d10.webp";

	@Mock
	private ProductRepository repository;

	@Mock
	private PublicImageUrlResolver imageResolver;

	@Mock
	private ImageCleanupService imageCleanup;

	private ProductService service;

	@BeforeEach
	void setUp() {
		service = new ProductService(repository, imageResolver, imageCleanup);
	}

	@Test
	void createDerivesImagePathFromPublicUrl() {
		when(repository.categoryExists(CATEGORY_ID)).thenReturn(true);
		when(imageResolver.resolvePath(IMAGE_URL)).thenReturn(Optional.of(IMAGE_PATH));
		when(repository.create(anyString(), any(), anyString(), anyString(), any(),
			anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(product());

		service.create("Torta Tres Leches", CATEGORY_ID,
			"Bizcocho húmedo bañado en tres leches.", "20 porciones",
			new BigDecimal("85.00"), IMAGE_URL, true, true);

		verify(repository).create("Torta Tres Leches", CATEGORY_ID,
			"Bizcocho húmedo bañado en tres leches.", "20 porciones",
			new BigDecimal("85.00"), IMAGE_URL, IMAGE_PATH, true, true);
	}

	@Test
	void createRejectsUnknownCategory() {
		when(repository.categoryExists(CATEGORY_ID)).thenReturn(false);
		when(imageResolver.resolvePath(IMAGE_URL)).thenReturn(Optional.of(IMAGE_PATH));

		InvalidInput error = assertThrows(InvalidInput.class, () -> service.create(
			"Torta Tres Leches", CATEGORY_ID, "Bizcocho húmedo bañado en tres leches.",
			"20 porciones", new BigDecimal("85.00"), IMAGE_URL, false, true));

		assertTrue(error.details().containsKey("categoryId"));
		verify(repository, never()).create(anyString(), any(), anyString(), anyString(),
			any(), anyString(), anyString(), anyBoolean(), anyBoolean());
	}

	@Test
	void createRejectsImageOutsideTheBucket() {
		when(repository.categoryExists(CATEGORY_ID)).thenReturn(true);
		when(imageResolver.resolvePath(IMAGE_URL)).thenReturn(Optional.empty());

		InvalidInput error = assertThrows(InvalidInput.class, () -> service.create(
			"Torta Tres Leches", CATEGORY_ID, "Bizcocho húmedo bañado en tres leches.",
			"20 porciones", new BigDecimal("85.00"), IMAGE_URL, false, true));

		assertTrue(error.details().containsKey("image"));
	}

	@Test
	void createRejectsPriceOutOfRange() {
		when(repository.categoryExists(CATEGORY_ID)).thenReturn(true);
		when(imageResolver.resolvePath(IMAGE_URL)).thenReturn(Optional.of(IMAGE_PATH));

		InvalidInput zero = assertThrows(InvalidInput.class, () -> service.create(
			"Torta Tres Leches", CATEGORY_ID, "Bizcocho húmedo bañado en tres leches.",
			"20 porciones", BigDecimal.ZERO, IMAGE_URL, false, true));
		assertTrue(zero.details().containsKey("priceFrom"));

		InvalidInput tooExpensive = assertThrows(InvalidInput.class, () -> service.create(
			"Torta Tres Leches", CATEGORY_ID, "Bizcocho húmedo bañado en tres leches.",
			"20 porciones", new BigDecimal("10000.01"), IMAGE_URL, false, true));
		assertTrue(tooExpensive.details().containsKey("priceFrom"));
	}

	@Test
	void createRejectsMoreThanTwoDecimals() {
		when(repository.categoryExists(CATEGORY_ID)).thenReturn(true);
		when(imageResolver.resolvePath(IMAGE_URL)).thenReturn(Optional.of(IMAGE_PATH));

		InvalidInput error = assertThrows(InvalidInput.class, () -> service.create(
			"Torta Tres Leches", CATEGORY_ID, "Bizcocho húmedo bañado en tres leches.",
			"20 porciones", new BigDecimal("85.123"), IMAGE_URL, false, true));

		assertTrue(error.details().containsKey("priceFrom"));
	}

	@Test
	void listPublicNormalizesBlankCategorySlug() {
		when(repository.findPublic(null, true)).thenReturn(List.of(product()));

		service.listPublic("   ", true);

		verify(repository).findPublic(null, true);
	}

	@Test
	void listAdminDefaultsSortToUpdatedAt() {
		when(repository.findAdmin(any(), any(), anyString(), any(), anyInt(), anyInt(),
			anyString())).thenReturn(new ProductRepository.ProductPage(List.of(product()), 1));

		service.listAdmin("all", null, null, null, 1, 20, null);

		verify(repository).findAdmin(null, null, "all", null, 1, 20, "-updatedAt");
	}

	@Test
	void listAdminRejectsInvalidStatus() {
		assertThrows(BadRequest.class,
			() -> service.listAdmin("published", null, null, null, 1, 20, null));
	}

	@Test
	void listAdminRejectsInvalidSort() {
		assertThrows(BadRequest.class,
			() -> service.listAdmin("all", null, null, null, 1, 20, "image"));
	}

	@Test
	void getPublicReturnsTheProductWhenItIsVisible() {
		when(repository.findPublicById(ID)).thenReturn(Optional.of(product()));

		assertEquals("Torta Tres Leches", service.getPublic(ID).name());
	}

	@Test
	void getPublicThrowsNotFoundWhenTheProductIsHidden() {
		when(repository.findPublicById(ID)).thenReturn(Optional.empty());

		assertThrows(NotFound.class, () -> service.getPublic(ID));
	}

	@Test
	void updateKeepsUnsentFieldsAndImagePath() {
		when(repository.findById(ID)).thenReturn(Optional.of(product()));
		when(repository.update(any(Product.class))).thenReturn(product());

		service.update(ID, null, null, null, null, new BigDecimal("99.90"),
			ImageChange.absent(), null, null);

		ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
		verify(repository).update(captor.capture());
		assertEquals(new BigDecimal("99.90"), captor.getValue().priceFrom());
		assertEquals("Torta Tres Leches", captor.getValue().name());
		assertEquals(CATEGORY_ID, captor.getValue().categoryId());
		assertEquals(IMAGE_PATH, captor.getValue().imagePath());
		verify(imageCleanup, never()).deleteAfterCommit(any());
	}

	@Test
	void updateRejectsEmptyBody() {
		when(repository.findById(ID)).thenReturn(Optional.of(product()));

		assertThrows(InvalidInput.class, () -> service.update(
			ID, null, null, null, null, null, ImageChange.absent(), null, null));
	}

	@Test
	void updateRejectsClearingTheImage() {
		when(repository.findById(ID)).thenReturn(Optional.of(product()));

		InvalidInput error = assertThrows(InvalidInput.class, () -> service.update(
			ID, null, null, null, null, null, ImageChange.clear(), null, null));

		assertTrue(error.details().containsKey("image"));
		verify(repository, never()).update(any(Product.class));
	}

	@Test
	void updateDeletesTheReplacedImage() {
		when(repository.findById(ID)).thenReturn(Optional.of(product()));
		when(imageResolver.resolvePath(NEW_IMAGE_URL)).thenReturn(Optional.of(NEW_IMAGE_PATH));
		when(repository.update(any(Product.class))).thenReturn(productWithImage(NEW_IMAGE_URL,
			NEW_IMAGE_PATH));

		service.update(ID, null, null, null, null, null,
			ImageChange.set(NEW_IMAGE_URL), null, null);

		verify(imageCleanup).deleteAfterCommit(IMAGE_PATH);
	}

	@Test
	void deleteRemovesTheImageAfterCommit() {
		when(repository.findById(ID)).thenReturn(Optional.of(product()));

		service.delete(ID);

		verify(repository).deleteById(ID);
		verify(imageCleanup).deleteAfterCommit(IMAGE_PATH);
	}

	@Test
	void deletePassesNullPathWhenTheProductHasNoImage() {
		when(repository.findById(ID)).thenReturn(Optional.of(productWithImage(null, null)));

		service.delete(ID);

		verify(imageCleanup).deleteAfterCommit(null);
	}

	private Product product() {
		return productWithImage(IMAGE_URL, IMAGE_PATH);
	}

	private Product productWithImage(String imageUrl, String imagePath) {
		Instant now = Instant.parse("2026-10-27T12:00:00Z");
		return new Product(
			ID, "Torta Tres Leches", CATEGORY_ID, "Tortas clásicas", "tortas-clasicas",
			"Bizcocho húmedo bañado en tres leches.", "20 porciones",
			new BigDecimal("85.00"), imageUrl, imagePath, true, true, now, now);
	}
}
