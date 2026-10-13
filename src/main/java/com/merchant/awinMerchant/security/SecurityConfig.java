package com.merchant.awinMerchant.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

	/** Explicitly public: API documentation and the health probe. Everything else needs an API key. */
	private static final String[] PUBLIC_PATHS = { "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
			"/swagger-ui/**", "/actuator/health", "/actuator/health/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, MerchantApiKeyDao merchantApiKeyDao,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) throws Exception {
		var errorHandler = new ProblemDetailSecurityErrorHandler(handlerExceptionResolver);

		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.addFilterBefore(new ApiKeyAuthenticationFilter(merchantApiKeyDao, errorHandler),
					AnonymousAuthenticationFilter.class)
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errorHandler)
				.accessDeniedHandler(errorHandler))
			.authorizeHttpRequests(requests -> requests
				// Container error dispatches (e.g. to /error after a 404) carry no API key; the original
				// request was already authorized, so the error rendering must not turn into a 401.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.requestMatchers("/api/v1/merchants/{" + MerchantPathAuthorizationManager.MERCHANT_ID_VARIABLE + "}/**")
				.access(new MerchantPathAuthorizationManager())
				.anyRequest().authenticated());
		return http.build();
	}

}
