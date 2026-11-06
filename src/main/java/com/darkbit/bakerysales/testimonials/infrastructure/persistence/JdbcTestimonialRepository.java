package com.darkbit.bakerysales.testimonials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.darkbit.bakerysales.testimonials.application.TestimonialRepository;
import com.darkbit.bakerysales.testimonials.domain.Testimonial;

@Repository
public class JdbcTestimonialRepository implements TestimonialRepository {

	private static final RowMapper<Testimonial> ROW_MAPPER = JdbcTestimonialRepository::mapTestimonial;
	private static final String COLUMNS =
		"id, name, occasion, rating, text, image_url, image_path, published, created_at, updated_at";

	private final JdbcTemplate jdbcTemplate;

	public JdbcTestimonialRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Testimonial> findPublished(int limit) {
		return jdbcTemplate.query(
			"SELECT " + COLUMNS + " FROM testimonials WHERE published = true " +
				"ORDER BY created_at DESC, id DESC LIMIT ?",
			ROW_MAPPER,
			limit);
	}

	@Override
	public TestimonialPage findAdmin(
			String status, String query, Integer rating, int page, int pageSize, String sort) {
		SortOrder sortOrder = SortOrder.from(sort);
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		List<Object> filters = new ArrayList<>();
		if (!status.equals("all")) {
			where.append(" AND published = ?");
			filters.add(status.equals("published"));
		}
		if (rating != null) {
			where.append(" AND rating = ?");
			filters.add(rating);
		}
		if (query != null && !query.isBlank()) {
			String pattern = "%" + escapeLike(query) + "%";
			where.append(" AND (unaccent(lower(name)) LIKE unaccent(lower(?)) ESCAPE '\\'")
				.append(" OR unaccent(lower(occasion)) LIKE unaccent(lower(?)) ESCAPE '\\'")
				.append(" OR unaccent(lower(text)) LIKE unaccent(lower(?)) ESCAPE '\\')");
			filters.add(pattern);
			filters.add(pattern);
			filters.add(pattern);
		}

		Long total = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM testimonials" + where,
			Long.class,
			filters.toArray());

		long offset = ((long) page - 1) * pageSize;
		List<Object> pageParameters = new ArrayList<>(filters);
		pageParameters.add(pageSize);
		pageParameters.add(offset);
		List<Testimonial> data = jdbcTemplate.query(
			"SELECT " + COLUMNS + " FROM testimonials" + where +
				" ORDER BY " + sortOrder.column() + " " + sortOrder.direction() +
				", created_at DESC, id DESC LIMIT ? OFFSET ?",
			ROW_MAPPER,
			pageParameters.toArray());

		return new TestimonialPage(data, total == null ? 0 : total);
	}

	@Override
	public Optional<Testimonial> findById(UUID id) {
		List<Testimonial> rows = jdbcTemplate.query(
			"SELECT " + COLUMNS + " FROM testimonials WHERE id = ?",
			ROW_MAPPER,
			id);
		return rows.stream().findFirst();
	}

	@Override
	public Testimonial create(
			String name, String occasion, int rating, String text,
			String imageUrl, String imagePath, boolean published) {
		return jdbcTemplate.queryForObject(
			"INSERT INTO testimonials " +
				"(name, occasion, rating, text, image_url, image_path, published, created_at, updated_at) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, now(), now()) RETURNING " + COLUMNS,
			ROW_MAPPER,
			name, occasion, rating, text, imageUrl, imagePath, published);
	}

	@Override
	public Testimonial update(Testimonial testimonial) {
		return jdbcTemplate.queryForObject(
			"UPDATE testimonials SET name = ?, occasion = ?, rating = ?, text = ?, " +
				"image_url = ?, image_path = ?, published = ?, updated_at = now() " +
				"WHERE id = ? RETURNING " + COLUMNS,
			ROW_MAPPER,
			testimonial.name(), testimonial.occasion(), testimonial.rating(), testimonial.text(),
			testimonial.imageUrl(), testimonial.imagePath(), testimonial.published(),
			testimonial.id());
	}

	@Override
	public void deleteById(UUID id) {
		jdbcTemplate.update("DELETE FROM testimonials WHERE id = ?", id);
	}

	private String escapeLike(String query) {
		return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Testimonial mapTestimonial(ResultSet resultSet, int rowNum) throws SQLException {
		return new Testimonial(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("name"),
			resultSet.getString("occasion"),
			resultSet.getInt("rating"),
			resultSet.getString("text"),
			resultSet.getString("image_url"),
			resultSet.getString("image_path"),
			resultSet.getBoolean("published"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private record SortOrder(String column, String direction) {
		private static SortOrder from(String requested) {
			boolean descending = requested.startsWith("-");
			String field = descending ? requested.substring(1) : requested;
			String column = switch (field) {
				case "createdAt" -> "created_at";
				case "rating" -> "rating";
				case "name" -> "name";
				default -> throw new IllegalArgumentException("Unsupported testimonial sort field.");
			};
			return new SortOrder(column, descending ? "DESC" : "ASC");
		}
	}
}
