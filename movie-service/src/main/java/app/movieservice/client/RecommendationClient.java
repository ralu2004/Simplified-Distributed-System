package app.movieservice.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class RecommendationClient {

    private final WebClient webClient;

    public RecommendationClient(WebClient.Builder builder, @Value("${recommendation.url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = "recommendationCB", fallbackMethod = "fallback")
    @TimeLimiter(name = "recommendationCB")
    public Mono<List<String>> getRecommendations(String movieId) {
        return webClient.get()
                .uri("/recommendations/{id}", movieId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {});                // deserialize the body
    }

    public Mono<List<String>> fallback(String movieId, Throwable t) {
        return Mono.just(List.of("Movie-1", "Movie-2", "Movie-3"));
    }
}
