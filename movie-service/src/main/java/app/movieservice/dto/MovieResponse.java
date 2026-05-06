package app.movieservice.dto;

import java.util.List;

public record MovieResponse(String id, String title, String description, List<String> recommendations) {}