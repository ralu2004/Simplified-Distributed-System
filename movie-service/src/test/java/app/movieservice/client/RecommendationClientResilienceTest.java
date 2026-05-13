package app.movieservice.client;

import app.movieservice.resilience.ManualCircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aop.support.AopUtils.isCglibProxy;

/**
 * Ensures {@link RecommendationClient} uses the hand-rolled {@link ManualCircuitBreaker} so HTTP
 * failures from the recommendation stub yield the catalog fallback instead of propagating errors.
 */
@SpringBootTest
class RecommendationClientResilienceTest {

	private static DisposableServer recommendationStub;

	@DynamicPropertySource
	static void recommendationUrl(DynamicPropertyRegistry registry) {
		recommendationStub = HttpServer.create()
				.host("127.0.0.1")
				.port(0)
				.route(routes -> routes.get("/recommendations/{id}",
						(req, res) -> res.status(503).send()))
				.bindNow();
		registry.add("recommendation.url",
				() -> "http://127.0.0.1:" + recommendationStub.port());
	}

	@AfterAll
	static void stopStub() {
		if (recommendationStub != null) {
			recommendationStub.disposeNow();
		}
	}

	@Autowired
	RecommendationClient recommendationClient;

	@Autowired
	ManualCircuitBreaker manualCircuitBreaker;

	/** This branch does not use Resilience4j AOP; the client is a plain Spring component. */
	@Test
	void clientBeanIsNotCglibResilienceProxy() {
		assertThat(isCglibProxy(recommendationClient)).isFalse();
	}

	@Test
	void circuitBreakerBeanPresent() {
		assertThat(manualCircuitBreaker).isNotNull();
	}

	/** When the recommendation API returns HTTP 503, the fallback list is emitted. */
	@Test
	void serviceUnavailableUsesFallbackRecommendations() {
		StepVerifier.create(recommendationClient.getRecommendations("movie-1"))
				.assertNext(recs -> {
					assertThat(recs).hasSize(5);
					assertThat(recs).allMatch(id -> id.matches("\\d+"));
				})
				.expectComplete()
				.verify(Duration.ofSeconds(5));
	}
}
