package com.merchant.awinMerchant.revenuetrend.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import com.merchant.awinMerchant.revenuetrend.entity.DailyRevenue;

public interface TrendTransactionDao {

	/**
	 * Sums APPROVED transaction amounts per UTC calendar day of {@code occurred_at}, for {@code occurred_at} in
	 * {@code [from, to)}, ordered by day. Days without approved transactions are absent; callers fill them.
	 */
	@SqlQuery("""
			SELECT (occurred_at AT TIME ZONE 'UTC')::date AS revenue_date,
			       SUM(amount)                            AS revenue
			FROM transactions
			WHERE merchant_id = :merchantId
			  AND status = 'APPROVED'
			  AND occurred_at >= :from
			  AND occurred_at < :to
			GROUP BY revenue_date
			ORDER BY revenue_date
			""")
	@RegisterConstructorMapper(DailyRevenue.class)
	List<DailyRevenue> sumApprovedByUtcDay(@Bind("merchantId") long merchantId, @Bind("from") OffsetDateTime from,
			@Bind("to") OffsetDateTime to);

}
