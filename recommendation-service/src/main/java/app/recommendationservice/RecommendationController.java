package app.recommendationservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * To run in chaos mode set CHAOS_MODE=true;
 */
@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    @Value("${chaos.mode:false}")  // reads env var CHAOS_MODE
    private boolean chaosEnabled;

    @GetMapping("/{movieId}")
    public List<String> getRecommendations(@PathVariable("movieId") String movieId) {
        if (chaosEnabled) {
            injectChaos();
        }
        return List.of("rec-101", "rec-102", "rec-103");
    }

    private void injectChaos() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (rng.nextDouble() < 0.30) {  // 30% chance
            log.info("Chaos: injecting 503");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chaos: simulated failure");
        }

        if (rng.nextDouble() < 0.40) {  // 40% chance
            int ms = rng.nextInt(3000, 10001);
            log.info("Chaos: sleeping {}ms", ms);
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
