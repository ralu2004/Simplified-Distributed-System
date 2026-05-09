package app.movieservice.client;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aop.support.AopUtils.isCglibProxy;

/**
 * Ensures {@link RecommendationClient} calls go through the Resilience4j proxy so HTTP failures
 * from the recommendation service are handled by the configured fallback instead of erroring the
 * movie-service caller.
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

    @Test
    void clientBeanIsProxiedForResilience4j() {
        assertThat(isCglibProxy(recommendationClient)).isTrue();
    }

    @Test
    void serviceUnavailableUsesFallbackRecommendations() {
        StepVerifier.create(recommendationClient.getRecommendations("movie-1"))
                .expectNext(List.of("Movie-1", "Movie-2", "Movie-3"))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
