package com.merchant.awinMerchant.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

	@Test
	void producesLowerCaseHexSha256() {
		// FIPS 180-2 test vector for "abc"
		assertThat(ApiKeyHasher.sha256Hex("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void outputMatchesStoredKeyHashFormat() {
		assertThat(ApiKeyHasher.sha256Hex("any-key")).matches("^[0-9a-f]{64}$");
	}

}
