package app.movieservice.controller;

import app.movieservice.dto.MovieResponse;
import app.movieservice.client.RecommendationClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/movies")
public class MovieController {

    private final RecommendationClient recommendationClient;

    public MovieController(RecommendationClient recommendationClient) {
        this.recommendationClient = recommendationClient;
    }

    @GetMapping("/{id}")
    public Mono<MovieResponse> getMovie(@PathVariable String id) {
        return recommendationClient.getRecommendations(id)
                .map(recs -> new MovieResponse(id, "Movie " + id, "A great movie.", recs));
    }
}
