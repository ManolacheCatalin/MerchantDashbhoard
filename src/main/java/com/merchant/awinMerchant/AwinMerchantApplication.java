package com.merchant.awinMerchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Authentication is API-key only; Boot's default in-memory user (and its generated password) is not wanted.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AwinMerchantApplication {

	public static void main(String[] args) {
		SpringApplication.run(AwinMerchantApplication.class, args);
	}

}
