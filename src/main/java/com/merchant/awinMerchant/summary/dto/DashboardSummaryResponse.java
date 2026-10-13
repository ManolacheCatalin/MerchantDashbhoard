package com.merchant.awinMerchant.summary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.merchant.awinMerchant.summary.util.PercentageChange;

/**
 * Summary cards: each metric holds the requested period's value, the previous period's value and the change between
 * them. Money is a 2-decimal amount in {@code currency}.
 */
public record DashboardSummaryResponse(long merchantId, Period period, String currency,
		Metric<BigDecimal> revenue, Metric<Long> transactions, Metric<Long> clicks) {

	/** The requested period only, {@code [from, to)}; the previous period's dates are not returned (AR-2). */
	public record Period(LocalDate from, LocalDate to) {
	}

	/** {@code changePercentage} is {@code null} when the previous value is zero. */
	public record Metric<T>(T current, T previous, BigDecimal changePercentage) {

		//normal static factories for instantiation
		public static Metric<BigDecimal> ofAmounts(BigDecimal current, BigDecimal previous) {
			return new Metric<>(current, previous, PercentageChange.between(current, previous));
		}

		public static Metric<Long> ofCounts(long current, long previous) {
			return new Metric<>(current, previous, PercentageChange.between(current, previous));
		}

	}

}
