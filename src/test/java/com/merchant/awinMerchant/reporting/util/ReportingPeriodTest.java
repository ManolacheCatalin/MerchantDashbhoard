package com.merchant.awinMerchant.reporting.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class ReportingPeriodTest {

	@Test
	void boundariesAreUtcMidnightsWithExclusiveEnd() {
		var period = new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-08"));

		assertThat(period.startInclusive()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
		assertThat(period.endExclusive()).isEqualTo(OffsetDateTime.parse("2026-09-08T00:00:00Z"));
		assertThat(period.days()).isEqualTo(7);
	}

	@Test
	void previousPeriodHasSameLengthAndEndsWhereCurrentStarts() {
		var period = new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-08"));

		assertThat(period.previous())
			.isEqualTo(new ReportingPeriod(LocalDate.parse("2026-08-25"), LocalDate.parse("2026-09-01")));
	}

	@Test
	void previousPeriodUsesEqualDaysNotCalendarMonths() {
		// Jul + Aug + Sep = 92 days, so the previous period starts 92 days earlier (31 March), not on 1 April.
		var period = new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-10-01"));

		assertThat(period.days()).isEqualTo(92);
		assertThat(period.previous())
			.isEqualTo(new ReportingPeriod(LocalDate.parse("2026-03-31"), LocalDate.parse("2026-07-01")));
	}

	@Test
	void previousPeriodAcrossLeapDay() {
		var period = new ReportingPeriod(LocalDate.parse("2028-03-01"), LocalDate.parse("2028-03-08"));

		assertThat(period.previous())
			.isEqualTo(new ReportingPeriod(LocalDate.parse("2028-02-23"), LocalDate.parse("2028-03-01")));
	}

	@Test
	void singleDayPeriod() {
		var period = new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-02"));

		assertThat(period.days()).isEqualTo(1);
		assertThat(period.previous())
			.isEqualTo(new ReportingPeriod(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-01")));
	}

	@Test
	void rejectsEmptyPeriod() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-01")))
			.isInstanceOf(InvalidReportingPeriodException.class)
			.hasMessage("'from' must be before 'to'.");
	}

	@Test
	void rejectsReversedPeriod() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-01")))
			.isInstanceOf(InvalidReportingPeriodException.class);
	}

	@Test
	void rejectsMissingDates() {
		assertThatThrownBy(() -> new ReportingPeriod(null, LocalDate.parse("2026-09-01")))
			.isInstanceOf(InvalidReportingPeriodException.class);
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.parse("2026-09-01"), null))
			.isInstanceOf(InvalidReportingPeriodException.class);
	}

	@Test
	void rejectsDatesBeforeYearOne() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.of(-5000, 1, 1), LocalDate.parse("2026-09-08")))
			.isInstanceOf(InvalidReportingPeriodException.class)
			.hasMessage("'from' and 'to' must be between 0001-01-01 and 9999-12-31.");
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.of(0, 12, 31), LocalDate.of(1, 1, 2)))
			.isInstanceOf(InvalidReportingPeriodException.class);
	}

	@Test
	void rejectsDatesAfterYear9999() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.of(9999, 12, 30), LocalDate.of(10000, 1, 1)))
			.isInstanceOf(InvalidReportingPeriodException.class);
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.of(300000, 1, 1), LocalDate.of(300000, 1, 2)))
			.isInstanceOf(InvalidReportingPeriodException.class);
	}

	@Test
	void rejectsExtremeDatesWithoutArithmeticOverflow() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.MIN, LocalDate.MAX))
			.isInstanceOf(InvalidReportingPeriodException.class);
	}

	@Test
	void acceptsDatesAtBothCalendarBounds() {
		var earliest = new ReportingPeriod(LocalDate.of(1, 1, 1), LocalDate.of(1, 1, 2));
		var latest = new ReportingPeriod(LocalDate.of(9999, 12, 30), LocalDate.of(9999, 12, 31));

		assertThat(earliest.startInclusive()).isEqualTo(OffsetDateTime.parse("0001-01-01T00:00:00Z"));
		assertThat(latest.endExclusive()).isEqualTo(OffsetDateTime.parse("9999-12-31T00:00:00Z"));
	}

	@Test
	void comparisonPeriodMayStartOnTheEarliestDate() {
		var period = new ReportingPeriod(LocalDate.of(1, 1, 2), LocalDate.of(1, 1, 3));

		assertThat(period.previous()).isEqualTo(new ReportingPeriod(LocalDate.of(1, 1, 1), LocalDate.of(1, 1, 2)));
	}

	@Test
	void rejectsComparisonPeriodStartingBeforeTheEarliestDate() {
		var period = new ReportingPeriod(LocalDate.of(1, 1, 1), LocalDate.of(1, 1, 2));

		assertThatThrownBy(period::previous).isInstanceOf(InvalidReportingPeriodException.class)
			.hasMessage("The comparison period would start before 0001-01-01.");
	}

	@Test
	void acceptsAFullLeapYear() {
		var period = new ReportingPeriod(LocalDate.parse("2028-01-01"), LocalDate.parse("2029-01-01"));

		assertThat(period.days()).isEqualTo(ReportingPeriod.MAX_DAYS);
		assertThat(period.previous())
			.isEqualTo(new ReportingPeriod(LocalDate.parse("2026-12-31"), LocalDate.parse("2028-01-01")));
	}

	@Test
	void rejectsPeriodLongerThan366Days() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-03")))
			.isInstanceOf(InvalidReportingPeriodException.class)
			.hasMessage("The reporting period must not exceed 366 days.");
	}

	@Test
	void rejectsVeryLongPeriodWithinTheCalendar() {
		assertThatThrownBy(() -> new ReportingPeriod(LocalDate.parse("5000-07-01"), LocalDate.parse("9999-12-31")))
			.isInstanceOf(InvalidReportingPeriodException.class)
			.hasMessage("The reporting period must not exceed 366 days.");
	}

}
