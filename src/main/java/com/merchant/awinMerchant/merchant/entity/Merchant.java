package com.merchant.awinMerchant.merchant.entity;

/** One row of {@code merchants}. All of the merchant's transaction amounts are in {@code reportingCurrency}. */
public record Merchant(long id, String name, String reportingCurrency) {
}
