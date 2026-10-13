package com.merchant.awinMerchant.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;

import org.jdbi.v3.core.Jdbi;

import com.merchant.awinMerchant.security.ApiKeyHasher;

/** Shared fixture helpers for integration tests running against the dedicated awin_test database. */
public final class TestDatabase {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final Jdbi jdbi;

	public TestDatabase(Jdbi jdbi) {
		this.jdbi = jdbi;
	}

	/**
	 * Empties all business tables after verifying the connection really points at awin_test. If test resources are
	 * missing from the classpath (e.g. a stale IDE build), the "test" profile would fall back to the dev database.
	 */
	public void reset() {
		jdbi.useHandle(handle -> {
			String database = handle.createQuery("SELECT current_database()").mapTo(String.class).one();
			assertThat(database).as("integration tests must run against awin_test").isEqualTo("awin_test");
			handle.execute(
					"TRUNCATE merchant_api_keys, todo_items, clicks, transactions, merchants RESTART IDENTITY CASCADE");
		});
	}

	/** Inserts a merchant with a fixed id and moves the identity sequence past it. */
	public void insertMerchant(long id, String name, String reportingCurrency) {
		jdbi.useHandle(handle -> {
			handle.execute("INSERT INTO merchants (id, name, reporting_currency) VALUES (?, ?, ?)", id, name,
					reportingCurrency);
			handle.execute("SELECT setval(pg_get_serial_sequence('merchants', 'id'), (SELECT max(id) FROM merchants))");
		});
	}

	/** Generates a random API key, stores only its hash, and returns the raw key for use in requests. */
	public String insertApiKey(long merchantId, boolean active) {
		String apiKey = newApiKey();
		jdbi.useHandle(handle -> handle.execute(
				"INSERT INTO merchant_api_keys (merchant_id, key_hash, active) VALUES (?, ?, ?)", merchantId,
				ApiKeyHasher.sha256Hex(apiKey), active));
		return apiKey;
	}

	/** {@code occurredAt} is an ISO-8601 timestamp with offset, e.g. {@code 2026-09-01T00:00:00Z}. */
	public void insertTransaction(long merchantId, String amount, String status, String occurredAt) {
		jdbi.useHandle(handle -> handle.execute(
				"INSERT INTO transactions (merchant_id, amount, status, occurred_at) VALUES (?, ?::numeric, ?, ?::timestamptz)",
				merchantId, amount, status, occurredAt));
	}

	public void insertClick(long merchantId, String occurredAt) {
		jdbi.useHandle(handle -> handle.execute(
				"INSERT INTO clicks (merchant_id, occurred_at) VALUES (?, ?::timestamptz)", merchantId, occurredAt));
	}

	public static String newApiKey() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
