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

	@Test
	void neonUrlKeepsItsSslSettingAndTheDriverAcceptsIt() {
		String jdbcUrl = GallbladderTrackerApplication.datasourceProperties(
				"postgresql://neondb_owner:npg_secret@ep-cool-name-a1b2c3d4.ap-southeast-1.aws.neon.tech/neondb"
				+ "?sslmode=require&channel_binding=require").get("spring.datasource.url");

		assertThat(jdbcUrl).isEqualTo("jdbc:postgresql://ep-cool-name-a1b2c3d4.ap-southeast-1.aws.neon.tech/neondb"
				+ "?sslmode=require&channel_binding=require");
		java.util.Properties parsed = org.postgresql.Driver.parseURL(jdbcUrl, new java.util.Properties());
		assertThat(parsed).isNotNull();
		assertThat(parsed.getProperty("sslmode")).isEqualTo("require");
	}

}
