package com.merchant.awinMerchant.revenuetrend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.merchant.awinMerchant.merchant.entity.Merchant;
import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.revenuetrend.dao.TrendTransactionDao;
import com.merchant.awinMerchant.revenuetrend.dto.Granularity;
import com.merchant.awinMerchant.revenuetrend.dto.RevenueTrendResponse.Point;
import com.merchant.awinMerchant.revenuetrend.entity.DailyRevenue;

@ExtendWith(MockitoExtension.class)
class RevenueTrendServiceTest {

	private static final OffsetDateTime PREVIOUS_START = OffsetDateTime.parse("2026-08-25T00:00:00Z");

	private static final OffsetDateTime CURRENT_START = OffsetDateTime.parse("2026-09-01T00:00:00Z");

	private static final OffsetDateTime CURRENT_END = OffsetDateTime.parse("2026-09-08T00:00:00Z");

	@Mock
	private MerchantService merchantService;

	@Mock
	private TrendTransactionDao transactionDao;

	@Test
	void queriesBothUtcWindowsAndReturnsAlignedZeroFilledSeries() {
		when(merchantService.getMerchant(1)).thenReturn(new Merchant(1, "Merchant A", "GBP"));
		// Strict stubs: the test fails if the service queries any other window.
		when(transactionDao.sumApprovedByUtcDay(1, CURRENT_START, CURRENT_END))
			.thenReturn(List.of(new DailyRevenue(LocalDate.parse("2026-09-02"), new BigDecimal("180.00"))));
		when(transactionDao.sumApprovedByUtcDay(1, PREVIOUS_START, CURRENT_START))
			.thenReturn(List.of(new DailyRevenue(LocalDate.parse("2026-08-25"), new BigDecimal("90.00"))));

		var trend = new RevenueTrendService(merchantService, transactionDao).getDailyTrend(1,
				new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-08")));

		assertThat(trend.merchantId()).isEqualTo(1);
		assertThat(trend.currency()).isEqualTo("GBP");
		assertThat(trend.granularity()).isEqualTo(Granularity.DAY);

		assertThat(trend.currentPeriod().from()).isEqualTo(LocalDate.parse("2026-09-01"));
		assertThat(trend.currentPeriod().to()).isEqualTo(LocalDate.parse("2026-09-08"));
		assertThat(trend.previousPeriod().from()).isEqualTo(LocalDate.parse("2026-08-25"));
		assertThat(trend.previousPeriod().to()).isEqualTo(LocalDate.parse("2026-09-01"));

		assertThat(trend.currentPeriod().data()).hasSize(7);
		assertThat(trend.previousPeriod().data()).hasSize(7);
		assertThat(trend.currentPeriod().data().get(0))
			.isEqualTo(new Point(LocalDate.parse("2026-09-01"), new BigDecimal("0.00")));
		assertThat(trend.currentPeriod().data().get(1))
			.isEqualTo(new Point(LocalDate.parse("2026-09-02"), new BigDecimal("180.00")));
		assertThat(trend.previousPeriod().data().get(0))
			.isEqualTo(new Point(LocalDate.parse("2026-08-25"), new BigDecimal("90.00")));
		assertThat(trend.previousPeriod().data().get(6))
			.isEqualTo(new Point(LocalDate.parse("2026-08-31"), new BigDecimal("0.00")));
	}

}
