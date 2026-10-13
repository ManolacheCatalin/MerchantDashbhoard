package com.merchant.awinMerchant.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.merchant.awinMerchant.merchant.entity.Merchant;
import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.summary.dao.ClickDao;
import com.merchant.awinMerchant.summary.dao.SummaryTransactionDao;
import com.merchant.awinMerchant.summary.entity.TransactionTotals;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	private static final OffsetDateTime PREVIOUS_START = OffsetDateTime.parse("2026-08-25T00:00:00Z");

	private static final OffsetDateTime CURRENT_START = OffsetDateTime.parse("2026-09-01T00:00:00Z");

	private static final OffsetDateTime CURRENT_END = OffsetDateTime.parse("2026-09-08T00:00:00Z");

	@Mock
	private MerchantService merchantService;

	@Mock
	private SummaryTransactionDao transactionDao;

	@Mock
	private ClickDao clickDao;

	@Test
	void queriesCurrentAndPreviousWindowsAndBuildsComparisons() {
		when(merchantService.getMerchant(1)).thenReturn(new Merchant(1, "Merchant A", "GBP"));
		// Strict stubs: the test fails if the service queries any other window.
		when(transactionDao.sumApproved(1, CURRENT_START, CURRENT_END))
			.thenReturn(new TransactionTotals(new BigDecimal("150.00"), 3));
		when(transactionDao.sumApproved(1, PREVIOUS_START, CURRENT_START))
			.thenReturn(new TransactionTotals(new BigDecimal("100.00"), 4));
		when(clickDao.count(1, CURRENT_START, CURRENT_END)).thenReturn(20L);
		when(clickDao.count(1, PREVIOUS_START, CURRENT_START)).thenReturn(0L);

		var service = new DashboardService(merchantService, transactionDao, clickDao);
		var summary = service.getSummary(1,
				new ReportingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-08")));

		assertThat(summary.merchantId()).isEqualTo(1);
		assertThat(summary.currency()).isEqualTo("GBP");
		assertThat(summary.period().from()).isEqualTo(LocalDate.parse("2026-09-01"));
		assertThat(summary.period().to()).isEqualTo(LocalDate.parse("2026-09-08"));

		assertThat(summary.revenue().current()).isEqualTo(new BigDecimal("150.00"));
		assertThat(summary.revenue().previous()).isEqualTo(new BigDecimal("100.00"));
		assertThat(summary.revenue().changePercentage()).isEqualTo(new BigDecimal("50.00"));

		assertThat(summary.transactions().current()).isEqualTo(3L);
		assertThat(summary.transactions().previous()).isEqualTo(4L);
		assertThat(summary.transactions().changePercentage()).isEqualTo(new BigDecimal("-25.00"));

		assertThat(summary.clicks().current()).isEqualTo(20L);
		assertThat(summary.clicks().previous()).isEqualTo(0L);
		assertThat(summary.clicks().changePercentage()).isNull();
	}

}
