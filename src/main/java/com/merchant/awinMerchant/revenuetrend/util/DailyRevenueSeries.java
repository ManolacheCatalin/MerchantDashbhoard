package com.merchant.awinMerchant.revenuetrend.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.revenuetrend.entity.DailyRevenue;

/**
 * Turns sparse per-day aggregates into one entry for every calendar day of a
 * period.
 */
public final class DailyRevenueSeries {

	/** Scale 2, like the SQL sums, so filled days serialize as {@code 0.00} rather than {@code 0}. */
	public static final BigDecimal ZERO_REVENUE = new BigDecimal("0.00");

	private DailyRevenueSeries() {
	}

	/**
	 * Returns one entry per day in {@code [period.from, period.to)}, in date order.
	 * Days missing from
	 * {@code aggregated} get revenue {@code 0.00}; aggregated rows outside the
	 * period are ignored.
	 */
	public static List<DailyRevenue> zeroFilled(ReportingPeriod period, List<DailyRevenue> aggregated) {
		Map<LocalDate, BigDecimal> revenueByDate = aggregated.stream()
				.collect(Collectors.toMap(DailyRevenue::revenueDate, DailyRevenue::revenue));

		List<DailyRevenue> series = new ArrayList<>((int) period.days());
		for (LocalDate date = period.from(); date.isBefore(period.to()); date = date.plusDays(1)) {
			series.add(new DailyRevenue(date, revenueByDate.getOrDefault(date, ZERO_REVENUE)));
		}
		return series;
	}

}
