package com.darkbit.bakerysales.categories.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugGeneratorTests {

	private final SlugGenerator generator = new SlugGenerator();

	@Test
	void removesAccents() {
		assertEquals("tortas-clasicas", generator.generate("Tortas Clásicas"));
	}

	@Test
	void handlesEnye() {
		assertEquals("pina-colada", generator.generate("Piña Colada"));
	}

	@Test
	void collapsesSeparators() {
		assertEquals("bocaditos-salados", generator.generate("  Bocaditos   Salados!  "));
	}

	@Test
	void keepsNumbers() {
		assertEquals("tortas-100-por-ciento", generator.generate("Tortas 100% por ciento"));
	}

	@Test
	void truncatesToFiftyCharacters() {
		String slug = generator.generate("a".repeat(80));
		assertEquals(50, slug.length());
	}

	@Test
	void fallsBackWhenNothingRemains() {
		assertEquals("categoria", generator.generate("!!!"));
	}
}
