package com.darkbit.bakerysales.testimonials.application;

import static com.darkbit.bakerysales.testimonials.application.TestimonialExceptions.BadRequest;
import static com.darkbit.bakerysales.testimonials.application.TestimonialExceptions.InvalidInput;
import static com.darkbit.bakerysales.testimonials.application.TestimonialExceptions.NotFound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.darkbit.bakerysales.storage.application.ImageCleanupService;
import com.darkbit.bakerysales.storage.application.PublicImageUrlResolver;
import com.darkbit.bakerysales.testimonials.application.TestimonialRepository.TestimonialPage;
import com.darkbit.bakerysales.testimonials.domain.Testimonial;

@Service
public class TestimonialService {

	private static final Set<String> SORT_FIELDS = Set.of("createdAt", "rating", "name");
	private static final String IMAGE_ERROR =
		"La imagen debe ser la URL pública devuelta por /admin/uploads/sign o null.";

	private final TestimonialRepository repository;
	private final PublicImageUrlResolver imageResolver;
	private final ImageCleanupService imageCleanup;

	public TestimonialService(
			TestimonialRepository repository,
			PublicImageUrlResolver imageResolver,
			ImageCleanupService imageCleanup) {
		this.repository = repository;
		this.imageResolver = imageResolver;
		this.imageCleanup = imageCleanup;
	}

	@Transactional(readOnly = true)
	public List<Testimonial> listPublished(int limit) {
		if (limit < 1 || limit > 20) {
			throw new BadRequest("limit debe estar entre 1 y 20.");
		}
		return repository.findPublished(limit);
	}

	@Transactional(readOnly = true)
	public TestimonialPage listAdmin(
			String status, String query, Integer rating, int page, int pageSize, String sort) {
		if (status == null || !Set.of("published", "draft", "all").contains(status)) {
			throw new BadRequest("El parámetro status debe ser published, draft o all.");
		}
		if (page < 1) {
			throw new BadRequest("page debe ser mayor o igual a 1.");
		}
		if (pageSize < 1 || pageSize > 100) {
			throw new BadRequest("pageSize debe estar entre 1 y 100.");
		}
		String normalizedQuery = query == null ? null : query.trim();
		if (normalizedQuery != null && normalizedQuery.length() > 100) {
			throw new BadRequest("q no puede superar los 100 caracteres.");
		}
		if (rating != null && (rating < 1 || rating > 5)) {
			throw new BadRequest("rating debe estar entre 1 y 5.");
		}
		if (sort == null || sort.isBlank()) {
			sort = "-createdAt";
		}
		String sortField = sort.startsWith("-") ? sort.substring(1) : sort;
		if (!SORT_FIELDS.contains(sortField)) {
			throw new BadRequest("El parámetro sort no contiene un campo permitido.");
		}
		return repository.findAdmin(status,
			normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery,
			rating, page, pageSize, sort);
	}

	@Transactional(readOnly = true)
	public Testimonial get(UUID id) {
		return repository.findById(id).orElseThrow(NotFound::new);
	}

	@Transactional
	public Testimonial create(
			String name, String occasion, Integer rating, String text, String image, boolean published) {
		Map<String, List<String>> errors = new HashMap<>();
		validate(name, occasion, rating, text, errors);
		String imageUrl = normalizeImage(image);
		String imagePath = resolveImagePath(imageUrl, errors);
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}
		return repository.create(
			name.trim(), occasion.trim(), rating, text.trim(), imageUrl, imagePath, published);
	}

	@Transactional
	public Testimonial update(
			UUID id, String name, String occasion, Integer rating, String text,
			ImageChange imageChange, Boolean published) {
		Testimonial existing = get(id);
		if (name == null && occasion == null && rating == null && text == null
				&& !imageChange.present() && published == null) {
			throw new InvalidInput(Map.of("body", List.of("Debe enviar al menos un campo para actualizar.")));
		}

		String updatedName = name == null ? existing.name() : name.trim();
		String updatedOccasion = occasion == null ? existing.occasion() : occasion.trim();
		int updatedRating = rating == null ? existing.rating() : rating;
		String updatedText = text == null ? existing.text() : text.trim();

		Map<String, List<String>> errors = new HashMap<>();
		validate(updatedName, updatedOccasion, updatedRating, updatedText, errors);

		String imageUrl = existing.imageUrl();
		String imagePath = existing.imagePath();
		if (imageChange.present()) {
			imageUrl = normalizeImage(imageChange.url());
			imagePath = resolveImagePath(imageUrl, errors);
		}
		if (!errors.isEmpty()) {
			throw new InvalidInput(errors);
		}

		Testimonial updated = repository.update(new Testimonial(
			id, updatedName, updatedOccasion, updatedRating, updatedText,
			imageUrl, imagePath,
			published == null ? existing.published() : published,
			existing.createdAt(), existing.updatedAt()));
		if (!Objects.equals(existing.imagePath(), updated.imagePath())) {
			imageCleanup.deleteAfterCommit(existing.imagePath());
		}
		return updated;
	}

	@Transactional
	public void delete(UUID id) {
		Testimonial existing = get(id);
		repository.deleteById(id);
		imageCleanup.deleteAfterCommit(existing.imagePath());
	}

	private void validate(
			String name, String occasion, Integer rating, String text,
			Map<String, List<String>> errors) {
		if (name == null || name.trim().length() < 3 || name.trim().length() > 60) {
			errors.put("name", List.of("Debe tener entre 3 y 60 caracteres."));
		}
		if (occasion == null || occasion.trim().length() < 3 || occasion.trim().length() > 60) {
			errors.put("occasion", List.of("Debe tener entre 3 y 60 caracteres."));
		}
		if (rating == null || rating < 1 || rating > 5) {
			errors.put("rating", List.of("La calificación debe ser un entero entre 1 y 5."));
		}
		if (text == null || text.trim().length() < 20 || text.trim().length() > 400) {
			errors.put("text", List.of("Debe tener entre 20 y 400 caracteres."));
		}
	}

	private String normalizeImage(String image) {
		if (image == null || image.isBlank()) {
			return null;
		}
		return image.trim();
	}

	private String resolveImagePath(String imageUrl, Map<String, List<String>> errors) {
		if (imageUrl == null) {
			return null;
		}
		return imageResolver.resolvePath(imageUrl).orElseGet(() -> {
			errors.put("image", List.of(IMAGE_ERROR));
			return null;
		});
	}

	public record ImageChange(boolean present, String url) {

		public static ImageChange absent() {
			return new ImageChange(false, null);
		}

		public static ImageChange clear() {
			return new ImageChange(true, null);
		}

		public static ImageChange set(String url) {
			return new ImageChange(true, url);
		}
	}
}
