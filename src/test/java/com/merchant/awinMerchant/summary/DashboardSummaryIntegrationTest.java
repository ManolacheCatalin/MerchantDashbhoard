package com.merchant.awinMerchant.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.merchant.awinMerchant.security.ApiKeyAuthenticationFilter;
import com.merchant.awinMerchant.support.TestDatabase;

/**
 * Real SQL aggregation against awin_test. Current period [2026-09-01, 2026-09-08), previous [2026-08-25, 2026-09-01).
 * Expected values are computed by hand from the fixtures below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardSummaryIntegrationTest {

	private static final String HEADER = ApiKeyAuthenticationFilter.API_KEY_HEADER;

	private static final String SUMMARY = "/api/v1/merchants/{merchantId}/dashboard/summary";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Jdbi jdbi;

	private String merchantAKey;

	private String merchantBKey;

	private String merchantCKey;

	@BeforeEach
	void setUpFixtures() {
		TestDatabase db = new TestDatabase(jdbi);
		db.reset();
		db.insertMerchant(1, "Merchant A", "GBP");
		db.insertMerchant(2, "Merchant B", "EUR");
		db.insertMerchant(3, "Merchant C", "GBP"); // no activity at all
		merchantAKey = db.insertApiKey(1, true);
		merchantBKey = db.insertApiKey(2, true);
		merchantCKey = db.insertApiKey(3, true);

		// Merchant A, current period: approved 100.00 + 0.10 + 50.25 = 150.35 (3 transactions)
		db.insertTransaction(1, "100.00", "APPROVED", "2026-09-01T00:00:00Z"); // inclusive start
		db.insertTransaction(1, "0.10", "APPROVED", "2026-09-03T12:00:00Z");
		db.insertTransaction(1, "50.25", "APPROVED", "2026-09-07T23:59:59.999999Z"); // last microsecond
		db.insertTransaction(1, "999.00", "APPROVED", "2026-09-08T00:00:00Z"); // exclusive end: not counted
		db.insertTransaction(1, "10.00", "PENDING", "2026-09-04T10:00:00Z"); // non-approved: not counted
		db.insertTransaction(1, "20.00", "DECLINED", "2026-09-04T11:00:00Z");
		db.insertTransaction(1, "30.00", "REFUNDED", "2026-09-04T12:00:00Z");

		// Merchant A, previous period: approved 200.00 + 75.50 + 24.50 = 300.00 (3 transactions)
		db.insertTransaction(1, "200.00", "APPROVED", "2026-08-25T00:00:00Z"); // previous inclusive start
		db.insertTransaction(1, "75.50", "APPROVED", "2026-08-31T23:59:59Z");
		// 01:30 at +03:00 on 1 Sep is 22:30 UTC on 31 Aug: belongs to the previous period
		db.insertTransaction(1, "24.50", "APPROVED", "2026-09-01T01:30:00+03:00");
		db.insertTransaction(1, "500.00", "APPROVED", "2026-08-24T23:59:59Z"); // before previous period

		// Merchant A clicks: 3 in current, none in previous
		db.insertClick(1, "2026-09-01T00:00:00Z");
		db.insertClick(1, "2026-09-05T10:00:00Z");
		db.insertClick(1, "2026-09-07T23:59:59Z");
		db.insertClick(1, "2026-09-08T00:00:00Z"); // exclusive end
		db.insertClick(1, "2026-08-24T12:00:00Z"); // before previous period

		// Merchant B, same windows: must never leak into Merchant A's figures
		db.insertTransaction(2, "1000.00", "APPROVED", "2026-09-02T10:00:00Z");
		db.insertTransaction(2, "400.00", "APPROVED", "2026-08-28T10:00:00Z");
		for (int i = 0; i < 5; i++) {
			db.insertClick(2, "2026-09-02T1" + i + ":00:00Z");
		}
		for (int i = 0; i < 4; i++) {
			db.insertClick(2, "2026-08-27T1" + i + ":00:00Z");
		}
	}

	@Test
	void aggregatesApprovedTransactionsAndClicksWithinHalfOpenUtcWindows() throws Exception {
		summary(1, merchantAKey, "2026-09-01", "2026-09-08").andExpect(status().isOk())
			.andExpect(jsonPath("$.merchantId").value(1))
			.andExpect(jsonPath("$.period.from").value("2026-09-01"))
			.andExpect(jsonPath("$.period.to").value("2026-09-08"))
			.andExpect(jsonPath("$.currency").value("GBP"))
			.andExpect(jsonPath("$.revenue.current").value(150.35))
			.andExpect(jsonPath("$.revenue.previous").value(300.00))
			.andExpect(jsonPath("$.revenue.changePercentage").value(-49.88)) // -149.65 / 300 = -49.883...
			.andExpect(jsonPath("$.transactions.current").value(3))
			.andExpect(jsonPath("$.transactions.previous").value(3))
			.andExpect(jsonPath("$.transactions.changePercentage").value(0.00))
			.andExpect(jsonPath("$.clicks.current").value(3))
			.andExpect(jsonPath("$.clicks.previous").value(0));
	}

	@Test
	void serializesMoneyAsTwoDecimalNumbersAndUndefinedChangeAsNull() throws Exception {
		String body = summary(1, merchantAKey, "2026-09-01", "2026-09-08").andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains("\"revenue\":{\"current\":150.35,\"previous\":300.00,\"changePercentage\":-49.88}")
			.contains("\"transactions\":{\"current\":3,\"previous\":3,\"changePercentage\":0.00}")
			.contains("\"clicks\":{\"current\":3,\"previous\":0,\"changePercentage\":null}");
	}

	@Test
	void eachMerchantSeesOnlyItsOwnData() throws Exception {
		summary(2, merchantBKey, "2026-09-01", "2026-09-08").andExpect(status().isOk())
			.andExpect(jsonPath("$.merchantId").value(2))
			.andExpect(jsonPath("$.currency").value("EUR"))
			.andExpect(jsonPath("$.revenue.current").value(1000.00))
			.andExpect(jsonPath("$.revenue.previous").value(400.00))
			.andExpect(jsonPath("$.revenue.changePercentage").value(150.00))
			.andExpect(jsonPath("$.transactions.current").value(1))
			.andExpect(jsonPath("$.transactions.previous").value(1))
			.andExpect(jsonPath("$.clicks.current").value(5))
			.andExpect(jsonPath("$.clicks.previous").value(4))
			.andExpect(jsonPath("$.clicks.changePercentage").value(25.00));
	}

	@Test
	void merchantWithoutActivityGetsZerosAndNullChanges() throws Exception {
		String body = summary(3, merchantCKey, "2026-09-01", "2026-09-08").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains("\"revenue\":{\"current\":0.00,\"previous\":0.00,\"changePercentage\":null}")
			.contains("\"transactions\":{\"current\":0,\"previous\":0,\"changePercentage\":null}")
			.contains("\"clicks\":{\"current\":0,\"previous\":0,\"changePercentage\":null}");
	}

	@Test
	void periodWithoutRecordsReturnsZeros() throws Exception {
		summary(1, merchantAKey, "2025-01-01", "2025-02-01").andExpect(status().isOk())
			.andExpect(jsonPath("$.revenue.current").value(0.00))
			.andExpect(jsonPath("$.transactions.current").value(0))
			.andExpect(jsonPath("$.clicks.current").value(0));
	}

	@Test
	void anotherMerchantsSummaryIsForbidden() throws Exception {
		summary(2, merchantAKey, "2026-09-01", "2026-09-08").andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void missingApiKeyIsUnauthorized() throws Exception {
		mockMvc.perform(get(SUMMARY, 1).param("from", "2026-09-01").param("to", "2026-09-08"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void missingDateParameterIsBadRequest() throws Exception {
		mockMvc.perform(get(SUMMARY, 1).header(HEADER, merchantAKey).param("from", "2026-09-01"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void malformedDateIsBadRequest() throws Exception {
		summary(1, merchantAKey, "2026-13-01", "2026-09-08").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		summary(1, merchantAKey, "01/09/2026", "2026-09-08").andExpect(status().isBadRequest());
	}

	@Test
	void emptyOrReversedPeriodIsBadRequest() throws Exception {
		summary(1, merchantAKey, "2026-09-01", "2026-09-01").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("'from' must be before 'to'."));
		summary(1, merchantAKey, "2026-09-08", "2026-09-01").andExpect(status().isBadRequest());
	}

	private ResultActions summary(long merchantId, String apiKey, String from, String to) throws Exception {
		return mockMvc.perform(get(SUMMARY, merchantId).header(HEADER, apiKey).param("from", from).param("to", to));
	}

}
