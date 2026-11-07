package com.darkbit.bakerysales.categories.application;

import java.util.List;
import java.util.Map;

public final class CategoryExceptions {

	private CategoryExceptions() {
	}

	public static class NotFound extends RuntimeException {
		public NotFound() {
			super("No se encontró la categoría solicitada.");
		}
	}

	public static class InvalidInput extends RuntimeException {
		private final Map<String, List<String>> details;

		public InvalidInput(Map<String, List<String>> details) {
			super("Revisa los campos marcados.");
			this.details = Map.copyOf(details);
		}

		public Map<String, List<String>> details() {
			return details;
		}
	}

	public static class Conflict extends RuntimeException {
		private final Map<String, List<String>> details;

		public Conflict(String message, Map<String, List<String>> details) {
			super(message);
			this.details = Map.copyOf(details);
		}

		public Map<String, List<String>> details() {
			return details;
		}
	}

	public static class BadRequest extends RuntimeException {
		public BadRequest(String message) {
			super(message);
		}
	}
}
