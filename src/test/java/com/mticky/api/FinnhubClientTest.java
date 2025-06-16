package com.example.mticky.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.mticky.stock.StockQuote;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
        // Start WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8089));
        wireMockServer.start();
        
        // Create client with mock server URL
        finnhubClient = new TestFinnhubClient(TEST_API_KEY, "http://localhost:8089");
    }
    
    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
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
                .withBody("{\"c\":150.75,\"d\":2.25,\"dp\":1.52,\"h\":151.00,\"l\":148.50,\"o\":149.00,\"pc\":148.50}")));
        
        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);
        
        assertTrue(result.isPresent());
        StockQuote quote = result.get();
        
        assertEquals("AAPL", quote.getSymbol());
        assertEquals(new BigDecimal("150.75"), quote.getCurrentPrice());
        assertEquals(new BigDecimal("2.25"), quote.getChange());
        assertEquals(new BigDecimal("1.52"), quote.getPercentChange());
        assertNotNull(quote.getLastUpdated());
    }
    
    @Test
    void testGetStockQuoteInvalidSymbol() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response for invalid symbol
        wireMockServer.stubFor(get(urlPathEqualTo("/quote")) // FIX: Changed path from /api/v1/quote
            .withQueryParam("symbol", equalTo("INVALID"))
            .withQueryParam("token", equalTo(TEST_API_KEY))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"c\":0,\"d\":0,\"dp\":0,\"h\":0,\"l\":0,\"o\":0,\"pc\":0}"))); // Finnhub often returns 0s for invalid symbols
        
        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("INVALID");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);
        
        // Assert that result is empty for a quote with all zeros (invalid symbol response)
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetStockQuoteRateLimit() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response for rate limit
        wireMockServer.stubFor(get(urlPathEqualTo("/quote")) // FIX: Changed path from /api/v1/quote
            .withQueryParam("symbol", equalTo("AAPL"))
            .withQueryParam("token", equalTo(TEST_API_KEY))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Retry-After", "60")
                .withBody("Rate limit exceeded")));
        
        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);
        
        // Should return empty due to rate limit
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetStockQuoteAuthenticationError() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response for authentication error
        wireMockServer.stubFor(get(urlPathEqualTo("/quote")) // FIX: Changed path from /api/v1/quote
            .withQueryParam("symbol", equalTo("AAPL"))
            .withQueryParam("token", equalTo(TEST_API_KEY))
            .willReturn(aResponse()
                .withStatus(401)
                .withBody("Unauthorized")));
        
        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);
        
        // Should return empty due to auth error
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetStockQuoteMalformedJson() throws ExecutionException, InterruptedException, TimeoutException {
        // Setup mock response with invalid JSON
        wireMockServer.stubFor(get(urlPathEqualTo("/quote")) // FIX: Changed path from /api/v1/quote
            .withQueryParam("symbol", equalTo("AAPL"))
            .withQueryParam("token", equalTo(TEST_API_KEY))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ invalid json")));
        
        CompletableFuture<Optional<StockQuote>> future = finnhubClient.getStockQuote("AAPL");
        Optional<StockQuote> result = future.get(5, TimeUnit.SECONDS);
        
        // Should return empty due to JSON parsing error
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

