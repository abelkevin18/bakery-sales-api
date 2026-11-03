package com.darkbit.bakerysales.faqs.domain;

import java.time.Instant;
import java.util.UUID;

public record Faq(
		UUID id,
		String question,
		String answer,
		int order,
		boolean published,
		Instant createdAt,
		Instant updatedAt) {
}
