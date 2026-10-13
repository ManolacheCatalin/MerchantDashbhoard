package com.merchant.awinMerchant.todo.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * No to-do item with the requested id belongs to the merchant, including items that belong to another merchant (they
 * are indistinguishable from unknown ids). Rendered as a 404 ProblemDetail by the existing MVC exception handling.
 */
public class TodoNotFoundException extends ErrorResponseException {

	public TodoNotFoundException() {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "To-do item not found."),
				null);
	}

}
