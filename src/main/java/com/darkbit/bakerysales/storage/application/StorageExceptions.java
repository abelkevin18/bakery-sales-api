package com.darkbit.bakerysales.storage.application;

import java.util.List;
import java.util.Map;

public final class StorageExceptions {

	private StorageExceptions() {
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

	public static class PayloadTooLarge extends RuntimeException {
		public PayloadTooLarge(String message) {
			super(message);
		}
	}

	public static class UnsupportedMediaType extends RuntimeException {
		public UnsupportedMediaType(String message) {
			super(message);
		}
	}

	public static class NotFound extends RuntimeException {
		public NotFound(String message) {
			super(message);
		}
	}

	public static class Conflict extends RuntimeException {
		public Conflict(String message) {
			super(message);
		}
	}

	public static class StorageUnavailable extends RuntimeException {
		public StorageUnavailable(String message) {
			super(message);
		}
	}
}
