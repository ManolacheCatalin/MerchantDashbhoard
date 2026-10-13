package com.merchant.awinMerchant.summary.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.merchant.awinMerchant.reporting.util.ReportingPeriod;
import com.merchant.awinMerchant.summary.dto.DashboardSummaryResponse;
import com.merchant.awinMerchant.summary.service.DashboardService;

/** Merchant ownership of {@code merchantId} is enforced by the security filter chain before this runs. */
@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/dashboard")
public class DashboardSummaryController {

	private final DashboardService dashboardService;

	public DashboardSummaryController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/summary")
	public DashboardSummaryResponse getSummary(@PathVariable long merchantId,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
		return dashboardService.getSummary(merchantId, new ReportingPeriod(from, to));
	}

}
