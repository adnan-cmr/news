package com.example.news.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.news.entity.AdminUser;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
	Optional<AdminUser> findByUsername(String username);

	boolean existsByUsername(String username);
}
