package com.darkbit.bakerysales.testimonials.domain;

import java.time.Instant;
import java.util.UUID;

public record Testimonial(
		UUID id,
		String name,
		String occasion,
		int rating,
		String text,
		String imageUrl,
		String imagePath,
		boolean published,
		Instant createdAt,
		Instant updatedAt) {
}
