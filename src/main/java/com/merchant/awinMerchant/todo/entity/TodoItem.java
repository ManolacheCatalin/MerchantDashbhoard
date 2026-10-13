package com.merchant.awinMerchant.todo.entity;

import java.time.OffsetDateTime;

/** One row of {@code todo_items}. */
public record TodoItem(long id, long merchantId, String title, TodoStatus status, OffsetDateTime createdAt) {
}
