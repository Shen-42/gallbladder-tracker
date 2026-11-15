package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GallbladderTrackerApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void hostDatabaseUrlBecomesJdbcSettings() {
		assertThat(GallbladderTrackerApplication.datasourceProperties(
				"postgresql://gallbladder:p%40ss:w0rd@dpg-abc123-a:5432/gallbladder"))
				.containsEntry("spring.datasource.url", "jdbc:postgresql://dpg-abc123-a:5432/gallbladder")
				.containsEntry("spring.datasource.username", "gallbladder")
				.containsEntry("spring.datasource.password", "p@ss:w0rd");
		assertThat(GallbladderTrackerApplication.datasourceProperties("postgresql://u:p@host/db"))
				.containsEntry("spring.datasource.url", "jdbc:postgresql://host/db");
	}

}
