package app.recommendationservice;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds the recommendation pool: the most-rated movies from the dataset.
 * Built once at startup from movies.csv (id validity) and ratings.csv (popularity).
 */
@Component
public class MovieCatalog {

    private static final Logger log = LoggerFactory.getLogger(MovieCatalog.class);
    private static final int POOL_SIZE = 50;

    private List<String> popularIds = List.of();

    @PostConstruct
    public void load() throws IOException {
        Set<String> knownMovieIds = new HashSet<>();
        var movies = new ClassPathResource("movies.csv");
        try (var reader = new InputStreamReader(movies.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord row : parser) {
                knownMovieIds.add(row.get("movieId"));
            }
        }

        Map<String, Integer> ratingCounts = new HashMap<>();

        var ratings = new ClassPathResource("ratings.csv");
        try (var reader = new InputStreamReader(ratings.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord row : parser) {
                String id = row.get("movieId");
                if (knownMovieIds.contains(id)) {
                    ratingCounts.merge(id, 1, Integer::sum);
                }
            }
        }

        popularIds = ratingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(POOL_SIZE)
                .map(Map.Entry::getKey)
                .toList();

        log.info("Built popularity pool with top {} movies (from {} rated IDs, {} known movie IDs)",
                popularIds.size(), ratingCounts.size(), knownMovieIds.size());
    }

    /**
     * Picks {@code n} movie IDs at random from the popularity pool, excluding {@code excludedId}.
     *
     * @param n          how many IDs to return
     * @param excludedId id to skip (typically the requested movie itself)
     * @return list of distinct IDs from the pool, may be shorter than {@code n} if the pool is small
     */
    public List<String> sampleRecommendations(int n, String excludedId) {
        List<String> candidates = popularIds.stream()
                .filter(id -> !id.equals(excludedId))
                .toList();

        var rng = ThreadLocalRandom.current();
        // copy then shuffle, then take first n
        List<String> shuffled = new java.util.ArrayList<>(candidates);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            String tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        return shuffled.subList(0, Math.min(n, shuffled.size()));
    }
}