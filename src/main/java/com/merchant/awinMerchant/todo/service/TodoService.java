package com.merchant.awinMerchant.todo.service;

import org.springframework.stereotype.Service;

import com.merchant.awinMerchant.merchant.service.MerchantService;
import com.merchant.awinMerchant.todo.dao.TodoDao;
import com.merchant.awinMerchant.todo.dto.TodoListResponse;
import com.merchant.awinMerchant.todo.dto.TodoResponse;
import com.merchant.awinMerchant.todo.entity.TodoStatus;

@Service
public class TodoService {

	private final MerchantService merchantService;

	private final TodoDao todoDao;

	public TodoService(MerchantService merchantService, TodoDao todoDao) {
		this.merchantService = merchantService;
		this.todoDao = todoDao;
	}

	/** Open items of the merchant, oldest first. An unknown merchant yields 404 like the dashboard endpoints. */
	public TodoListResponse listOpen(long merchantId) {
		merchantService.getMerchant(merchantId);
		return TodoListResponse.of(merchantId, todoDao.findOpenByMerchant(merchantId));
	}

	/**
	 * Marks the merchant's item COMPLETED. Idempotent: completing an already completed item returns it again. Any other
	 * requested status is rejected before touching the database.
	 * Acts as atomic operation which satifies our contract for idempotency on the request
	 */
	public TodoResponse updateStatus(long merchantId, long todoId, TodoStatus requestedStatus) {
		if (requestedStatus != TodoStatus.COMPLETED) {
			throw new UnsupportedTodoStatusException();
		}
		return todoDao.markCompleted(merchantId, todoId).map(TodoResponse::of).orElseThrow(TodoNotFoundException::new);
	}

}
