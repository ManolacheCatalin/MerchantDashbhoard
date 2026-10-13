package com.merchant.awinMerchant.merchant.service;

/** No merchant with the given id. Mapped to a 404 ProblemDetail with a fixed text; the id is not echoed. */
public class MerchantNotFoundException extends RuntimeException {

	public MerchantNotFoundException(long merchantId) {
		super("Merchant " + merchantId + " was not found");
	}

}
