package app.movieservice.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EventLogger {

    private final CircuitBreakerRegistry registry;
    private static final Logger log = LoggerFactory.getLogger(EventLogger.class);

    public EventLogger(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.registry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void registerListener() {
        registry.circuitBreaker("recommendationCB")
                .getEventPublisher()
                .onStateTransition(event -> log.info(">>> Circuit state changed: {}", event.getStateTransition()));
    }
}
