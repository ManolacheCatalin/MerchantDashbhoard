package com.merchant.awinMerchant.revenuetrend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.revenuetrend.entity.DailyRevenue;

class DailyRevenueSeriesTest {

	private static final ReportingPeriod SEPT_1_TO_8 = new ReportingPeriod(LocalDate.parse("2026-09-01"),
			LocalDate.parse("2026-09-08"));

	@Test
	void noAggregatedRowsGivesZeroForEveryDay() {
		List<DailyRevenue> series = DailyRevenueSeries.zeroFilled(SEPT_1_TO_8, List.of());

		assertThat(series).hasSize(7);
		assertThat(series).extracting(DailyRevenue::revenue).containsOnly(new BigDecimal("0.00"));
		assertThat(series.get(0).revenueDate()).isEqualTo(LocalDate.parse("2026-09-01"));
		assertThat(series.get(6).revenueDate()).isEqualTo(LocalDate.parse("2026-09-07")); // 'to' is exclusive
	}

	@Test
	void keepsAggregatedValuesAndFillsGapsInDateOrder() {
		List<DailyRevenue> series = DailyRevenueSeries.zeroFilled(SEPT_1_TO_8,
				List.of(day("2026-09-07", "60.25"), day("2026-09-01", "120.00"), day("2026-09-02", "180.00")));

		assertThat(series).containsExactly(day("2026-09-01", "120.00"), day("2026-09-02", "180.00"),
				day("2026-09-03", "0.00"), day("2026-09-04", "0.00"), day("2026-09-05", "0.00"),
				day("2026-09-06", "0.00"), day("2026-09-07", "60.25"));
	}

	@Test
	void filledZeroHasTwoDecimals() {
		BigDecimal filled = DailyRevenueSeries.zeroFilled(SEPT_1_TO_8, List.of()).get(0).revenue();

		assertThat(filled.scale()).isEqualTo(2);
		assertThat(filled.toPlainString()).isEqualTo("0.00");
	}

	@Test
	void ignoresRowsOutsideThePeriod() {
		List<DailyRevenue> series = DailyRevenueSeries.zeroFilled(SEPT_1_TO_8,
				List.of(day("2026-08-31", "5.00"), day("2026-09-08", "7.00")));

		assertThat(series).hasSize(7).extracting(DailyRevenue::revenue).containsOnly(new BigDecimal("0.00"));
	}

	@Test
	void spansMonthAndLeapDayBoundaries() {
		var period = new ReportingPeriod(LocalDate.parse("2028-02-27"), LocalDate.parse("2028-03-02"));

		assertThat(DailyRevenueSeries.zeroFilled(period, List.of())).extracting(DailyRevenue::revenueDate)
			.containsExactly(LocalDate.parse("2028-02-27"), LocalDate.parse("2028-02-28"),
					LocalDate.parse("2028-02-29"), LocalDate.parse("2028-03-01"));
	}

	@Test
	void singleDayPeriodHasOneEntry() {
		var period = new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-02"));

		assertThat(DailyRevenueSeries.zeroFilled(period, List.of(day("2026-09-01", "9.99"))))
			.containsExactly(day("2026-09-01", "9.99"));
	}

	private static DailyRevenue day(String date, String revenue) {
		return new DailyRevenue(LocalDate.parse(date), new BigDecimal(revenue));
	}

}
