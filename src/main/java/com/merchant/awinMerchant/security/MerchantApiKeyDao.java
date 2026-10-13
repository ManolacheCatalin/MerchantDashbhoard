package com.merchant.awinMerchant.security;

import java.util.Optional;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface MerchantApiKeyDao {

	@SqlQuery("SELECT merchant_id FROM merchant_api_keys WHERE key_hash = :keyHash AND active")
	Optional<Long> findActiveMerchantIdByKeyHash(@Bind("keyHash") String keyHash);

}
