package com.merchant.awinMerchant.config;

import javax.sql.DataSource;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import com.merchant.awinMerchant.merchant.dao.MerchantDao;
import com.merchant.awinMerchant.revenuetrend.dao.TrendTransactionDao;
import com.merchant.awinMerchant.security.MerchantApiKeyDao;
import com.merchant.awinMerchant.summary.dao.ClickDao;
import com.merchant.awinMerchant.summary.dao.SummaryTransactionDao;
import com.merchant.awinMerchant.todo.dao.TodoDao;

@Configuration
public class JdbiConfig {

	@Bean
	Jdbi jdbi(DataSource dataSource) {
		// The proxy lets JDBI handles reuse the connection of a Spring-managed transaction when one is active.
		// Not yet relied on: no @Transactional code exists, and the integration is unverified by tests.
		//On multiple operations under same method needs explicit @Transactional declaration
		return Jdbi.create(new TransactionAwareDataSourceProxy(dataSource))
				.installPlugin(new SqlObjectPlugin())
				.installPlugin(new PostgresPlugin());
	}

	@Bean
	MerchantDao merchantDao(Jdbi jdbi) {
		return jdbi.onDemand(MerchantDao.class);
	}

	@Bean
	MerchantApiKeyDao merchantApiKeyDao(Jdbi jdbi) {
		return jdbi.onDemand(MerchantApiKeyDao.class);
	}

	@Bean
	SummaryTransactionDao summaryTransactionDao(Jdbi jdbi) {
		return jdbi.onDemand(SummaryTransactionDao.class);
	}

	@Bean
	ClickDao clickDao(Jdbi jdbi) {
		return jdbi.onDemand(ClickDao.class);
	}

	@Bean
	TrendTransactionDao trendTransactionDao(Jdbi jdbi) {
		return jdbi.onDemand(TrendTransactionDao.class);
	}

	@Bean
	TodoDao todoDao(Jdbi jdbi) {
		return jdbi.onDemand(TodoDao.class);
	}

}
