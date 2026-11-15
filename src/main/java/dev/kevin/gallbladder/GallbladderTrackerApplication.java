package dev.kevin.gallbladder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

@SpringBootApplication
public class GallbladderTrackerApplication {

	public static void main(String[] args) {
		String databaseUrl = System.getenv("DATABASE_URL");
		if (databaseUrl != null && System.getenv("SPRING_DATASOURCE_URL") == null) {
			// System properties, so they win over the localhost defaults in application.properties.
			datasourceProperties(databaseUrl).forEach(System::setProperty);
		}
		SpringApplication.run(GallbladderTrackerApplication.class, args);
	}

	/**
	 * Turns a host's {@code postgresql://user:password@host:port/database} into Spring's datasource settings,
	 * because the JDBC driver doesn't accept a user and password inside the URL.
	 */
	static Map<String, String> datasourceProperties(String databaseUrl) {
		URI uri = URI.create(databaseUrl);
		String[] userInfo = uri.getRawUserInfo().split(":", 2);
		String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
		return Map.of(
				"spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath(),
				"spring.datasource.username", decode(userInfo[0]),
				"spring.datasource.password", userInfo.length > 1 ? decode(userInfo[1]) : "");
	}

	private static String decode(String s) {
		return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
	}

	/** Injected so tests can pin "now". Uses the JVM time zone, which Docker Compose sets via TZ. */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
