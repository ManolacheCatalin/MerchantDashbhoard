package com.merchant.awinMerchant.revenuetrend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Approved revenue for one UTC calendar day. Used both for aggregated rows and for the zero-filled response. */
public record DailyRevenue(LocalDate revenueDate, BigDecimal revenue) {
}
