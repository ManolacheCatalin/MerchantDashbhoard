package com.merchant.awinMerchant.summary.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PercentageChangeTest {

	@Test
	void increase() {
		assertThat(PercentageChange.between(new BigDecimal("150.00"), new BigDecimal("100.00")))
			.isEqualTo(new BigDecimal("50.00"));
	}

	@Test
	void decrease() {
		assertThat(PercentageChange.between(new BigDecimal("75.00"), new BigDecimal("100.00")))
			.isEqualTo(new BigDecimal("-25.00"));
	}

	@Test
	void noChangeIsZeroWithTwoDecimals() {
		assertThat(PercentageChange.between(new BigDecimal("42.50"), new BigDecimal("42.50")))
			.isEqualTo(new BigDecimal("0.00"));
	}

	@Test
	void dropToZeroIsMinusOneHundred() {
		assertThat(PercentageChange.between(BigDecimal.ZERO, new BigDecimal("80.00")))
			.isEqualTo(new BigDecimal("-100.00"));
	}

	@Test
	void previousZeroIsNull() {
		assertThat(PercentageChange.between(new BigDecimal("10.00"), new BigDecimal("0.00"))).isNull();
	}

	@Test
	void bothZeroIsNull() {
		assertThat(PercentageChange.between(new BigDecimal("0.00"), BigDecimal.ZERO)).isNull();
		assertThat(PercentageChange.between(0, 0)).isNull();
	}

	@Test
	void roundsHalfUpToTwoDecimals() {
		assertThat(PercentageChange.between(1, 3)).isEqualTo(new BigDecimal("-66.67")); // -66.666...
		assertThat(PercentageChange.between(4, 3)).isEqualTo(new BigDecimal("33.33")); // 33.333...
		assertThat(PercentageChange.between(new BigDecimal("100.005"), new BigDecimal("100")))
			.isEqualTo(new BigDecimal("0.01")); // exactly 0.005 rounds up
		assertThat(PercentageChange.between(new BigDecimal("99.995"), new BigDecimal("100")))
			.isEqualTo(new BigDecimal("-0.01")); // exactly -0.005 rounds away from zero
	}

	@Test
	void countsUseExactArithmetic() {
		assertThat(PercentageChange.between(1248, 1153)).isEqualTo(new BigDecimal("8.24"));
		assertThat(PercentageChange.between(63400, 65300)).isEqualTo(new BigDecimal("-2.91"));
	}

}
