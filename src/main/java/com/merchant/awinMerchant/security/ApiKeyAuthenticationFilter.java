package com.merchant.awinMerchant.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests carrying an {@code X-API-Key} header.
 * <ul>
 * <li>No (or blank) header: the request continues anonymously; protected endpoints then answer 401.</li>
 * <li>Unknown or inactive key: rejected immediately with 401.</li>
 * <li>Active key: the owning merchant becomes the authenticated principal.</li>
 * </ul>
 * Deliberately not a Spring bean, so Boot does not also register it as a servlet filter.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	public static final String API_KEY_HEADER = "X-API-Key";

	private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

	private final MerchantApiKeyDao merchantApiKeyDao;

	private final AuthenticationEntryPoint authenticationEntryPoint;

	public ApiKeyAuthenticationFilter(MerchantApiKeyDao merchantApiKeyDao,
			AuthenticationEntryPoint authenticationEntryPoint) {
		this.merchantApiKeyDao = merchantApiKeyDao;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(API_KEY_HEADER);
		//usefull for public api calls, which will be later triagged
		if (apiKey == null || apiKey.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		Optional<Long> merchantId = merchantApiKeyDao.findActiveMerchantIdByKeyHash(ApiKeyHasher.sha256Hex(apiKey));
		if (merchantId.isEmpty()) {
			// Never log the key (or its hash).
			log.debug("Rejected request to {}: unknown or inactive API key", request.getRequestURI());
			//usefull for thread execution isolation before returning the thread to the pool
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.commence(request, response,
					new BadCredentialsException("Unknown or inactive API key"));
			return;
		}

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new PreAuthenticatedAuthenticationToken(
				new AuthenticatedMerchant(merchantId.get()), null, List.of()));
		SecurityContextHolder.setContext(context);
		filterChain.doFilter(request, response);
	}

}
