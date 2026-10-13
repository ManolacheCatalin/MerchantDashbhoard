package com.merchant.awinMerchant.summary.entity;

import java.math.BigDecimal;

/** Approved-transaction aggregates for one merchant and period. */
public record TransactionTotals(BigDecimal revenue, long transactionCount) {
}
