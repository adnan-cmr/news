package com.example.news;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:news-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class NewsApplicationTests {

	@Test
	void contextLoads() {
	}

}
