package app.movieservice.client;

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

    public Mono<List<String>> getRecommendations(String movieId) {
        return webClient.get()
                .uri("/recommendations/{id}", movieId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {});                // deserialize the body
    }

}
