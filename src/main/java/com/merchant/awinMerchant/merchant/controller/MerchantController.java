package com.merchant.awinMerchant.merchant.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.merchant.awinMerchant.merchant.dto.MerchantListResponse;
import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.security.AuthenticatedMerchant;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

	private final MerchantService merchantService;

	public MerchantController(MerchantService merchantService) {
		this.merchantService = merchantService;
	}

	@GetMapping
	public MerchantListResponse listMerchants(@AuthenticationPrincipal AuthenticatedMerchant merchant) {
		return MerchantListResponse.of(merchantService.getMerchant(merchant.merchantId()));
	}

}
