package com.merchant.awinMerchant.revenuetrend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

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

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real SQL aggregation against awin_test. Current period [2026-09-01, 2026-09-08), previous [2026-08-25, 2026-09-01).
 * Expected daily values are computed by hand from the fixtures below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevenueTrendIntegrationTest {

	private static final String HEADER = ApiKeyAuthenticationFilter.API_KEY_HEADER;

	private static final String TREND = "/api/v1/merchants/{merchantId}/dashboard/revenue-trend";

	private static final JsonMapper EXACT_DECIMALS = JsonMapper.builder()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

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

		// Merchant A, current period
		db.insertTransaction(1, "100.00", "APPROVED", "2026-09-01T00:00:00Z"); // inclusive start
		db.insertTransaction(1, "20.00", "APPROVED", "2026-09-01T23:59:59Z"); // same UTC day: 09-01 = 120.00
		db.insertTransaction(1, "180.00", "APPROVED", "2026-09-02T10:00:00Z"); // 09-02 = 180.00
		db.insertTransaction(1, "50.00", "PENDING", "2026-09-02T11:00:00Z"); // non-approved: not counted
		// 09-03: no transactions at all -> 0.00
		db.insertTransaction(1, "30.00", "REFUNDED", "2026-09-04T12:00:00Z"); // not counted
		db.insertTransaction(1, "40.00", "DECLINED", "2026-09-04T13:00:00Z"); // not counted
		// 01:30 at +03:00 on 5 Sep is 22:30 UTC on 4 Sep: bucketed into 09-04 = 15.50
		db.insertTransaction(1, "15.50", "APPROVED", "2026-09-05T01:30:00+03:00");
		// 09-05, 09-06: nothing -> 0.00
		db.insertTransaction(1, "60.25", "APPROVED", "2026-09-07T23:59:59.999999Z"); // 09-07 = 60.25
		db.insertTransaction(1, "999.00", "APPROVED", "2026-09-08T00:00:00Z"); // exclusive end: not counted

		// Merchant A, previous period
		db.insertTransaction(1, "500.00", "APPROVED", "2026-08-24T23:59:59Z"); // before previous period
		db.insertTransaction(1, "90.00", "APPROVED", "2026-08-25T00:00:00Z"); // 08-25 = 90.00
		db.insertTransaction(1, "140.00", "APPROVED", "2026-08-26T09:00:00Z"); // 08-26 = 140.00
		db.insertTransaction(1, "110.00", "APPROVED", "2026-08-27T09:00:00Z"); // 08-27 = 110.00
		// 01:30 at +03:00 on 1 Sep is 22:30 UTC on 31 Aug: previous period, 08-31 = 24.50
		db.insertTransaction(1, "24.50", "APPROVED", "2026-09-01T01:30:00+03:00");

		// Merchant B, same windows: must never leak into Merchant A's series
		db.insertTransaction(2, "1000.00", "APPROVED", "2026-09-02T10:00:00Z");
		db.insertTransaction(2, "400.00", "APPROVED", "2026-08-28T10:00:00Z");
	}

	@Test
	void returnsDailyApprovedRevenueWithZeroFilledDaysForBothPeriods() throws Exception {
		String body = trend(1, merchantAKey, "2026-09-01", "2026-09-08").andExpect(status().isOk())
			.andExpect(jsonPath("$.merchantId").value(1))
			.andExpect(jsonPath("$.currency").value("GBP"))
			.andExpect(jsonPath("$.granularity").value("DAY"))
			.andExpect(jsonPath("$.currentPeriod.from").value("2026-09-01"))
			.andExpect(jsonPath("$.currentPeriod.to").value("2026-09-08"))
			.andExpect(jsonPath("$.currentPeriod.data", hasSize(7)))
			.andExpect(jsonPath("$.previousPeriod.from").value("2026-08-25"))
			.andExpect(jsonPath("$.previousPeriod.to").value("2026-09-01"))
			.andExpect(jsonPath("$.previousPeriod.data", hasSize(7)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains("\"currentPeriod\":{\"from\":\"2026-09-01\",\"to\":\"2026-09-08\",\"data\":["
				+ "{\"date\":\"2026-09-01\",\"revenue\":120.00},"
				+ "{\"date\":\"2026-09-02\",\"revenue\":180.00},"
				+ "{\"date\":\"2026-09-03\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-09-04\",\"revenue\":15.50},"
				+ "{\"date\":\"2026-09-05\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-09-06\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-09-07\",\"revenue\":60.25}]}");
		assertThat(body).contains("\"previousPeriod\":{\"from\":\"2026-08-25\",\"to\":\"2026-09-01\",\"data\":["
				+ "{\"date\":\"2026-08-25\",\"revenue\":90.00},"
				+ "{\"date\":\"2026-08-26\",\"revenue\":140.00},"
				+ "{\"date\":\"2026-08-27\",\"revenue\":110.00},"
				+ "{\"date\":\"2026-08-28\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-08-29\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-08-30\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-08-31\",\"revenue\":24.50}]}");
	}

	/**
	 * The chart and the summary card use different SQL (daily GROUP BY vs one SUM); for the same merchant and range,
	 * the trend's daily values must add up exactly to the summary's revenue in both periods.
	 */
	@Test
	void dailyTrendAddsUpToSummaryRevenueForBothPeriods() throws Exception {
		record Case(long merchantId, String apiKey, String current, String previous) {
		}
		var cases = List.of(new Case(1, merchantAKey, "375.75", "364.50"), // 120+180+15.50+60.25 / 90+140+110+24.50
				new Case(2, merchantBKey, "1000.00", "400.00"), new Case(3, merchantCKey, "0.00", "0.00"));

		for (Case c : cases) {
			JsonNode trend = json(trend(c.merchantId(), c.apiKey(), "2026-09-01", "2026-09-08"));
			JsonNode summary = json(mockMvc.perform(get("/api/v1/merchants/{merchantId}/dashboard/summary",
					c.merchantId()).header(HEADER, c.apiKey()).param("from", "2026-09-01").param("to", "2026-09-08")));

			BigDecimal trendCurrent = sumRevenue(trend.get("currentPeriod").get("data"));
			BigDecimal trendPrevious = sumRevenue(trend.get("previousPeriod").get("data"));

			assertThat(trendCurrent).as("merchant %d current", c.merchantId())
				.isEqualTo(summary.get("revenue").get("current").decimalValue())
				.isEqualTo(new BigDecimal(c.current()));
			assertThat(trendPrevious).as("merchant %d previous", c.merchantId())
				.isEqualTo(summary.get("revenue").get("previous").decimalValue())
				.isEqualTo(new BigDecimal(c.previous()));
		}
	}

	@Test
	void eachMerchantSeesOnlyItsOwnRevenue() throws Exception {
		trend(2, merchantBKey, "2026-09-01", "2026-09-08").andExpect(status().isOk())
			.andExpect(jsonPath("$.merchantId").value(2))
			.andExpect(jsonPath("$.currency").value("EUR"))
			.andExpect(jsonPath("$.currentPeriod.data[0].revenue").value(0.00))
			.andExpect(jsonPath("$.currentPeriod.data[1].date").value("2026-09-02"))
			.andExpect(jsonPath("$.currentPeriod.data[1].revenue").value(1000.00))
			.andExpect(jsonPath("$.previousPeriod.data[3].date").value("2026-08-28"))
			.andExpect(jsonPath("$.previousPeriod.data[3].revenue").value(400.00))
			.andExpect(jsonPath("$.previousPeriod.data[0].revenue").value(0.00));
	}

	@Test
	void merchantWithoutTransactionsGetsZeroForEveryDay() throws Exception {
		String body = trend(3, merchantCKey, "2026-09-01", "2026-09-04").andExpect(status().isOk())
			.andExpect(jsonPath("$.currentPeriod.data", hasSize(3)))
			.andExpect(jsonPath("$.previousPeriod.data", hasSize(3)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains("\"data\":[{\"date\":\"2026-09-01\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-09-02\",\"revenue\":0.00},{\"date\":\"2026-09-03\",\"revenue\":0.00}]");
		assertThat(body).contains("\"data\":[{\"date\":\"2026-08-29\",\"revenue\":0.00},"
				+ "{\"date\":\"2026-08-30\",\"revenue\":0.00},{\"date\":\"2026-08-31\",\"revenue\":0.00}]");
	}

	@Test
	void periodAcrossMonthBoundaryIsContinuous() throws Exception {
		trend(1, merchantAKey, "2026-08-30", "2026-09-03").andExpect(status().isOk())
			.andExpect(jsonPath("$.currentPeriod.data", hasSize(4)))
			.andExpect(jsonPath("$.currentPeriod.data[0].date").value("2026-08-30"))
			.andExpect(jsonPath("$.currentPeriod.data[1].date").value("2026-08-31"))
			.andExpect(jsonPath("$.currentPeriod.data[1].revenue").value(24.50))
			.andExpect(jsonPath("$.currentPeriod.data[2].date").value("2026-09-01"))
			.andExpect(jsonPath("$.currentPeriod.data[2].revenue").value(120.00))
			.andExpect(jsonPath("$.currentPeriod.data[3].date").value("2026-09-02"))
			.andExpect(jsonPath("$.previousPeriod.from").value("2026-08-26"))
			.andExpect(jsonPath("$.previousPeriod.data[0].revenue").value(140.00)) // 08-26
			.andExpect(jsonPath("$.previousPeriod.data[1].revenue").value(110.00)); // 08-27
	}

	@Test
	void explicitDayGranularityIsAccepted() throws Exception {
		trend(1, merchantAKey, "2026-09-01", "2026-09-08").andExpect(status().isOk());
		mockMvc
			.perform(get(TREND, 1).header(HEADER, merchantAKey)
				.param("from", "2026-09-01")
				.param("to", "2026-09-08")
				.param("granularity", "DAY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.granularity").value("DAY"));
	}

	@Test
	void unsupportedGranularityIsBadRequest() throws Exception {
		for (String granularity : new String[] { "WEEK", "MONTH", "HOUR", "day" }) {
			mockMvc
				.perform(get(TREND, 1).header(HEADER, merchantAKey)
					.param("from", "2026-09-01")
					.param("to", "2026-09-08")
					.param("granularity", granularity))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void anotherMerchantsTrendIsForbidden() throws Exception {
		trend(2, merchantAKey, "2026-09-01", "2026-09-08").andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void missingApiKeyIsUnauthorized() throws Exception {
		mockMvc.perform(get(TREND, 1).param("from", "2026-09-01").param("to", "2026-09-08"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void missingOrMalformedDatesAreBadRequest() throws Exception {
		mockMvc.perform(get(TREND, 1).header(HEADER, merchantAKey).param("from", "2026-09-01"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		trend(1, merchantAKey, "2026-13-01", "2026-09-08").andExpect(status().isBadRequest());
	}

	@Test
	void emptyOrReversedPeriodIsBadRequest() throws Exception {
		trend(1, merchantAKey, "2026-09-01", "2026-09-01").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("'from' must be before 'to'."));
		trend(1, merchantAKey, "2026-09-08", "2026-09-01").andExpect(status().isBadRequest());
	}

	/** Parses decimals as BigDecimal (not double) so money compares exactly, including scale. */
	private static JsonNode json(ResultActions result) throws Exception {
		String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return EXACT_DECIMALS.readTree(body);
	}

	private static BigDecimal sumRevenue(JsonNode days) {
		BigDecimal total = new BigDecimal("0.00");
		for (JsonNode day : days) {
			total = total.add(day.get("revenue").decimalValue());
		}
		return total;
	}

	private ResultActions trend(long merchantId, String apiKey, String from, String to) throws Exception {
		return mockMvc.perform(get(TREND, merchantId).header(HEADER, apiKey).param("from", from).param("to", to));
	}

}
