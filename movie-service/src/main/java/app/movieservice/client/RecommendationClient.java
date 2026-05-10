package app.movieservice.client;

import app.movieservice.catalog.MovieCatalog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP client for the recommendation service. Calls are protected by Resilience4j (circuit breaker
 * {@code recommendationCB}, shared time limiter) applied via Spring AOP on the proxied bean.
 */
@Component
public class RecommendationClient {

    private final WebClient webClient;
    private final MovieCatalog movieCatalog;

    /**
     * @param builder Spring-managed {@link WebClient.Builder} used to create the client
     * @param baseUrl root URL of the recommendation service (e.g. {@code http://localhost:8082})
     */
    public RecommendationClient(WebClient.Builder builder,
                                @Value("${recommendation.url}") String baseUrl,
                                MovieCatalog movieCatalog) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.movieCatalog = movieCatalog;
    }

    /**
     * Fetches recommendation IDs for a movie. Failures and timeouts are handled by Resilience4j;
     * see {@link #fallback(String, Throwable)}.
     *
     * @param movieId movie identifier passed as the path variable {@code id}
     * @return cold {@link Mono} of recommendation strings from {@code GET /recommendations/{id}}
     */
    @CircuitBreaker(name = "recommendationCB", fallbackMethod = "fallback")
    @TimeLimiter(name = "recommendationCB")
    public Mono<List<String>> getRecommendations(String movieId) {
        return webClient.get()
                .uri("/recommendations/{id}", movieId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    /**
     * Fallback for {@link #getRecommendations(String)} when the circuit reports an error, the breaker
     * is open, or the time limiter fires.
     *
     * @param movieId same argument as the primary method (unused; required for Resilience4j signature)
     * @param t       failure that triggered the fallback
     * @return a stable list of movie IDs from the local dataset standing in for live recommendations
     */
    public Mono<List<String>> fallback(String movieId, Throwable t) {
        return Mono.just(movieCatalog.firstNIds(5));
    }
}