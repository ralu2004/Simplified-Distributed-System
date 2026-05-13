package app.movieservice;

import app.movieservice.resilience.ManualCircuitBreakerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the movie service (WebFlux).
 */
@SpringBootApplication
@EnableConfigurationProperties(ManualCircuitBreakerProperties.class)
public class MovieServiceApplication {

	/**
	 * Boots the Spring application context and starts the embedded WebFlux server.
	 *
	 * @param args standard Spring Boot command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(MovieServiceApplication.class, args);
	}
}
