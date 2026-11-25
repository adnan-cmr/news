package com.example.news.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.news.entity.NewsPost;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {
	List<NewsPost> findAllByOrderByCreatedAtDesc();
}
