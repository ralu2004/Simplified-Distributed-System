package app.movieservice.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds {@code manual.circuit-breaker.*} from {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "manual.circuit-breaker")
public class ManualCircuitBreakerProperties {

	/** Upper bound on each remote Mono; counts as failure if exceeded. */
	private Duration callTimeout = Duration.ofMillis(1500);

	/** How many recent outcomes (success/fail) CLOSED uses before deleting oldest. */
	private int slidingWindowSize = 10;

	/** Do not open from CLOSED until the window has at least this many completed calls. */
	private int minimumNumberOfCalls = 5;

	/** Open CLOSED when failure share in the window is at least this percent (0–100). */
	private int failureRateThreshold = 50;

	/** After OPEN, block remote calls until this much time passes, then move to HALF_OPEN. */
	private Duration waitDurationInOpenState = Duration.ofSeconds(10);

	/** In HALF_OPEN, need this many successful remote completions in a row to return to CLOSED. */
	private int permittedNumberOfCallsInHalfOpenState = 2;

	public Duration getCallTimeout() {
		return callTimeout;
	}

	public void setCallTimeout(Duration callTimeout) {
		this.callTimeout = callTimeout;
	}

	public int getSlidingWindowSize() {
		return slidingWindowSize;
	}

	public void setSlidingWindowSize(int slidingWindowSize) {
		this.slidingWindowSize = slidingWindowSize;
	}

	public int getMinimumNumberOfCalls() {
		return minimumNumberOfCalls;
	}

	public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
		this.minimumNumberOfCalls = minimumNumberOfCalls;
	}

	public int getFailureRateThreshold() {
		return failureRateThreshold;
	}

	public void setFailureRateThreshold(int failureRateThreshold) {
		this.failureRateThreshold = failureRateThreshold;
	}

	public Duration getWaitDurationInOpenState() {
		return waitDurationInOpenState;
	}

	public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
		this.waitDurationInOpenState = waitDurationInOpenState;
	}

	public int getPermittedNumberOfCallsInHalfOpenState() {
		return permittedNumberOfCallsInHalfOpenState;
	}

	public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
		this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
	}
}
