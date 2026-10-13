package com.merchant.awinMerchant.summary.dao;

import java.time.OffsetDateTime;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface ClickDao {

	/** Counts click rows with {@code occurred_at} in {@code [from, to)}; every row counts once. */
	@SqlQuery("""
			SELECT COUNT(*)
			FROM clicks
			WHERE merchant_id = :merchantId
			  AND occurred_at >= :from
			  AND occurred_at < :to
			""")
	long count(@Bind("merchantId") long merchantId, @Bind("from") OffsetDateTime from, @Bind("to") OffsetDateTime to);

}
