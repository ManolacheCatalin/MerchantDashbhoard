package com.merchant.awinMerchant.summary.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Percentage change from a previous value to a current one, rounded to two decimals (HALF_UP). */
public final class PercentageChange {

	static final int SCALE = 2;

	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

	private PercentageChange() {
	}

	/**
	 * Returns {@code (current - previous) / previous * 100}, or {@code null} when {@code previous} is zero (the change
	 * is undefined, including when both values are zero).
	 */
	public static BigDecimal between(BigDecimal current, BigDecimal previous) {
		//part of the contract to return null in case of zeros
		if (previous.signum() == 0) {
			return null;
		}
		return current.subtract(previous).multiply(ONE_HUNDRED).divide(previous, SCALE, RoundingMode.HALF_UP);
	}

	public static BigDecimal between(long current, long previous) {
		return between(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
	}

}
