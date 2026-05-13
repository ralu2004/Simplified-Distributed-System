package app.movieservice.observability;

import app.movieservice.resilience.ManualCircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs state transitions from the hand-rolled {@link ManualCircuitBreaker} (this branch).
 */
@Component
public class EventLogger {

	private static final Logger log = LoggerFactory.getLogger(EventLogger.class);
	private final ManualCircuitBreaker circuitBreaker;

	public EventLogger(ManualCircuitBreaker circuitBreaker) {
		this.circuitBreaker = circuitBreaker;
	}

	@PostConstruct
	public void registerListener() {
		circuitBreaker.setTransitionListener(
				message -> log.info(">>> Circuit state changed: {}", message));
	}
}
