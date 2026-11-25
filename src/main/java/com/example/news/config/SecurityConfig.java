package com.example.news.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**").permitAll()
						.requestMatchers("/", "/news/**", "/pdf/view/**").permitAll()
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.defaultSuccessUrl("/admin/dashboard", true)
						.failureUrl("/login?error")
						.permitAll())
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID"))
				.sessionManagement(session -> session
						.sessionFixation(fixation -> fixation.migrateSession())
						.maximumSessions(1))
				.csrf(Customizer.withDefaults())
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' https://cdn.jsdelivr.net https://tessdata.projectnaptha.com; worker-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net blob:"))
						.frameOptions(frame -> frame.deny())
						.xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
						.contentTypeOptions(Customizer.withDefaults()));
		// CSRF protection intentionally remains enabled for all browser form and JSON mutations.
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}
}
