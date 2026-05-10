package app.movieservice.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Resilience4j circuit breaker events for observability (state transitions on
 * {@code recommendationCB}).
 */
@Component
public class EventLogger {

    private final CircuitBreakerRegistry registry;
    private static final Logger log = LoggerFactory.getLogger(EventLogger.class);

    public EventLogger(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.registry = circuitBreakerRegistry;
    }

    /**
     * Registers a listener that logs each {@code recommendationCB} state transition after the context
     * starts.
     */
    @PostConstruct
    public void registerListener() {
        registry.circuitBreaker("recommendationCB")
                .getEventPublisher()
                .onStateTransition(event -> log.info(">>> Circuit state changed: {}", event.getStateTransition()));
    }
}
