package com.merchant.awinMerchant.todo.dto;

import jakarta.validation.constraints.NotNull;
import com.merchant.awinMerchant.todo.entity.TodoStatus;

/** PATCH body: {@code {"status": "COMPLETED"}}. Other properties are ignored; only the status can change. */
public record UpdateTodoRequest(@NotNull TodoStatus status) {
}
