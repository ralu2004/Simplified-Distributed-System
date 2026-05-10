package app.movieservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Entry point for the movie service. {@link EnableAspectJAutoProxy} is required so Spring builds
 * CGLIB proxies around beans like {@link app.movieservice.client.RecommendationClient}, allowing
 * Resilience4j {@code @Aspect} beans to intercept {@code @CircuitBreaker} / {@code @TimeLimiter}
 * calls.
 */
@EnableAspectJAutoProxy
@SpringBootApplication
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
