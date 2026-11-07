package com.darkbit.bakerysales.categories.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.darkbit.bakerysales.categories.application.CategoryRepository;
import com.darkbit.bakerysales.categories.domain.Category;

@Repository
public class JdbcCategoryRepository implements CategoryRepository {

	private static final RowMapper<Category> CATEGORY_ROW_MAPPER = JdbcCategoryRepository::mapCategory;

	private static final String PRODUCT_COUNT_ALL =
		"(SELECT count(*) FROM products p WHERE p.category_id = c.id)";
	private static final String PRODUCT_COUNT_ACTIVE =
		"(SELECT count(*) FROM products p WHERE p.category_id = c.id AND p.active)";

	private static final String COLUMNS =
		"c.id, c.name, c.slug, c.description, c.active, c.created_at, c.updated_at";

	private final JdbcTemplate jdbcTemplate;

	public JdbcCategoryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Category> findActive() {
		return jdbcTemplate.query(
			"SELECT " + COLUMNS + ", " + PRODUCT_COUNT_ACTIVE + " AS product_count " +
				"FROM categories c WHERE c.active = true " +
				"ORDER BY c.name ASC, c.id ASC",
			CATEGORY_ROW_MAPPER);
	}

	@Override
	public CategoryPage findAdmin(String status, String query, int page, int pageSize, String sort) {
		SortOrder sortOrder = SortOrder.from(sort);
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		if (!status.equals("all")) {
			where.append(" AND c.active = ?");
		}
		if (query != null && !query.isBlank()) {
			where.append(" AND (unaccent(lower(c.name)) LIKE unaccent(lower(?)) ESCAPE '\\' ")
				.append("OR unaccent(lower(c.slug)) LIKE unaccent(lower(?)) ESCAPE '\\')");
		}

		Object[] filters = filters(status, query);
		Long total = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM categories c" + where, Long.class, filters);
		long offset = ((long) page - 1) * pageSize;
		Object[] pageParameters = append(filters, pageSize, offset);
		List<Category> data = jdbcTemplate.query(
			"SELECT " + COLUMNS + ", " + PRODUCT_COUNT_ALL + " AS product_count " +
				"FROM categories c" + where +
				" ORDER BY " + sortOrder.column + " " + sortOrder.direction +
				", c.id ASC LIMIT ? OFFSET ?",
			CATEGORY_ROW_MAPPER,
			pageParameters);
		return new CategoryPage(data, total == null ? 0 : total);
	}

	@Override
	public Optional<Category> findById(UUID id) {
		List<Category> rows = jdbcTemplate.query(
			"SELECT " + COLUMNS + ", " + PRODUCT_COUNT_ALL + " AS product_count " +
				"FROM categories c WHERE c.id = ?",
			CATEGORY_ROW_MAPPER,
			id);
		return rows.stream().findFirst();
	}

	@Override
	public boolean slugExists(String slug, UUID excludeId) {
		Long count = excludeId == null
			? jdbcTemplate.queryForObject(
				"SELECT count(*) FROM categories WHERE lower(slug) = lower(?)", Long.class, slug)
			: jdbcTemplate.queryForObject(
				"SELECT count(*) FROM categories WHERE lower(slug) = lower(?) AND id <> ?",
				Long.class, slug, excludeId);
		return count != null && count > 0;
	}

	@Override
	public Category create(String name, String slug, String description, boolean active) {
		return jdbcTemplate.queryForObject(
			"INSERT INTO categories (name, slug, description, active, created_at, updated_at) " +
				"VALUES (?, ?, ?, ?, now(), now()) " +
				"RETURNING id, name, slug, description, active, 0 AS product_count, " +
				"created_at, updated_at",
			CATEGORY_ROW_MAPPER,
			name, slug, description, active);
	}

	@Override
	public Category update(Category category) {
		return jdbcTemplate.queryForObject(
			"UPDATE categories c SET name = ?, description = ?, active = ?, updated_at = now() " +
				"WHERE c.id = ? " +
				"RETURNING c.id, c.name, c.slug, c.description, c.active, " +
				PRODUCT_COUNT_ALL + " AS product_count, c.created_at, c.updated_at",
			CATEGORY_ROW_MAPPER,
			category.name(), category.description(), category.active(), category.id());
	}

	@Override
	public void deleteById(UUID id) {
		jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
	}

	@Override
	public long countProducts(UUID categoryId) {
		Long count = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM products WHERE category_id = ?", Long.class, categoryId);
		return count == null ? 0 : count;
	}

	private Object[] filters(String status, String query) {
		boolean filterStatus = !status.equals("all");
		boolean filterQuery = query != null && !query.isBlank();
		if (filterStatus && filterQuery) {
			String pattern = "%" + escapeLike(query) + "%";
			return new Object[] { status.equals("active"), pattern, pattern };
		}
		if (filterStatus) {
			return new Object[] { status.equals("active") };
		}
		if (filterQuery) {
			String pattern = "%" + escapeLike(query) + "%";
			return new Object[] { pattern, pattern };
		}
		return new Object[0];
	}

	private Object[] append(Object[] values, Object... additional) {
		Object[] result = Arrays.copyOf(values, values.length + additional.length);
		System.arraycopy(additional, 0, result, values.length, additional.length);
		return result;
	}

	private String escapeLike(String query) {
		return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Category mapCategory(ResultSet resultSet, int rowNum) throws SQLException {
		return new Category(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("name"),
			resultSet.getString("slug"),
			resultSet.getString("description"),
			resultSet.getBoolean("active"),
			resultSet.getInt("product_count"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private record SortOrder(String column, String direction) {
		private static SortOrder from(String requested) {
			boolean descending = requested.startsWith("-");
			String field = descending ? requested.substring(1) : requested;
			String column = switch (field) {
				case "name" -> "c.name";
				case "createdAt" -> "c.created_at";
				case "updatedAt" -> "c.updated_at";
				default -> throw new IllegalArgumentException("Unsupported category sort field.");
			};
			return new SortOrder(column, descending ? "DESC" : "ASC");
		}
	}
}
