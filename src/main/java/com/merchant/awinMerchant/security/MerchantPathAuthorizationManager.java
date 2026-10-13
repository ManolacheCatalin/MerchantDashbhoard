package com.merchant.awinMerchant.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Grants access to {@code /api/v1/merchants/{merchantId}/**} only when the path merchant is the authenticated one.
 * Runs in the security filter chain, before any controller or merchant data access. A denial for an authenticated
 * merchant yields 403; for an anonymous request, 401.
 */
final class MerchantPathAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	static final String MERCHANT_ID_VARIABLE = "merchantId";

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		Authentication current = authentication.get();
		if (current == null || !(current.getPrincipal() instanceof AuthenticatedMerchant merchant)) {
			return new AuthorizationDecision(false);
		}
		return new AuthorizationDecision(isSameMerchant(merchant, context.getVariables().get(MERCHANT_ID_VARIABLE)));
	}

	private static boolean isSameMerchant(AuthenticatedMerchant merchant, String requestedMerchantId) {
		try {
			return requestedMerchantId != null && Long.parseLong(requestedMerchantId) == merchant.merchantId();
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

}
