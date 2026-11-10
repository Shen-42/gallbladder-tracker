package dev.kevin.gallbladder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class GallbladderTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GallbladderTrackerApplication.class, args);
	}

	/** Injected so tests can pin "now". Uses the JVM time zone, which Docker Compose sets via TZ. */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
