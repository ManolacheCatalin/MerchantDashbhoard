package com.merchant.awinMerchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static com.merchant.awinMerchant.support.TestDatabase.newApiKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.merchant.awinMerchant.support.TestDatabase;

/**
 * Runs the full security filter chain against the real awin_test database. Keys are generated per run, so no working
 * credential exists in the repository.
 */
@SpringBootTest(properties = { "logging.level.com.merchant.awinMerchant=DEBUG",
		"logging.level.org.springframework.security=DEBUG" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyAuthenticationIntegrationTest {

	private static final String HEADER = ApiKeyAuthenticationFilter.API_KEY_HEADER;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Jdbi jdbi;

	private String merchantAKey;

	private String merchantBKey;

	private String merchantCInactiveKey;

	@BeforeEach
	void setUpFixtures() {
		//db stored setup (TestDatabase.reset() refuses to run against anything but awin_test)
		TestDatabase db = new TestDatabase(jdbi);
		db.reset();
		db.insertMerchant(1, "Merchant A", "GBP");
		db.insertMerchant(2, "Merchant B", "EUR");
		db.insertMerchant(3, "Merchant C", "GBP");
		merchantAKey = db.insertApiKey(1, true);
		merchantBKey = db.insertApiKey(2, true);
		merchantCInactiveKey = db.insertApiKey(3, false);
	}

	@Test
	void validKeyReturnsOnlyTheAuthenticatedMerchant() throws Exception {
		mockMvc.perform(get("/api/v1/merchants").header(HEADER, merchantAKey))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.merchants", hasSize(1)))
			.andExpect(jsonPath("$.merchants[0].id").value(1))
			.andExpect(jsonPath("$.merchants[0].name").value("Merchant A"));
	}

	@Test
	void selectorFollowsTheKeyNotTheFirstMerchantRow() throws Exception {
		// Merchant 2 is not the first row, so a hard-coded id or "first merchant" lookup would fail here.
		mockMvc.perform(get("/api/v1/merchants").header(HEADER, merchantBKey))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.merchants", hasSize(1)))
			.andExpect(jsonPath("$.merchants[0].id").value(2))
			.andExpect(jsonPath("$.merchants[0].name").value("Merchant B"));
	}

	@Test
	void missingKeyIsUnauthorized() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/v1/merchants")).andReturn());
	}

	@Test
	void blankKeyIsUnauthorized() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/v1/merchants").header(HEADER, "   ")).andReturn());
	}

	@Test
	void unknownKeyIsUnauthorizedAndNeverEchoedOrLogged(CapturedOutput output) throws Exception {
		String unknownKey = newApiKey();

		MvcResult result = mockMvc.perform(get("/api/v1/merchants").header(HEADER, unknownKey)).andReturn();

		expectUnauthorized(result);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(unknownKey);
		assertThat(output).doesNotContain(unknownKey).doesNotContain(ApiKeyHasher.sha256Hex(unknownKey));
	}

	@Test
	void inactiveKeyIsUnauthorized(CapturedOutput output) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/merchants").header(HEADER, merchantCInactiveKey))
			.andReturn();

		expectUnauthorized(result);
		assertThat(output).doesNotContain(merchantCInactiveKey);
	}

	@Test
	void validKeyForAnotherMerchantsResourceIsForbidden(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/api/v1/merchants/2/todos").header(HEADER, merchantAKey))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));
		assertThat(output).doesNotContain(merchantAKey);
	}

	@Test
	void unknownMerchantIdInPathIsForbiddenNotLeaked() throws Exception {
		mockMvc.perform(get("/api/v1/merchants/999/todos").header(HEADER, merchantAKey))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownMerchantPathPassesAuthorization() throws Exception {
		// An unmapped merchant-scoped path: a request that clears security ends in 404 from MVC, not 401/403.
		mockMvc.perform(get("/api/v1/merchants/1/unmapped").header(HEADER, merchantAKey))
			.andExpect(status().isNotFound());
	}

	@Test
	void merchantScopedPathWithoutKeyIsUnauthorized() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/v1/merchants/1/todos")).andReturn());
	}

	@Test
	void healthProbeIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void openApiDocumentIsPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	private static void expectUnauthorized(MvcResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("ApiKey header=\"X-API-Key\"");
		assertThat(result.getResponse().getContentAsString()).contains("\"status\":401");
	}

}
