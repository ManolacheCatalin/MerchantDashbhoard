package com.merchant.awinMerchant.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.merchant.awinMerchant.merchant.entity.Merchant;
import com.merchant.awinMerchant.merchant.service.MerchantNotFoundException;
import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.todo.dao.TodoDao;
import com.merchant.awinMerchant.todo.dto.TodoListResponse;
import com.merchant.awinMerchant.todo.dto.TodoResponse;
import com.merchant.awinMerchant.todo.entity.TodoItem;
import com.merchant.awinMerchant.todo.entity.TodoStatus;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-09-20T09:00:00Z");

	@Mock
	private MerchantService merchantService;

	@Mock
	private TodoDao todoDao;

	private TodoService service() {
		return new TodoService(merchantService, todoDao);
	}

	@Test
	void listOpenMapsItemsInDaoOrder() {
		when(merchantService.getMerchant(1)).thenReturn(new Merchant(1, "Merchant A", "GBP"));
		when(todoDao.findOpenByMerchant(1)).thenReturn(List.of(
				new TodoItem(101, 1, "Review expired offers", TodoStatus.OPEN, CREATED),
				new TodoItem(102, 1, "Approve applications", TodoStatus.OPEN, CREATED.plusDays(2))));

		TodoListResponse response = service().listOpen(1);

		assertThat(response.merchantId()).isEqualTo(1);
		assertThat(response.items()).containsExactly(
				new TodoListResponse.Item(101, "Review expired offers", TodoStatus.OPEN, CREATED),
				new TodoListResponse.Item(102, "Approve applications", TodoStatus.OPEN, CREATED.plusDays(2)));
	}

	@Test
	void listOpenWithoutItemsIsEmpty() {
		when(merchantService.getMerchant(3)).thenReturn(new Merchant(3, "Merchant C", "GBP"));
		when(todoDao.findOpenByMerchant(3)).thenReturn(List.of());

		assertThat(service().listOpen(3).items()).isEmpty();
	}

	@Test
	void listOpenForUnknownMerchantFailsBeforeQueryingItems() {
		when(merchantService.getMerchant(99)).thenThrow(new MerchantNotFoundException(99));

		assertThatThrownBy(() -> service().listOpen(99)).isInstanceOf(MerchantNotFoundException.class);
		verifyNoInteractions(todoDao);
	}

	@Test
	void completingReturnsTheUpdatedItem() {
		when(todoDao.markCompleted(1, 101))
			.thenReturn(Optional.of(new TodoItem(101, 1, "Review expired offers", TodoStatus.COMPLETED, CREATED)));

		TodoResponse response = service().updateStatus(1, 101, TodoStatus.COMPLETED);

		assertThat(response)
			.isEqualTo(new TodoResponse(101, 1, "Review expired offers", TodoStatus.COMPLETED, CREATED));
	}

	@Test
	void completingIsIdempotent() {
		var completed = Optional.of(new TodoItem(103, 1, "Update commission groups", TodoStatus.COMPLETED, CREATED));
		when(todoDao.markCompleted(1, 103)).thenReturn(completed);

		TodoResponse first = service().updateStatus(1, 103, TodoStatus.COMPLETED);
		TodoResponse second = service().updateStatus(1, 103, TodoStatus.COMPLETED);

		assertThat(second).isEqualTo(first);
		assertThat(second.status()).isEqualTo(TodoStatus.COMPLETED);
	}

	@Test
	void unknownOrForeignItemIsNotFound() {
		when(todoDao.markCompleted(1, 201)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().updateStatus(1, 201, TodoStatus.COMPLETED))
			.isInstanceOf(TodoNotFoundException.class)
			.satisfies(ex -> assertThat(((TodoNotFoundException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		verify(todoDao).markCompleted(1, 201);
	}

	@Test
	void reopeningIsRejectedWithoutTouchingTheDatabase() {
		assertThatThrownBy(() -> service().updateStatus(1, 101, TodoStatus.OPEN))
			.isInstanceOf(UnsupportedTodoStatusException.class)
			.satisfies(ex -> assertThat(((UnsupportedTodoStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST));
		verifyNoInteractions(todoDao, merchantService);
	}

}
