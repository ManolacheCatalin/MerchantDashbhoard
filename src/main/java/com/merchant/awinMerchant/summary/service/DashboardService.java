package com.merchant.awinMerchant.summary.service;

import org.springframework.stereotype.Service;

import com.merchant.awinMerchant.merchant.entity.Merchant;
import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.summary.dao.ClickDao;
import com.merchant.awinMerchant.summary.dao.SummaryTransactionDao;
import com.merchant.awinMerchant.summary.dto.DashboardSummaryResponse.Metric;
import com.merchant.awinMerchant.summary.dto.DashboardSummaryResponse.Period;
import com.merchant.awinMerchant.summary.dto.DashboardSummaryResponse;
import com.merchant.awinMerchant.summary.entity.TransactionTotals;

@Service
public class DashboardService {

	private final MerchantService merchantService;

	private final SummaryTransactionDao transactionDao;

	private final ClickDao clickDao;

	public DashboardService(MerchantService merchantService, SummaryTransactionDao transactionDao, ClickDao clickDao) {
		this.merchantService = merchantService;
		this.transactionDao = transactionDao;
		this.clickDao = clickDao;
	}

	/**
	 * Revenue, approved-transaction and click totals for {@code period} and the equally long period just before it.
	 * Transactions and clicks are aggregated by separate queries, so no join can double-count.
	 */
	public DashboardSummaryResponse getSummary(long merchantId, ReportingPeriod period) {
		Merchant merchant = merchantService.getMerchant(merchantId);
		ReportingPeriod previous = period.previous();

		TransactionTotals currentTransactions = transactionDao.sumApproved(merchantId, period.startInclusive(),
				period.endExclusive());
		TransactionTotals previousTransactions = transactionDao.sumApproved(merchantId, previous.startInclusive(),
				previous.endExclusive());
		long currentClicks = clickDao.count(merchantId, period.startInclusive(), period.endExclusive());
		long previousClicks = clickDao.count(merchantId, previous.startInclusive(), previous.endExclusive());

		return new DashboardSummaryResponse(merchantId, new Period(period.from(), period.to()),
				merchant.reportingCurrency(),
				Metric.ofAmounts(currentTransactions.revenue(), previousTransactions.revenue()),
				Metric.ofCounts(currentTransactions.transactionCount(), previousTransactions.transactionCount()),
				Metric.ofCounts(currentClicks, previousClicks));
	}

}
