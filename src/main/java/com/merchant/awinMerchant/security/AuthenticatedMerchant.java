package com.merchant.awinMerchant.security;

/** Security principal resolved from a valid API key. Never carries the key itself. */
public record AuthenticatedMerchant(long merchantId) {
}
