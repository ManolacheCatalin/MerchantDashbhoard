package com.merchant.awinMerchant.todo.dto;

import java.time.OffsetDateTime;
import com.merchant.awinMerchant.todo.entity.TodoItem;
import com.merchant.awinMerchant.todo.entity.TodoStatus;

/** The updated item returned by PATCH. */
public record TodoResponse(long id, long merchantId, String title, TodoStatus status, OffsetDateTime createdAt) {

	public static TodoResponse of(TodoItem todo) {
		return new TodoResponse(todo.id(), todo.merchantId(), todo.title(), todo.status(), todo.createdAt());
	}

}
