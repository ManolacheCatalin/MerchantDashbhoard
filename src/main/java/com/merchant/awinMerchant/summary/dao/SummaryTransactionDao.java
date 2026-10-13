package com.merchant.awinMerchant.summary.dao;

import java.time.OffsetDateTime;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import com.merchant.awinMerchant.summary.entity.TransactionTotals;

public interface SummaryTransactionDao {

	/**
	 * Sums and counts APPROVED transactions with {@code occurred_at} in {@code [from, to)}. Always returns one row:
	 * revenue {@code 0.00} and count {@code 0} when nothing matches.
	 */
	@SqlQuery("""
			SELECT COALESCE(SUM(amount), 0.00) AS revenue,
			       COUNT(*)                    AS transaction_count
			FROM transactions
			WHERE merchant_id = :merchantId
			  AND status = 'APPROVED'
			  AND occurred_at >= :from
			  AND occurred_at < :to
			""")
	@RegisterConstructorMapper(TransactionTotals.class)
	TransactionTotals sumApproved(@Bind("merchantId") long merchantId, @Bind("from") OffsetDateTime from,
			@Bind("to") OffsetDateTime to);

}
