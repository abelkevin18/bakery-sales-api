package com.darkbit.bakerysales.faqs.infrastructure.persistence;

import com.darkbit.bakerysales.faqs.application.FaqRepository;
import com.darkbit.bakerysales.faqs.domain.Faq;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFaqRepository implements FaqRepository {

	private static final RowMapper<Faq> FAQ_ROW_MAPPER = JdbcFaqRepository::mapFaq;
	private static final String FAQ_COLUMNS =
		"id, question, answer, sort_order, published, created_at, updated_at";

	private final JdbcTemplate jdbcTemplate;

	public JdbcFaqRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Faq> findPublished() {
		return jdbcTemplate.query(
			"SELECT " + FAQ_COLUMNS + " FROM faqs WHERE published = true " +
				"ORDER BY sort_order ASC, created_at ASC, id ASC",
			FAQ_ROW_MAPPER);
	}

	@Override
	public FaqPage findAdmin(String status, String query, int page, int pageSize, String sort) {
		SortOrder sortOrder = SortOrder.from(sort);
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		if (!status.equals("all")) {
			where.append(" AND published = ?");
		}
		if (query != null && !query.isBlank()) {
			where.append(" AND (unaccent(lower(question)) LIKE unaccent(lower(?)) ESCAPE '\\' ")
				.append("OR unaccent(lower(answer)) LIKE unaccent(lower(?)) ESCAPE '\\')");
		}

		Object[] filters = filters(status, query);
		Long total = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM faqs" + where, Long.class, filters);
		long offset = ((long) page - 1) * pageSize;
		Object[] pageParameters = append(filters, pageSize, offset);
		List<Faq> data = jdbcTemplate.query(
			"SELECT " + FAQ_COLUMNS + " FROM faqs" + where +
				" ORDER BY " + sortOrder.column + " " + sortOrder.direction +
				", created_at ASC, id ASC LIMIT ? OFFSET ?",
			FAQ_ROW_MAPPER,
			pageParameters);
		return new FaqPage(data, total == null ? 0 : total);
	}

	@Override
	public Optional<Faq> findById(UUID id) {
		List<Faq> rows = jdbcTemplate.query(
			"SELECT " + FAQ_COLUMNS + " FROM faqs WHERE id = ?",
			FAQ_ROW_MAPPER,
			id);
		return rows.stream().findFirst();
	}

	@Override
	public Faq create(String question, String answer, int order, boolean published) {
		return jdbcTemplate.queryForObject(
			"INSERT INTO faqs (question, answer, sort_order, published, created_at, updated_at) " +
				"VALUES (?, ?, ?, ?, now(), now()) RETURNING " + FAQ_COLUMNS,
			FAQ_ROW_MAPPER,
			question, answer, order, published);
	}

	@Override
	public Faq update(Faq faq) {
		return jdbcTemplate.queryForObject(
			"UPDATE faqs SET question = ?, answer = ?, sort_order = ?, published = ?, updated_at = now() " +
				"WHERE id = ? RETURNING " + FAQ_COLUMNS,
			FAQ_ROW_MAPPER,
			faq.question(), faq.answer(), faq.order(), faq.published(), faq.id());
	}

	@Override
	public void deleteById(UUID id) {
		jdbcTemplate.update("DELETE FROM faqs WHERE id = ?", id);
	}

	private Object[] filters(String status, String query) {
		boolean filterStatus = !status.equals("all");
		boolean filterQuery = query != null && !query.isBlank();
		if (filterStatus && filterQuery) {
			String pattern = "%" + escapeLike(query) + "%";
			return new Object[] { status.equals("published"), pattern, pattern };
		}
		if (filterStatus) {
			return new Object[] { status.equals("published") };
		}
		if (filterQuery) {
			String pattern = "%" + escapeLike(query) + "%";
			return new Object[] { pattern, pattern };
		}
		return new Object[0];
	}

	private Object[] append(Object[] values, Object... additional) {
		Object[] result = java.util.Arrays.copyOf(values, values.length + additional.length);
		System.arraycopy(additional, 0, result, values.length, additional.length);
		return result;
	}

	private String escapeLike(String query) {
		return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Faq mapFaq(ResultSet resultSet, int rowNum) throws SQLException {
		return new Faq(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("question"),
			resultSet.getString("answer"),
			resultSet.getInt("sort_order"),
			resultSet.getBoolean("published"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private record SortOrder(String column, String direction) {
		private static SortOrder from(String requested) {
			boolean descending = requested.startsWith("-");
			String field = descending ? requested.substring(1) : requested;
			String column = switch (field) {
				case "order" -> "sort_order";
				case "question" -> "question";
				case "createdAt" -> "created_at";
				case "updatedAt" -> "updated_at";
				default -> throw new IllegalArgumentException("Unsupported FAQ sort field.");
			};
			return new SortOrder(column, descending ? "DESC" : "ASC");
		}
	}
}
