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
 * REST API that returns recommendation identifiers for a given movie ID.
 *
 * <p>Chaos mode: set the environment variable {@code CHAOS_MODE=true} (binding
 * to {@code chaos.mode}) to randomly simulate HTTP 503 and/or multi-second latency on some requests
 * (see {@link #injectChaos()}).
 */
@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    /** When {@code true}, chaos injection runs on each request (env {@code CHAOS_MODE}). */
    @Value("${chaos.mode:false}")
    private boolean chaosEnabled;

    /**
     * Returns a fixed recommendation list for the given movie unless chaos mode introduces failure
     * or delay beforehand.
     *
     * @param movieId movie identifier from the path
     * @return list of recommendation keys
     */
    @GetMapping("/{movieId}")
    public List<String> getRecommendations(@PathVariable("movieId") String movieId) {
        if (chaosEnabled) {
            injectChaos();
        }
        return List.of("rec-101", "rec-102", "rec-103");
    }

    /**
     * Randomly triggers failure modes independently: about 30% chance of throwing HTTP 503, and about
     * 40% chance of sleeping 3–10 seconds.
     */
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
