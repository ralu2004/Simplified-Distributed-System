package app.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API gateway (Spring Cloud Gateway WebMVC). Routes declared in
 * {@code application.yaml} forward client-facing paths (for example {@code /movies/**}) to the
 * movie service upstream.
 */
@SpringBootApplication
public class GatewayServiceApplication {

	/**
	 * Boots the Spring application context and starts the gateway on the configured server port.
	 *
	 * @param args standard Spring Boot command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
