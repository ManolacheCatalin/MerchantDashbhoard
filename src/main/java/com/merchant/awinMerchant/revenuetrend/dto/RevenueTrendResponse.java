package com.merchant.awinMerchant.revenuetrend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.revenuetrend.entity.DailyRevenue;

/**
 * Daily revenue for the requested period and the equally long previous period. Both {@code data} arrays have one
 * entry per day and align by index (first day of current with first day of previous).
 */
public record RevenueTrendResponse(long merchantId, String currency, Granularity granularity,
		PeriodSeries currentPeriod, PeriodSeries previousPeriod) {

	public record PeriodSeries(LocalDate from, LocalDate to, List<Point> data) {

		/** Maps an already zero-filled series to API points, keeping its date order. */
		public static PeriodSeries of(ReportingPeriod period, List<DailyRevenue> series) {
			return new PeriodSeries(period.from(), period.to(),
					series.stream().map(day -> new Point(day.revenueDate(), day.revenue())).toList());
		}

	}

	//api contract resoponse, equivalent to DailyRevenue on the dao level
	public record Point(LocalDate date, BigDecimal revenue) {
	}

}
