package com.example.mticky.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.mticky.stock.StockQuote;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration; // Import Duration
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for FinnhubClient using WireMock.
 */
class FinnhubClientTest {

    private WireMockServer wireMockServer;
    private TestFinnhubClient finnhubClient;
    private static final String TEST_API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8089));
        wireMockServer.start();

        finnhubClient = new TestFinnhubClient(TEST_API_KEY, "http://localhost:8089");
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }

        // Ensure the internal scheduler is shut down after each test
        if (finnhubClient != null) {
            finnhubClient.shutdown();
        }
    }

    @Test
    void testGetStockQuoteSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("token", equalTo(TEST_API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"c\":150.75,\"d\":2.25,\"dp\":1.52,\"h\":151.00,\"l\":148.50,\"o\":149.00,\"pc\":148.50,\"t\":1678886400}")));

        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isPresent());
        StockQuote quote = result.get();

        assertEquals("AAPL", quote.getSymbol());
        assertEquals(new BigDecimal("150.75"), quote.getCurrentPrice());
        assertEquals(new BigDecimal("2.25"), quote.getChange());
        assertEquals(new BigDecimal("1.52"), quote.getPercentChange());
        assertEquals(151.00, quote.getHighPrice(), 0.001);
        assertEquals(148.50, quote.getLowPrice(), 0.001);
        assertEquals(149.00, quote.getOpenPrice(), 0.001);
        assertEquals(148.50, quote.getPreviousClosePrice(), 0.001);
        assertEquals(1678886400L, quote.getTimestamp());
        assertNotNull(quote.getLastUpdated());
    }

    @Test
    void testGetStockQuoteInvalidSymbol() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response for invalid symbol
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("INVALID"))
                .withQueryParam("token", equalTo(TEST_API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"c\":0,\"d\":0,\"dp\":0,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}")));

        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("INVALID");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);

        // Assert that result is empty for a quote with all zeros (invalid symbol response)
        assertFalse(result.isPresent());
    }

    @Test
    void testGetStockQuoteRateLimitExhaustedRetries() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response for rate limit (429)
        // Stubbing multiple times to ensure all retries fail
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("token", equalTo(TEST_API_KEY))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "1") // Set a short retry time for faster test
                        .withBody("Rate limit exceeded. Retry after: 1s")));

        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");

        // The client will attempt retries and eventually complete with Optional.empty()
        // after exhausting MAX_RETRIES. The timeout should be long enough to allow all retries.
        // MAX_RETRIES = 3, each with 1s delay (plus a bit for execution) -> 3-5 seconds roughly.
        Optional<StockQuote> result = future.get(10, TimeUnit.SECONDS); // Increased timeout to ensure retries complete

        assertFalse(result.isPresent(), "Expected Optional.empty() after exhausting rate limit retries.");
    }

    @Test
    void testGetStockQuoteAuthenticationErrorExhaustedRetries() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response for authentication error (401)
        // Stubbing multiple times to ensure all retries fail
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("token", equalTo(TEST_API_KEY))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")));

        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");

        // The client will attempt retries for IOExceptions (which 401 maps to) and eventually
        // complete with Optional.empty() after exhausting MAX_RETRIES.
        // MAX_RETRIES = 3, using calculateDelay (1s, 2s, 4s roughly) -> ~7-10 seconds total for retries.
        Optional<StockQuote> result = future.get(15, TimeUnit.SECONDS); // Increased timeout to ensure retries complete

        assertFalse(result.isPresent(), "Expected Optional.empty() after exhausting authentication error retries.");
    }

    @Test
    void testGetStockQuoteMalformedJson() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response with invalid JSON
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("token", equalTo(TEST_API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ invalid json"))); // Malformed JSON

        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);

        // Should return empty due to JSON parsing error being caught internally
        assertFalse(result.isPresent());
    }

    /**
     * Test-specific FinnhubClient that allows overriding the base URL.
     * This is crucial for directing API calls to WireMock server during tests.
     */
    private static class TestFinnhubClient extends FinnhubClient {
        private final String baseUrl;

        public TestFinnhubClient(String apiKey, String baseUrl) {
            super(apiKey);
            this.baseUrl = baseUrl;
        }

        @Override
        protected String getBaseUrl() {
            return baseUrl;
        }
    }
}

