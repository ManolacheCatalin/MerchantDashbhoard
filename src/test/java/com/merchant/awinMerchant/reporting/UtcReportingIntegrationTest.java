package com.merchant.awinMerchant.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.merchant.awinMerchant.revenuetrend.dao.TrendTransactionDao;
import com.merchant.awinMerchant.revenuetrend.entity.DailyRevenue;
import com.merchant.awinMerchant.summary.dao.ClickDao;
import com.merchant.awinMerchant.summary.dao.SummaryTransactionDao;
import com.merchant.awinMerchant.summary.entity.TransactionTotals;
import com.merchant.awinMerchant.support.TestDatabase;

/**
 * Tests run with a UTC JVM and database session by default (build.gradle), where a missing
 * {@code AT TIME ZONE 'UTC'} would go unnoticed. These tests switch the PostgreSQL session to zones on both sides of
 * UTC, only for their own transaction ({@code SET LOCAL}), and prove the DAO results do not depend on it.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtcReportingIntegrationTest {

	/** UTC+14: a session-zone date would move late-UTC rows to the next day. */
	private static final String AHEAD_OF_UTC = "Pacific/Kiritimati";

	/** UTC-7 in September: a session-zone date would move early-UTC rows to the previous day. */
	private static final String BEHIND_UTC = "America/Los_Angeles";

	private static final OffsetDateTime SEPT_1 = OffsetDateTime.parse("2026-09-01T00:00:00Z");

	private static final OffsetDateTime SEPT_8 = OffsetDateTime.parse("2026-09-08T00:00:00Z");

	@Autowired
	private Jdbi jdbi;

	@BeforeEach
	void setUpFixtures() {
		TestDatabase db = new TestDatabase(jdbi);
		db.reset();
		db.insertMerchant(1, "Merchant A", "GBP");
		db.insertTransaction(1, "10.00", "APPROVED", "2026-09-01T00:00:00Z"); // inclusive start
		db.insertTransaction(1, "20.00", "APPROVED", "2026-09-04T22:30:00Z"); // late UTC: 09-05 in Kiritimati
		db.insertTransaction(1, "30.00", "APPROVED", "2026-09-05T01:00:00Z"); // early UTC: 09-04 in Los Angeles
		db.insertTransaction(1, "99.00", "APPROVED", "2026-09-08T00:00:00Z"); // exclusive end
		db.insertTransaction(1, "88.00", "APPROVED", "2026-08-31T23:59:59Z"); // just before start
		db.insertClick(1, "2026-09-01T00:00:00Z");
		db.insertClick(1, "2026-09-07T23:59:59Z");
		db.insertClick(1, "2026-09-08T00:00:00Z"); // exclusive end
	}

	@Test
	void dailyBucketsUseUtcDatesRegardlessOfSessionTimeZone() {
		for (String zone : List.of(AHEAD_OF_UTC, BEHIND_UTC)) {
			List<DailyRevenue> days = inSessionTimeZone(zone,
					handle -> handle.attach(TrendTransactionDao.class).sumApprovedByUtcDay(1, SEPT_1, SEPT_8));

			assertThat(days).as("session time zone %s", zone)
				.containsExactly(new DailyRevenue(LocalDate.parse("2026-09-01"), new BigDecimal("10.00")),
						new DailyRevenue(LocalDate.parse("2026-09-04"), new BigDecimal("20.00")),
						new DailyRevenue(LocalDate.parse("2026-09-05"), new BigDecimal("30.00")));
		}
	}

	@Test
	void summaryWindowsUseUtcBoundariesRegardlessOfSessionTimeZone() {
		for (String zone : List.of(AHEAD_OF_UTC, BEHIND_UTC)) {
			TransactionTotals totals = inSessionTimeZone(zone,
					handle -> handle.attach(SummaryTransactionDao.class).sumApproved(1, SEPT_1, SEPT_8));
			long clicks = inSessionTimeZone(zone, handle -> handle.attach(ClickDao.class).count(1, SEPT_1, SEPT_8));

			assertThat(totals).as("session time zone %s", zone)
				.isEqualTo(new TransactionTotals(new BigDecimal("60.00"), 3));
			assertThat(clicks).as("session time zone %s", zone).isEqualTo(2);
		}
	}

	/**
	 * Runs {@code work} in a transaction whose PostgreSQL session time zone is {@code zone}. {@code SET LOCAL} ends with
	 * the transaction, so the pooled connection goes back to UTC for other tests.
	 */
	private <T> T inSessionTimeZone(String zone, Function<Handle, T> work) {
		return jdbi.inTransaction(handle -> {
			// is_local = true: same as SET LOCAL, reverted when this transaction ends
			handle.createQuery("SELECT set_config('TimeZone', :zone, true)").bind("zone", zone).mapTo(String.class).one();
			String active = handle.createQuery("SELECT current_setting('TimeZone')").mapTo(String.class).one();
			assertThat(active).as("session time zone must really be non-UTC for this test").isEqualTo(zone);
			return work.apply(handle);
		});
	}

}
