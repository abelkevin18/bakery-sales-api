package com.darkbit.bakerysales.testimonials.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.testimonials.domain.Testimonial;

public interface TestimonialRepository {

	List<Testimonial> findPublished(int limit);

	TestimonialPage findAdmin(
		String status, String query, Integer rating, int page, int pageSize, String sort);

	Optional<Testimonial> findById(UUID id);

	Testimonial create(
		String name, String occasion, int rating, String text,
		String imageUrl, String imagePath, boolean published);

	Testimonial update(Testimonial testimonial);

	void deleteById(UUID id);

	record TestimonialPage(List<Testimonial> data, long total) {
	}
}
