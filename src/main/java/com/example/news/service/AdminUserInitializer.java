package com.example.news.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.news.entity.AdminUser;
import com.example.news.repository.AdminUserRepository;

@Component
public class AdminUserInitializer implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(AdminUserInitializer.class);
	private final AdminUserRepository users;
	private final PasswordEncoder passwordEncoder;

	public AdminUserInitializer(AdminUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		if (users.existsByUsername("admin")) {
			return;
		}

		String initialPassword = System.getenv("ADMIN_INIT_PASSWORD");
		if (initialPassword == null || initialPassword.isBlank()) {
			initialPassword = generatePassword();
			logger.warn("ADMIN_INIT_PASSWORD is unset. A one-time initial admin password follows on standard output.");
			System.out.println("Initial admin password (shown once): " + initialPassword);
		}
		if (initialPassword.length() < 12) {
			throw new IllegalStateException("ADMIN_INIT_PASSWORD must be at least 12 characters.");
		}

		users.save(new AdminUser("admin", passwordEncoder.encode(initialPassword)));
	}

	private String generatePassword() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
