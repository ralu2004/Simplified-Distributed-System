package app.movieservice.client;

import app.movieservice.catalog.MovieCatalog;
import app.movieservice.resilience.ManualCircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP client for the recommendation service. Outbound calls are wrapped by a hand-rolled
 * {@link ManualCircuitBreaker} (timeout + sliding-window trip + half-open recovery) on this branch.
 */
@Component
public class RecommendationClient {

	private final WebClient webClient;
	private final MovieCatalog movieCatalog;
	private final ManualCircuitBreaker circuitBreaker;

	/**
	 * @param builder Spring-managed {@link WebClient.Builder} used to create the client
	 * @param baseUrl root URL of the recommendation service (e.g. {@code http://localhost:8082})
	 */
	public RecommendationClient(WebClient.Builder builder,
			@Value("${recommendation.url}") String baseUrl,
			MovieCatalog movieCatalog,
			ManualCircuitBreaker circuitBreaker) {
		this.webClient = builder.baseUrl(baseUrl).build();
		this.movieCatalog = movieCatalog;
		this.circuitBreaker = circuitBreaker;
	}

	/**
	 * Fetches recommendation IDs for a movie. On timeout, HTTP errors, or while the breaker is open
	 * returns catalog-backed fallback IDs.
	 *
	 * @param movieId movie identifier passed as the path variable {@code id}
	 * @return cold {@link Mono} of recommendation strings from {@code GET /recommendations/{id}}
	 */
	public Mono<List<String>> getRecommendations(String movieId) {
		return circuitBreaker.execute(
				() -> webClient.get()
						.uri("/recommendations/{id}", movieId)
						.retrieve()
						.bodyToMono(new ParameterizedTypeReference<>() {}),
				() -> Mono.just(movieCatalog.firstNIds(5)));
	}
}
