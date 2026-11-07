package com.darkbit.bakerysales.products.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.darkbit.bakerysales.products.application.ProductRepository;
import com.darkbit.bakerysales.products.domain.Product;

@Repository
public class JdbcProductRepository implements ProductRepository {

	private static final RowMapper<Product> PRODUCT_ROW_MAPPER =
		JdbcProductRepository::mapProduct;

	private static final String COLUMNS =
		"p.id, p.name, p.category_id, c.name AS category_name, c.slug AS category_slug, " +
			"p.description, p.size, p.price_from, p.image_url, p.image_path, " +
			"p.featured, p.active, p.created_at, p.updated_at";

	private static final String FROM = " FROM products p JOIN categories c ON c.id = p.category_id";

	private final JdbcTemplate jdbcTemplate;

	public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Product> findPublic(String categorySlug, Boolean featured) {
		StringBuilder where = new StringBuilder(" WHERE p.active = true AND c.active = true");
		List<Object> filters = new ArrayList<>();
		if (categorySlug != null) {
			where.append(" AND lower(c.slug) = lower(?)");
			filters.add(categorySlug);
		}
		if (featured != null) {
			where.append(" AND p.featured = ?");
			filters.add(featured);
		}
		return jdbcTemplate.query(
			"SELECT " + COLUMNS + FROM + where +
				" ORDER BY p.featured DESC, p.name ASC, p.id ASC",
			PRODUCT_ROW_MAPPER,
			filters.toArray());
	}

	@Override
	public ProductPage findAdmin(
			String query, UUID categoryId, String status, Boolean featured,
			int page, int pageSize, String sort) {
		SortOrder sortOrder = SortOrder.from(sort);
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		List<Object> filters = new ArrayList<>();
		if (!status.equals("all")) {
			where.append(" AND p.active = ?");
			filters.add(status.equals("active"));
		}
		if (categoryId != null) {
			where.append(" AND p.category_id = ?");
			filters.add(categoryId);
		}
		if (featured != null) {
			where.append(" AND p.featured = ?");
			filters.add(featured);
		}
		if (query != null && !query.isBlank()) {
			String pattern = "%" + escapeLike(query) + "%";
			where.append(" AND (unaccent(lower(p.name)) LIKE unaccent(lower(?)) ESCAPE '\\'")
				.append(" OR unaccent(lower(p.description)) LIKE unaccent(lower(?)) ESCAPE '\\')");
			filters.add(pattern);
			filters.add(pattern);
		}

		Long total = jdbcTemplate.queryForObject(
			"SELECT count(*)" + FROM + where, Long.class, filters.toArray());

		long offset = ((long) page - 1) * pageSize;
		Object[] pageParameters = append(filters.toArray(), pageSize, offset);
		List<Product> data = jdbcTemplate.query(
			"SELECT " + COLUMNS + FROM + where +
				" ORDER BY " + sortOrder.column() + " " + sortOrder.direction() +
				", p.id ASC LIMIT ? OFFSET ?",
			PRODUCT_ROW_MAPPER,
			pageParameters);

		return new ProductPage(data, total == null ? 0 : total);
	}

	@Override
	public Optional<Product> findById(UUID id) {
		List<Product> rows = jdbcTemplate.query(
			"SELECT " + COLUMNS + FROM + " WHERE p.id = ?",
			PRODUCT_ROW_MAPPER,
			id);
		return rows.stream().findFirst();
	}

	@Override
	public Optional<Product> findPublicById(UUID id) {
		List<Product> rows = jdbcTemplate.query(
			"SELECT " + COLUMNS + FROM +
				" WHERE p.id = ? AND p.active = true AND c.active = true",
			PRODUCT_ROW_MAPPER,
			id);
		return rows.stream().findFirst();
	}

	@Override
	public boolean categoryExists(UUID categoryId) {
		Long count = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM categories WHERE id = ?", Long.class, categoryId);
		return count != null && count > 0;
	}

	@Override
	public Product create(
			String name, UUID categoryId, String description, String size,
			BigDecimal priceFrom, String imageUrl, String imagePath,
			boolean featured, boolean active) {
		UUID id = jdbcTemplate.queryForObject(
			"INSERT INTO products " +
				"(category_id, name, description, size, price_from, image_url, image_path, " +
				"featured, active, created_at, updated_at) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) RETURNING id",
			UUID.class,
			categoryId, name, description, size, priceFrom, imageUrl, imagePath,
			featured, active);
		return findById(id).orElseThrow();
	}

	@Override
	public Product update(Product product) {
		jdbcTemplate.update(
			"UPDATE products SET category_id = ?, name = ?, description = ?, size = ?, " +
				"price_from = ?, image_url = ?, image_path = ?, featured = ?, active = ?, " +
				"updated_at = now() WHERE id = ?",
			product.categoryId(), product.name(), product.description(), product.size(),
			product.priceFrom(), product.imageUrl(), product.imagePath(),
			product.featured(), product.active(), product.id());
		return findById(product.id()).orElseThrow();
	}

	@Override
	public void deleteById(UUID id) {
		jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
	}

	private Object[] append(Object[] values, Object... additional) {
		Object[] result = Arrays.copyOf(values, values.length + additional.length);
		System.arraycopy(additional, 0, result, values.length, additional.length);
		return result;
	}

	private String escapeLike(String query) {
		return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Product mapProduct(ResultSet resultSet, int rowNum) throws SQLException {
		return new Product(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("name"),
			resultSet.getObject("category_id", UUID.class),
			resultSet.getString("category_name"),
			resultSet.getString("category_slug"),
			resultSet.getString("description"),
			resultSet.getString("size"),
			resultSet.getBigDecimal("price_from"),
			resultSet.getString("image_url"),
			resultSet.getString("image_path"),
			resultSet.getBoolean("featured"),
			resultSet.getBoolean("active"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private record SortOrder(String column, String direction) {
		private static SortOrder from(String requested) {
			boolean descending = requested.startsWith("-");
			String field = descending ? requested.substring(1) : requested;
			String column = switch (field) {
				case "name" -> "p.name";
				case "priceFrom" -> "p.price_from";
				case "createdAt" -> "p.created_at";
				case "updatedAt" -> "p.updated_at";
				default -> throw new IllegalArgumentException("Unsupported product sort field.");
			};
			return new SortOrder(column, descending ? "DESC" : "ASC");
		}
	}
}
