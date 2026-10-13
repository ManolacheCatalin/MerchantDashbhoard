package com.merchant.awinMerchant.reporting.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A reporting window of whole UTC calendar days, half-open: {@code [from, to)}. {@code from} is included and
 * {@code to} is excluded, so {@code 2026-09-01..2026-09-08} covers seven days.
 * <p>
 * Dates must lie within {@link #EARLIEST_DATE}..{@link #LATEST_DATE} (ISO-8601 four-digit years). The ISO parser
 * also accepts signed and extended years, which PostgreSQL cannot store or which overflow date arithmetic; those are
 * rejected here as invalid input rather than failing later.
 * <p>
 * A period is at most {@link #MAX_DAYS} days long (one full year, including a leap year). This bounds the work and
 * response size of every request, e.g. the daily revenue trend's two series.
 */
public record ReportingPeriod(LocalDate from, LocalDate to) {

	public static final LocalDate EARLIEST_DATE = LocalDate.of(1, 1, 1);

	public static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);

	public static final int MAX_DAYS = 366;

	public ReportingPeriod {
		if (from == null || to == null) {
			throw new InvalidReportingPeriodException("Both 'from' and 'to' are required.");
		}
		if (from.isBefore(EARLIEST_DATE) || to.isAfter(LATEST_DATE)) {
			throw new InvalidReportingPeriodException("'from' and 'to' must be between 0001-01-01 and 9999-12-31.");
		}
		if (!from.isBefore(to)) {
			throw new InvalidReportingPeriodException("'from' must be before 'to'.");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
			throw new InvalidReportingPeriodException("The reporting period must not exceed 366 days.");
		}
	}

	public long days() {
		return ChronoUnit.DAYS.between(from, to);
	}

	/**
	 * The immediately preceding period with the same number of days: {@code [from - days, from)}. Checked here, not
	 * in the constructor, so a valid previous period is not itself rejected for its own (unused) comparison period.
	 */
	public ReportingPeriod previous() {
		LocalDate previousFrom = from.minusDays(days()); // cannot overflow: at most MAX_DAYS before a bounded date
		if (previousFrom.isBefore(EARLIEST_DATE)) {
			throw new InvalidReportingPeriodException("The comparison period would start before 0001-01-01.");
		}
		//from will become the new to/end for the previous period
		return new ReportingPeriod(previousFrom, from);
	}

	/** UTC midnight at the start of {@code from}; inclusive. */
	public OffsetDateTime startInclusive() {
		return from.atStartOfDay().atOffset(ZoneOffset.UTC);
	}

	/** UTC midnight at the start of {@code to}; exclusive. */
	public OffsetDateTime endExclusive() {
		return to.atStartOfDay().atOffset(ZoneOffset.UTC);
	}

}
