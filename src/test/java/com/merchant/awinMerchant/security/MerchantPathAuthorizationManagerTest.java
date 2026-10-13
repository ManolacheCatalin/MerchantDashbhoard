package com.merchant.awinMerchant.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class MerchantPathAuthorizationManagerTest {

	private final MerchantPathAuthorizationManager manager = new MerchantPathAuthorizationManager();

	@Test
	void grantsAccessToOwnMerchant() {
		assertThat(isGranted(merchant(1), "1")).isTrue();
	}

	@Test
	void deniesAccessToAnotherMerchant() {
		assertThat(isGranted(merchant(1), "2")).isFalse();
	}

	@Test
	void deniesNonNumericMerchantId() {
		assertThat(isGranted(merchant(1), "abc")).isFalse();
	}

	@Test
	void deniesAnonymousRequest() {
		Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymous",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		assertThat(isGranted(anonymous, "1")).isFalse();
	}

	private boolean isGranted(Authentication authentication, String merchantIdVariable) {
		var context = new RequestAuthorizationContext(new MockHttpServletRequest(),
				Map.of(MerchantPathAuthorizationManager.MERCHANT_ID_VARIABLE, merchantIdVariable));
		return manager.authorize(() -> authentication, context).isGranted();
	}

	private static Authentication merchant(long merchantId) {
		return new PreAuthenticatedAuthenticationToken(new AuthenticatedMerchant(merchantId), null, List.of());
	}

}
