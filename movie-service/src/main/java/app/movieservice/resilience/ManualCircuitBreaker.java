package app.movieservice.resilience;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Count-based sliding window circuit breaker with CLOSED / OPEN / HALF_OPEN, plus a strict
 * {@link Mono#timeout(Duration)} on each remote attempt. All mutable state is guarded by {@link #lock}.
 */
@Component
public final class ManualCircuitBreaker {

	/** Normal: calls pass through. OPEN: fail fast (no remote). HALF_OPEN: limited probes. */
	public enum State {
		CLOSED,
		OPEN,
		HALF_OPEN
	}

	private final Object lock = new Object();
	private State state = State.CLOSED;

	/** Latest outcomes in CLOSED (true = remote succeeded); size capped by {@code slidingWindowSize}. */
	private final Deque<Boolean> outcomes = new ArrayDeque<>();

	/** Applied to the remote Mono. */
	private final Duration callTimeout;

	/** Max outcomes kept for CLOSED failure-rate calculation. */
	private final int slidingWindowSize;

	/** Minimum samples before CLOSED can trip to OPEN. */
	private final int minimumNumberOfCalls;

	/** Open when failures are at least this percentage of the window (0–100). */
	private final int failureRateThreshold;

	/** How long OPEN rejects remotes before trying HALF_OPEN again. */
	private final Duration waitInOpen;

	/** Caps remote starts per HALF_OPEN cycle; returns to CLOSED after this many successful completions. */
	private final int permittedHalfOpen;

	/** Monotonic nanos when entered OPEN; used with {@code waitInOpen} for cooldown. */
	private long openedAtNanos;

	/** Remote attempts started in the current HALF_OPEN cycle (capped by {@code permittedHalfOpen}). */
	private int halfOpenRemoteStarts;

	/** Successful remote completions in HALF_OPEN; reaching {@code permittedHalfOpen} closes the circuit. */
	private int halfOpenSuccesses;

	private volatile Consumer<String> transitionListener = s -> { };

	public ManualCircuitBreaker(ManualCircuitBreakerProperties properties) {
		this.callTimeout = properties.getCallTimeout();
		this.slidingWindowSize = properties.getSlidingWindowSize();
		this.minimumNumberOfCalls = properties.getMinimumNumberOfCalls();
		this.failureRateThreshold = properties.getFailureRateThreshold();
		this.waitInOpen = properties.getWaitDurationInOpenState();
		this.permittedHalfOpen = properties.getPermittedNumberOfCallsInHalfOpenState();
	}

	/** Optional log hook (e.g. {@code EventLogger}); must be cheap and non-blocking. */
	public void setTransitionListener(Consumer<String> listener) {
		this.transitionListener = listener != null ? listener : s -> { };
	}

	/** Current state for tests or dashboards (short lock). */
	public State getState() {
		synchronized (lock) {
			return state;
		}
	}

	/**
	 * Runs {@code remote} when the breaker permits a call; otherwise (or after timeout / error)
	 * returns {@code fallback}. Outcomes are recorded for CLOSED / HALF_OPEN logic.
	 */
	public <T> Mono<T> execute(Supplier<Mono<T>> remote, Supplier<Mono<T>> fallback) {
		// Defer so each subscription re-evaluates permit and timeout (matches reactive cold Mono usage).
		return Mono.defer(() -> {
			boolean permit;
			synchronized (lock) {
				permit = permitRemote();
			}
			if (!permit) {
				return fallback.get();
			}
			return remote.get()
					.timeout(callTimeout)
					.flatMap(value -> {
						synchronized (lock) {
							onRemoteSuccess();
						}
						return Mono.just(value);
					})
					.onErrorResume(error -> {
						synchronized (lock) {
							onRemoteFailure();
						}
						return fallback.get();
					});
		});
	}

	/**
	 * Caller must hold {@link #lock}. Decides whether this request may hit the remote: short-circuits
	 * OPEN during cooldown; moves OPEN → HALF_OPEN after wait; limits parallel HALF_OPEN starts.
	 */
	private boolean permitRemote() {
		if (state == State.OPEN) {
			long elapsed = System.nanoTime() - openedAtNanos;
			if (elapsed < waitInOpen.toNanos()) {
				return false;
			}
			state = State.HALF_OPEN;
			halfOpenRemoteStarts = 0;
			halfOpenSuccesses = 0;
			fire("State transition from OPEN to HALF_OPEN");
		}
		if (state == State.HALF_OPEN) {
			if (halfOpenRemoteStarts >= permittedHalfOpen) {
				return false;
			}
			halfOpenRemoteStarts++;
			return true;
		}
		return state == State.CLOSED;
	}

	/** Caller must hold {@link #lock}. Records a successful remote completion for CLOSED or HALF_OPEN. */
	private void onRemoteSuccess() {
		if (state == State.CLOSED) {
			recordClosedOutcome(true);
			evaluateClosedWindow();
			return;
		}
		if (state == State.HALF_OPEN) {
			halfOpenSuccesses++;
			if (halfOpenSuccesses >= permittedHalfOpen) {
				state = State.CLOSED;
				outcomes.clear();
				halfOpenRemoteStarts = 0;
				halfOpenSuccesses = 0;
				fire("State transition from HALF_OPEN to CLOSED");
			}
		}
	}

	/** Caller must hold {@link #lock}. Timeout, HTTP error, or half-open probe failure land here. */
	private void onRemoteFailure() {
		if (state == State.CLOSED) {
			recordClosedOutcome(false);
			evaluateClosedWindow();
			return;
		}
		if (state == State.HALF_OPEN) {
			transitionToOpen("State transition from HALF_OPEN to OPEN");
		}
	}

	/** Append one CLOSED-window sample; evict oldest when over capacity. */
	private void recordClosedOutcome(boolean success) {
		while (outcomes.size() >= slidingWindowSize) {
			outcomes.removeFirst();
		}
		outcomes.addLast(success);
	}

	/** Trip CLOSED → OPEN when the window is large enough and failure rate crosses the threshold. */
	private void evaluateClosedWindow() {
		if (outcomes.size() < minimumNumberOfCalls) {
			return;
		}
		long failures = outcomes.stream().filter(b -> !b).count();
		int ratePercent = (int) (failures * 100L / outcomes.size());
		if (ratePercent >= failureRateThreshold) {
			transitionToOpen("State transition from CLOSED to OPEN");
		}
	}

	/** Reset window and half-open counters; refresh {@link #openedAtNanos} for the OPEN cooldown. */
	private void transitionToOpen(String message) {
		state = State.OPEN;
		openedAtNanos = System.nanoTime();
		outcomes.clear();
		halfOpenRemoteStarts = 0;
		halfOpenSuccesses = 0;
		fire(message);
	}

	/** Forwards human-readable transition lines to {@link #transitionListener}. */
	private void fire(String message) {
		transitionListener.accept(message);
	}
}
