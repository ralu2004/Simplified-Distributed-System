package app.movieservice.catalog;

import app.movieservice.dto.MovieData;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory catalog of movies loaded from movies.csv at startup.
 * Lookups are O(1) via the id index.
 */
@Component
public class MovieCatalog {

    private static final Logger log = LoggerFactory.getLogger(MovieCatalog.class);

    private final Map<String, MovieData> moviesById = new LinkedHashMap<>();

    @PostConstruct
    public void load() throws IOException {
        var resource = new ClassPathResource("movies.csv");
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord row : parser) {
                String id = row.get("movieId");
                String title = row.get("title");
                List<String> genres = Arrays.asList(row.get("genres").split("\\|"));
                moviesById.put(id, new MovieData(id, title, genres));
            }
        }
        log.info("Loaded {} movies into catalog", moviesById.size());
    }

    /**
     * Looks up a movie by its MovieLens id.
     *
     * @param id movie id (string form)
     * @return populated optional if found, empty if the id is not in the dataset
     */
    public Optional<MovieData> findById(String id) {
        return Optional.ofNullable(moviesById.get(id));
    }

    /**
     * Returns the first {@code n} movie IDs in insertion (file) order. Used as the static
     * fallback list when the recommendation service is unavailable.
     */
    public List<String> firstNIds(int n) {
        return moviesById.keySet().stream().limit(n).toList();
    }
}