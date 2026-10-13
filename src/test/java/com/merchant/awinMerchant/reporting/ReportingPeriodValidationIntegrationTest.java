package com.merchant.awinMerchant.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.merchant.awinMerchant.security.ApiKeyAuthenticationFilter;
import com.merchant.awinMerchant.support.TestDatabase;

/**
 * Dates that parse as ISO dates but cannot be reported on must be rejected with 400 by both dashboard endpoints,
 * never reach PostgreSQL or overflow date arithmetic (which used to surface as 500 or silently wrong windows).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportingPeriodValidationIntegrationTest {

	private static final String HEADER = ApiKeyAuthenticationFilter.API_KEY_HEADER;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Jdbi jdbi;

	private String apiKey;

	@BeforeEach
	void setUpFixtures() {
		TestDatabase db = new TestDatabase(jdbi);
		db.reset();
		db.insertMerchant(1, "Merchant A", "GBP");
		apiKey = db.insertApiKey(1, true);
	}

	@ParameterizedTest(name = "{0}: from={1} to={2}")
	@CsvSource({
			// endpoint,      from,                   to
			"summary,         -5000-01-01,            2026-09-08", // year before 0001
			"revenue-trend,   -5000-01-01,            2026-09-08",
			"summary,         +300000-01-01,          +300000-01-02", // beyond PostgreSQL timestamp range
			"revenue-trend,   +300000-01-01,          +300000-01-02",
			"summary,         -999999999-01-01,       +999999999-12-31", // overflows day arithmetic
			"revenue-trend,   -999999999-01-01,       +999999999-12-31",
			"summary,         9999-12-30,             +10000-01-01", // 'to' after 9999-12-31
			"revenue-trend,   9999-12-30,             +10000-01-01",
			"summary,         0001-01-01,             0001-01-02", // comparison period would start before 0001
			"revenue-trend,   0001-01-01,             0001-01-02",
			"summary,         2026-01-01,             2027-01-03", // 367 days: longer than the maximum
			"revenue-trend,   2026-01-01,             2027-01-03",
			"summary,         5000-07-01,             9999-12-31", // ~1.8M days: huge trend response before
			"revenue-trend,   5000-07-01,             9999-12-31" })
	void unsupportedDatesAreBadRequest(String endpoint, String from, String to) throws Exception {
		mockMvc
			.perform(get("/api/v1/merchants/1/dashboard/" + endpoint).header(HEADER, apiKey)
				.param("from", from)
				.param("to", to))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400));
	}

	@ParameterizedTest(name = "{0}: from={1} to={2}")
	@CsvSource({
			"summary,         0001-01-02,             0001-01-03", // comparison period starts exactly 0001-01-01
			"revenue-trend,   0001-01-02,             0001-01-03",
			"summary,         9999-12-30,             9999-12-31", // latest supported 'to'
			"revenue-trend,   9999-12-30,             9999-12-31",
			"summary,         2028-01-01,             2029-01-01", // exactly 366 days (full leap year)
			"revenue-trend,   2028-01-01,             2029-01-01" })
	void datesAtTheSupportedBoundariesAreAccepted(String endpoint, String from, String to) throws Exception {
		mockMvc
			.perform(get("/api/v1/merchants/1/dashboard/" + endpoint).header(HEADER, apiKey)
				.param("from", from)
				.param("to", to))
			.andExpect(status().isOk());
	}

}
