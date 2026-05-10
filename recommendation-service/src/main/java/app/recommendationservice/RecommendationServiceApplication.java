package app.recommendationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the recommendation service, which serves recommendation IDs per movie
 * (see {@link RecommendationController}).
 */
@SpringBootApplication
public class RecommendationServiceApplication {

    /**
     * Boots the Spring application context and starts the embedded web server on the configured port.
     *
     * @param args standard Spring Boot command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }

}
