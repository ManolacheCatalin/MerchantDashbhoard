package com.merchant.awinMerchant.todo.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Only completing a task is supported; reopening is not. Rendered as a 400 ProblemDetail. */
public class UnsupportedTodoStatusException extends ErrorResponseException {

	public UnsupportedTodoStatusException() {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Unsupported status transition: only COMPLETED is allowed."), null);
	}

}
