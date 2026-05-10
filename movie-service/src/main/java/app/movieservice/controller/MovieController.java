package app.movieservice.controller;

import app.movieservice.catalog.MovieCatalog;
import app.movieservice.dto.MovieData;
import app.movieservice.dto.MovieResponse;
import app.movieservice.client.RecommendationClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST API exposing movie details aggregated with recommendations from the recommendation service.
 */
@RestController
@RequestMapping("/movies")
public class MovieController {

    private final RecommendationClient recommendationClient;
    private final MovieCatalog catalog;

    public MovieController(RecommendationClient recommendationClient, MovieCatalog catalog) {
        this.recommendationClient = recommendationClient;
        this.catalog = catalog;
    }

    /**
     * Returns movie metadata for {@code id} from the local MovieLens-backed catalog plus the
     * recommendation list from the downstream service (or the resilience fallback list when that
     * call fails).
     *
     * @param id movie identifier
     * @return JSON {@link MovieResponse}
     */
    @GetMapping("/{id}")
    public Mono<MovieResponse> getMovie(@PathVariable String id) {
        MovieData data = catalog.findById(id).orElse(
                new MovieData(id, "Movie " + id, java.util.List.of("Unknown"))
        );
        String description = describe(data);

        return recommendationClient.getRecommendations(id)
                .map(recs -> new MovieResponse(id, data.title(), description, recs));
    }

    /** Synthesizes a one-line description from the movie's genre list. */
    private String describe(MovieData data) {
        if (data.genres().isEmpty() || data.genres().get(0).equals("Unknown")
                || data.genres().get(0).equals("(no genres listed)")) {
            return "A movie.";
        }
        String joined = String.join(", ", data.genres()).toLowerCase();
        return "A " + joined + " film.";
    }
}
