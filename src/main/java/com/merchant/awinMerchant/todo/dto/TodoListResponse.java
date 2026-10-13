package com.merchant.awinMerchant.todo.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.merchant.awinMerchant.todo.entity.TodoItem;
import com.merchant.awinMerchant.todo.entity.TodoStatus;

/** Open to-do items of one merchant, in the order given (oldest first). */
public record TodoListResponse(long merchantId, List<Item> items) {

	/** {@code createdAt} is the stored instant, serialized in UTC (e.g. {@code 2026-09-20T09:00:00Z}). */
	public record Item(long id, String title, TodoStatus status, OffsetDateTime createdAt) {
	}

	/** Drops the per-item merchant id, which is already given once at the top level. */
	public static TodoListResponse of(long merchantId, List<TodoItem> todos) {
		return new TodoListResponse(merchantId,
				todos.stream().map(todo -> new Item(todo.id(), todo.title(), todo.status(), todo.createdAt())).toList());
	}

}
