package app.movieservice.dto;

import java.util.List;

/**
 * Payload for {@code GET /movies/{id}}.
 *
 * @param id              movie identifier
 * @param title           display title
 * @param description     short synopsis
 * @param recommendations recommendation IDs or fallback labels from {@link app.movieservice.client.RecommendationClient}
 */
public record MovieResponse(String id, String title, String description, List<String> recommendations) {}