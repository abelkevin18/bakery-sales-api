package com.darkbit.bakerysales.faqs.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.darkbit.bakerysales.faqs.domain.Faq;

public interface FaqRepository {

	List<Faq> findPublished();

	FaqPage findAdmin(String status, String query, int page, int pageSize, String sort);

	Optional<Faq> findById(UUID id);

	Faq create(String question, String answer, int order, boolean published);

	Faq update(Faq faq);

	void deleteById(UUID id);

	record FaqPage(List<Faq> data, long total) {
	}
}
