package com.merchant.awinMerchant.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.merchant.awinMerchant.security.ApiKeyAuthenticationFilter;
import com.merchant.awinMerchant.support.TestDatabase;

/** GET and PATCH to-do endpoints against the real awin_test database, through the full security chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoIntegrationTest {

	private static final String HEADER = ApiKeyAuthenticationFilter.API_KEY_HEADER;

	private static final String TODOS = "/api/v1/merchants/{merchantId}/todos";

	private static final String TODO = "/api/v1/merchants/{merchantId}/todos/{todoId}";

	private static final String COMPLETE = "{\"status\":\"COMPLETED\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Jdbi jdbi;

	private String merchantAKey;

	private String merchantBKey;

	private String merchantCKey;

	private String inactiveKey;

	@BeforeEach
	void setUpFixtures() {
		TestDatabase db = new TestDatabase(jdbi);
		db.reset();
		db.insertMerchant(1, "Merchant A", "GBP");
		db.insertMerchant(2, "Merchant B", "EUR");
		db.insertMerchant(3, "Merchant C", "GBP"); // no to-do items
		db.insertMerchant(4, "Merchant D", "GBP");
		merchantAKey = db.insertApiKey(1, true);
		merchantBKey = db.insertApiKey(2, true);
		merchantCKey = db.insertApiKey(3, true);
		inactiveKey = db.insertApiKey(4, false);

		// Merchant A: inserted out of order to prove ORDER BY created_at, id
		insertTodo(102, 1, "Approve pending partner applications", "OPEN", "2026-09-22T14:30:00Z");
		insertTodo(104, 1, "Same timestamp as 101, higher id", "OPEN", "2026-09-20T09:00:00Z");
		insertTodo(101, 1, "Review expired offers", "OPEN", "2026-09-20T09:00:00Z");
		insertTodo(103, 1, "Update commission groups", "COMPLETED", "2026-09-10T08:15:00Z"); // not listed
		// Stored with a +03:00 offset; must be returned as the same instant in UTC: 2026-09-15T07:00:00Z
		insertTodo(105, 1, "Check offset handling", "OPEN", "2026-09-15T10:00:00+03:00");

		// Merchant B: must never appear for, or be changeable by, Merchant A
		insertTodo(201, 2, "Validate pending transactions", "OPEN", "2026-09-18T11:00:00Z");
	}

	// ---------------------------------------------------------------- GET

	@Test
	void listsOnlyOpenItemsOfTheMerchantOldestFirst() throws Exception {
		String body = mockMvc.perform(get(TODOS, 1).header(HEADER, merchantAKey))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.merchantId").value(1))
			.andExpect(jsonPath("$.items", hasSize(4)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).isEqualTo("{\"merchantId\":1,\"items\":["
				+ "{\"id\":105,\"title\":\"Check offset handling\",\"status\":\"OPEN\",\"createdAt\":\"2026-09-15T07:00:00Z\"},"
				+ "{\"id\":101,\"title\":\"Review expired offers\",\"status\":\"OPEN\",\"createdAt\":\"2026-09-20T09:00:00Z\"},"
				+ "{\"id\":104,\"title\":\"Same timestamp as 101, higher id\",\"status\":\"OPEN\",\"createdAt\":\"2026-09-20T09:00:00Z\"},"
				+ "{\"id\":102,\"title\":\"Approve pending partner applications\",\"status\":\"OPEN\",\"createdAt\":\"2026-09-22T14:30:00Z\"}]}");
	}

	@Test
	void eachMerchantSeesOnlyItsOwnItems() throws Exception {
		mockMvc.perform(get(TODOS, 2).header(HEADER, merchantBKey))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.merchantId").value(2))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(201));
	}

	@Test
	void merchantWithoutItemsGetsEmptyList() throws Exception {
		mockMvc.perform(get(TODOS, 3).header(HEADER, merchantCKey))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"merchantId\":3,\"items\":[]}", JsonCompareMode.STRICT));
	}

	@Test
	void listingAnotherMerchantsItemsIsForbidden() throws Exception {
		mockMvc.perform(get(TODOS, 2).header(HEADER, merchantAKey))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void listingWithoutValidKeyIsUnauthorized() throws Exception {
		mockMvc.perform(get(TODOS, 1)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(TODOS, 1).header(HEADER, TestDatabase.newApiKey())).andExpect(status().isUnauthorized());
		mockMvc.perform(get(TODOS, 4).header(HEADER, inactiveKey))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// ---------------------------------------------------------------- PATCH: success and idempotency

	@Test
	void completingAnOpenItemReturnsItAndPersistsTheChange() throws Exception {
		patchTodo(1, 101, merchantAKey, COMPLETE).andExpect(status().isOk())
			.andExpect(content().json("""
					{"id":101,"merchantId":1,"title":"Review expired offers","status":"COMPLETED",
					 "createdAt":"2026-09-20T09:00:00Z"}""", JsonCompareMode.STRICT));

		assertThat(storedStatus(101)).isEqualTo("COMPLETED");
		mockMvc.perform(get(TODOS, 1).header(HEADER, merchantAKey))
			.andExpect(jsonPath("$.items", hasSize(3)))
			.andExpect(jsonPath("$.items[?(@.id == 101)]").isEmpty());
	}

	@Test
	void completingTwiceIsIdempotent() throws Exception {
		String first = patchTodo(1, 102, merchantAKey, COMPLETE).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String second = patchTodo(1, 102, merchantAKey, COMPLETE).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(storedStatus(102)).isEqualTo("COMPLETED");
	}

	@Test
	void completingAnAlreadyCompletedItemReturnsOk() throws Exception {
		patchTodo(1, 103, merchantAKey, COMPLETE).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(103))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.createdAt").value("2026-09-10T08:15:00Z"));
	}

	@Test
	void onlyTheStatusChangesEvenIfOtherFieldsAreSent() throws Exception {
		patchTodo(1, 101, merchantAKey, "{\"status\":\"COMPLETED\",\"title\":\"Hacked\",\"merchantId\":2,\"id\":999}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(101))
			.andExpect(jsonPath("$.merchantId").value(1))
			.andExpect(jsonPath("$.title").value("Review expired offers"));

		String stored = jdbi.withHandle(h -> h.createQuery("SELECT title || '|' || merchant_id FROM todo_items WHERE id = 101")
			.mapTo(String.class)
			.one());
		assertThat(stored).isEqualTo("Review expired offers|1");
	}

	@Test
	void completingDoesNotAffectOtherItems() throws Exception {
		patchTodo(1, 101, merchantAKey, COMPLETE).andExpect(status().isOk());

		assertThat(storedStatus(102)).isEqualTo("OPEN");
		assertThat(storedStatus(104)).isEqualTo("OPEN");
		assertThat(storedStatus(201)).isEqualTo("OPEN");
	}

	// ---------------------------------------------------------------- PATCH: not found and authorization

	@Test
	void anotherMerchantsItemThroughOwnPathIsNotFoundAndUnchanged() throws Exception {
		patchTodo(1, 201, merchantAKey, COMPLETE).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("To-do item not found."));

		assertThat(storedStatus(201)).isEqualTo("OPEN");
	}

	@Test
	void unknownItemIsNotFound() throws Exception {
		patchTodo(1, 999, merchantAKey, COMPLETE).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void anotherMerchantsPathIsForbiddenAndUnchanged() throws Exception {
		patchTodo(2, 201, merchantAKey, COMPLETE).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(storedStatus(201)).isEqualTo("OPEN");
	}

	@Test
	void patchWithoutValidKeyIsUnauthorizedAndUnchanged() throws Exception {
		mockMvc.perform(patch(TODO, 1, 101).contentType(MediaType.APPLICATION_JSON).content(COMPLETE))
			.andExpect(status().isUnauthorized());
		patchTodo(1, 101, TestDatabase.newApiKey(), COMPLETE).andExpect(status().isUnauthorized());
		patchTodo(4, 101, inactiveKey, COMPLETE).andExpect(status().isUnauthorized());

		assertThat(storedStatus(101)).isEqualTo("OPEN");
	}

	// ---------------------------------------------------------------- PATCH: invalid requests

	@Test
	void reopeningIsAnUnsupportedTransition() throws Exception {
		patchTodo(1, 103, merchantAKey, "{\"status\":\"OPEN\"}").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Unsupported status transition: only COMPLETED is allowed."));

		assertThat(storedStatus(103)).isEqualTo("COMPLETED");
	}

	@ParameterizedTest(name = "body: {0}")
	@ValueSource(strings = { "{\"status\":\"DONE\"}", // unknown status
			"{\"status\":\"completed\"}", // wrong case
			"{\"status\":null}", // null status
			"{}", // missing status
			"{\"status\":", // malformed JSON
			"" }) // empty body
	void invalidBodyIsBadRequestAndUnchanged(String body) throws Exception {
		patchTodo(1, 101, merchantAKey, body).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(storedStatus(101)).isEqualTo("OPEN");
	}

	@Test
	void nonNumericTodoIdIsBadRequest() throws Exception {
		mockMvc
			.perform(patch("/api/v1/merchants/1/todos/abc").header(HEADER, merchantAKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(COMPLETE))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void nonJsonContentTypeIsUnsupportedMediaType() throws Exception {
		mockMvc
			.perform(patch(TODO, 1, 101).header(HEADER, merchantAKey)
				.contentType(MediaType.TEXT_PLAIN)
				.content("COMPLETED"))
			.andExpect(status().isUnsupportedMediaType());

		assertThat(storedStatus(101)).isEqualTo("OPEN");
	}

	// ---------------------------------------------------------------- helpers

	private ResultActions patchTodo(long merchantId, long todoId, String apiKey, String body) throws Exception {
		return mockMvc.perform(patch(TODO, merchantId, todoId).header(HEADER, apiKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private void insertTodo(long id, long merchantId, String title, String status, String createdAt) {
		jdbi.useHandle(handle -> handle.execute(
				"INSERT INTO todo_items (id, merchant_id, title, status, created_at) VALUES (?, ?, ?, ?, ?::timestamptz)",
				id, merchantId, title, status, createdAt));
	}

	private String storedStatus(long todoId) {
		return jdbi.withHandle(handle -> handle.createQuery("SELECT status FROM todo_items WHERE id = :id")
			.bind("id", todoId)
			.mapTo(String.class)
			.one());
	}

}
