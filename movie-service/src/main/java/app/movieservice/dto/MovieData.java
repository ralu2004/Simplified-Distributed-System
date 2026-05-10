package app.movieservice.dto;

import java.util.List;

/**
 * Catalog entry loaded from movies.csv. Internal representation in MovieCatalog.
 */
public record MovieData(String id, String title, List<String> genres) {}