package app.recommendationservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    @GetMapping("/{movieId}")
    public List<String> getRecommendations(@PathVariable("movieId") String movieId) {
        return List.of("rec-101", "rec-102", "rec-103");
    }
}
