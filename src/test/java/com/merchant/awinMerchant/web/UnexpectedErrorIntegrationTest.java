package com.merchant.awinMerchant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.SQLException;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.merchant.awinMerchant.revenuetrend.dao.TrendTransactionDao;
import com.merchant.awinMerchant.security.ApiKeyAuthenticationFilter;
import com.merchant.awinMerchant.summary.dao.SummaryTransactionDao;
import com.merchant.awinMerchant.support.TestDatabase;

/**
 * An unexpected failure (here: the transaction query fails) must return a 500
 * ProblemDetail with a fixed text. SQL,
 * exception names and stack traces stay in the server log and never reach the
 * client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorIntegrationTest {

	private static final String SQL_FRAGMENT = "secret_sql_fragment";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Jdbi jdbi;

	@MockitoBean

	private SummaryTransactionDao summaryTransactionDao;


	@MockitoBean

	private TrendTransactionDao trendTransactionDao;

	private String apiKey;

	@BeforeEach
	void setUp() {
		TestDatabase db = new TestDatabase(jdbi);
		db.reset();
		db.insertMerchant(1, "Merchant A", "GBP");
		apiKey = db.insertApiKey(1, true);

		var failure = new IllegalStateException("Query failed",
				new SQLException("ERROR: column \"" + SQL_FRAGMENT + "\" does not exist"));
		when(summaryTransactionDao.sumApproved(anyLong(), any(), any())).thenThrow(failure);
		when(trendTransactionDao.sumApprovedByUtcDay(anyLong(), any(), any())).thenThrow(failure);
	}

	@ParameterizedTest
	@ValueSource(strings = { "summary", "revenue-trend" })
	void databaseFailureReturnsGenericProblemDetail(String endpoint, CapturedOutput output) throws Exception {
		MvcResult result = mockMvc
				.perform(get("/api/v1/merchants/1/dashboard/" + endpoint)
						.header(ApiKeyAuthenticationFilter.API_KEY_HEADER, apiKey)
						.param("from", "2026-09-01")
						.param("to", "2026-09-08"))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(result.getResponse().getStatus()).isEqualTo(500);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(body).contains("\"detail\":\"Unexpected server error.\"").contains("\"status\":500");
		assertThat(body).doesNotContain(SQL_FRAGMENT)
				.doesNotContain("SQLException")
				.doesNotContain("IllegalStateException")
				.doesNotContain("at com.")
				.doesNotContain(apiKey);

		// The details are kept for operators, in the server log only.
		assertThat(output).contains("Unexpected error while handling request").contains(SQL_FRAGMENT);
	}

}
