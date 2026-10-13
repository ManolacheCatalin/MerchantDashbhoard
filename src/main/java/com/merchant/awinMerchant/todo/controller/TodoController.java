package com.merchant.awinMerchant.todo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import com.merchant.awinMerchant.todo.dto.TodoListResponse;
import com.merchant.awinMerchant.todo.dto.TodoResponse;
import com.merchant.awinMerchant.todo.dto.UpdateTodoRequest;
import com.merchant.awinMerchant.todo.service.TodoService;

/** Merchant ownership of {@code merchantId} is enforced by the security filter chain before this runs. */
@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/todos")
public class TodoController {

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping
	public TodoListResponse listOpenTodos(@PathVariable long merchantId) {
		return todoService.listOpen(merchantId);
	}

	@PatchMapping("/{todoId}")
	public TodoResponse updateTodo(@PathVariable long merchantId, @PathVariable long todoId,
			@Valid @RequestBody UpdateTodoRequest request) {
		return todoService.updateStatus(merchantId, todoId, request.status());
	}

}
