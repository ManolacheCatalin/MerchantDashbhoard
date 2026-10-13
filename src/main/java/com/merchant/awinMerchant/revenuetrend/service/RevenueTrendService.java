package com.merchant.awinMerchant.revenuetrend.service;

import org.springframework.stereotype.Service;

import com.merchant.awinMerchant.merchant.entity.Merchant;
import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.revenuetrend.dao.TrendTransactionDao;
import com.merchant.awinMerchant.revenuetrend.dto.Granularity;
import com.merchant.awinMerchant.revenuetrend.dto.RevenueTrendResponse.PeriodSeries;
import com.merchant.awinMerchant.revenuetrend.dto.RevenueTrendResponse;
import com.merchant.awinMerchant.revenuetrend.util.DailyRevenueSeries;

@Service
public class RevenueTrendService {

	private final MerchantService merchantService;

	private final TrendTransactionDao transactionDao;

	public RevenueTrendService(MerchantService merchantService, TrendTransactionDao transactionDao) {
		this.merchantService = merchantService;
		this.transactionDao = transactionDao;
	}

	/**
	 * Daily approved revenue for {@code period} and the equally long period just before it. PostgreSQL aggregates per
	 * UTC day; days without approved transactions are filled with {@code 0.00} here.
	 */
	public RevenueTrendResponse getDailyTrend(long merchantId, ReportingPeriod period) {
		Merchant merchant = merchantService.getMerchant(merchantId);
		ReportingPeriod previous = period.previous();

		var current = DailyRevenueSeries.zeroFilled(period,
				transactionDao.sumApprovedByUtcDay(merchantId, period.startInclusive(), period.endExclusive()));
		var before = DailyRevenueSeries.zeroFilled(previous,
				transactionDao.sumApprovedByUtcDay(merchantId, previous.startInclusive(), previous.endExclusive()));

		return new RevenueTrendResponse(merchantId, merchant.reportingCurrency(), Granularity.DAY,
				PeriodSeries.of(period, current), PeriodSeries.of(previous, before));
	}

}
