package com.merchant.awinMerchant.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes API keys to the lower-case hex SHA-256 stored in {@code merchant_api_keys.key_hash}.
 * A fast unsalted hash is acceptable only because keys are high-entropy random values, never human passwords.
 */
public final class ApiKeyHasher {

	private ApiKeyHasher() {
	}

	public static String sha256Hex(String apiKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

}
