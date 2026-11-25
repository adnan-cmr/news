package com.example.news.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.example.news.repository.AdminUserRepository;

@Configuration
public class AuthenticationConfig {

	@Bean
	UserDetailsService userDetailsService(AdminUserRepository users) {
		return username -> users.findByUsername(username)
				.orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
						"Admin account not found"));
	}
}
