package com.merchant.awinMerchant.revenuetrend.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.revenuetrend.dto.Granularity;
import com.merchant.awinMerchant.revenuetrend.dto.RevenueTrendResponse;
import com.merchant.awinMerchant.revenuetrend.service.RevenueTrendService;

/** Merchant ownership of {@code merchantId} is enforced by the security filter chain before this runs. */
@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/dashboard")
public class RevenueTrendController {

	private final RevenueTrendService revenueTrendService;

	public RevenueTrendController(RevenueTrendService revenueTrendService) {
		this.revenueTrendService = revenueTrendService;
	}

	/** {@code granularity} is optional; {@code DAY} is the only accepted (and default) value. */
	@GetMapping("/revenue-trend")
	public RevenueTrendResponse getRevenueTrend(@PathVariable long merchantId,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "DAY") Granularity granularity) {
		return revenueTrendService.getDailyTrend(merchantId, new ReportingPeriod(from, to));
	}

}
