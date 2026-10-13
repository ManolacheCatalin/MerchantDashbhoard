package com.merchant.awinMerchant.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.merchant.awinMerchant.merchant.service.MerchantNotFoundException;
import com.merchant.awinMerchant.reporting.util.InvalidReportingPeriodException;

/**
 * Maps API errors to RFC 9457 ProblemDetail responses. The base class covers Spring MVC errors (missing or
 * unparseable parameters, unknown routes, ...). Details below are fixed texts: credentials and SQL never reach the
 * client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"A valid API key is required in the X-API-Key header.");
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.header(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"X-API-Key\"")
			.body(problem);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"The API key is not authorized for the requested merchant.");
	}

	@ExceptionHandler(MerchantNotFoundException.class)
	ProblemDetail handleMerchantNotFound(MerchantNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Merchant not found.");
	}

	@ExceptionHandler(InvalidReportingPeriodException.class)
	ProblemDetail handleInvalidReportingPeriod(InvalidReportingPeriodException ex) {
		// The message is one of the fixed texts defined in ReportingPeriod.
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Anything not handled above (e.g. a database failure). The details are logged here only; the client gets a fixed
	 * text, never the exception message, SQL or stack trace.
	 */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error while handling request", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
	}

}
