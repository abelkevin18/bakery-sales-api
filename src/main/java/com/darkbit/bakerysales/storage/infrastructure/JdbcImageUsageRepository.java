package com.darkbit.bakerysales.storage.infrastructure;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcImageUsageRepository {

	private static final List<String> IMAGE_TABLES = List.of("products", "testimonials");

	private final JdbcTemplate jdbcTemplate;

	public JdbcImageUsageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean isReferenced(String path) {
		for (String table : IMAGE_TABLES) {
			if (!tableExists(table)) {
				continue;
			}
			Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE image_path = ?", Long.class, path);
			if (count != null && count > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean tableExists(String table) {
		Boolean exists = jdbcTemplate.queryForObject(
			"SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table);
		return Boolean.TRUE.equals(exists);
	}
}
