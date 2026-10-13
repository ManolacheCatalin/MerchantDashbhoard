package com.merchant.awinMerchant.merchant.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import com.merchant.awinMerchant.merchant.entity.Merchant;

@RegisterConstructorMapper(Merchant.class)
public interface MerchantDao {

	@SqlQuery("SELECT id, name, reporting_currency FROM merchants WHERE id = :id")
	Optional<Merchant> findById(@Bind("id") long id);

}
