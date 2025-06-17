package com.lyubomirbozhinov.mticky.api;

import com.lyubomirbozhinov.mticky.stock.StockQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom; // <--- ADDED THIS IMPORT
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for interacting with the Finnhub API.
 * Handles rate limiting, retries, and error recovery.
 */
public class FinnhubClient {
    private static final Logger logger = LoggerFactory.getLogger(FinnhubClient.class);

    private static final String BASE_URL = "https://finnhub.io/api/v1";
    private static final int MAX_RETRIES = 3;

    // Pattern to extract retry duration from message if Retry-After header is missing
    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("Retry after: (\\d+)s");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    // Dedicated scheduler for non-blocking delayed retries
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();

    // Constants for exponential backoff     
    private static final int BASE_DELAY_MS = 1000; // 1 second
    private static final int MAX_DELAY_MS = 30000; // 30 seconds

    /**
     * Creates a new FinnhubClient instance.
     *
     * @param apiKey the Finnhub API key
     */
    public FinnhubClient(String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Gets the base URL for API requests. Can be overridden for testing.
     *
     * @return the base URL
     */
    protected String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Fetches a stock quote for the given symbol asynchronously.
     * Implements asynchronous retry logic with exponential backoff for rate limits.
     *
     * @param symbol the stock symbol
     * @return CompletableFuture containing the stock quote, or empty if not found after retries
     */
    public CompletableFuture<Optional<StockQuote>> getStockQuote(String symbol) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        return fetchQuoteWithRetry(symbol.toUpperCase(), 0);
    }

    /**
     * Executes a single HTTP request to fetch a stock quote.
     * This method is designed to be called within a CompletableFuture.supplyAsync().
     *
     * @param symbol The stock symbol.
     * @return An Optional<StockQuote> if successful.
     * @throws RateLimitException   If rate limit is hit.
     * @throws IOException          For other network/IO errors.
     * @throws InterruptedException If the thread is interrupted during the HTTP call.
     */
    private Optional<StockQuote> performSingleQuoteFetch(String symbol) throws IOException, InterruptedException, RateLimitException {
        String url = String.format("%s/quote?symbol=%s&token=%s", getBaseUrl(), symbol, apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        logger.debug("Executing HTTP request for symbol: {}", symbol);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(symbol, response);
    }


    /**
     * Internal method to fetch a stock quote with exponential backoff retry logic.
     * This method is designed to be fully asynchronous.
     *
     * @param symbol the stock symbol
     * @param attempt the current attempt number (0-indexed)
     * @return CompletableFuture containing the stock quote, or empty if not found or max retries exceeded
     */
    private CompletableFuture<Optional<StockQuote>> fetchQuoteWithRetry(String symbol, int attempt) {
        // Step 1: Perform the single HTTP fetch asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performSingleQuoteFetch(symbol);
            } catch (RateLimitException e) {
                throw new CompletionException(e);
            } catch (IOException | InterruptedException e) {
                throw new CompletionException("Network/IO error fetching quote for " + symbol, e);
            }
        }).exceptionallyCompose(ex -> {
            // Step 2: Handle exceptions and orchestrate retries
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

            if (attempt < MAX_RETRIES - 1) { // -1 because attempt is 0-indexed, and MAX_RETRIES is total attempts including first
                long delayMs;

                if (cause instanceof RateLimitException) {
                    RateLimitException rle = (RateLimitException) cause;
                    delayMs = TimeUnit.SECONDS.toMillis(Math.min(rle.getRetryAfterSeconds(), 300)); // Cap delay at 5 min
                    logger.warn("Rate limit hit for {}. Retrying after {}ms (attempt {}/{})",
                            symbol, delayMs, attempt + 1, MAX_RETRIES);
                } else if (cause instanceof IOException || cause instanceof InterruptedException) {
                    delayMs = calculateDelay(attempt); // Use exponential backoff for other transient errors
                    logger.warn("Transient error for symbol {}, retrying in {}ms (attempt {}/{})",
                            symbol, delayMs, attempt + 1, MAX_RETRIES);
                } else {
                    // For non-retryable or unhandled exceptions, just log and complete
                    logger.error("An unhandled non-retryable error occurred while fetching quote for {}: {}", symbol, cause.getMessage(), ex);
                    return CompletableFuture.completedFuture(Optional.empty());
                }

                // Schedule a delayed recursive call to fetchQuoteWithRetry
                // The delay happens asynchronously, freeing up the current thread
                return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, retryScheduler))
                        .thenComposeAsync(__ -> fetchQuoteWithRetry(symbol, attempt + 1), retryScheduler);
            } else {
                logger.error("Max retries ({}) reached for {}. Giving up on fetching quote.", MAX_RETRIES, symbol);
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    /**
     * Handles the HTTP response and parses the stock quote.
     *
     * @param symbol the stock symbol
     * @param response the HTTP response
     * @return Optional containing the parsed stock quote
     * @throws IOException       if a non-recoverable HTTP error occurs
     * @throws RateLimitException if a rate limit error is encountered
     */
    private Optional<StockQuote> handleResponse(String symbol, HttpResponse<String> response)
            throws IOException, RateLimitException {

        int statusCode = response.statusCode();
        String body = response.body();

        logger.debug("Response for {}: status={}, body={}", symbol, statusCode, body);

        if (statusCode == 429) { 
            int retryAfter = 60; // Default if header/body doesn't specify
            Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After");
            if (retryAfterHeader.isPresent()) {
                try {
                    retryAfter = Integer.parseInt(retryAfterHeader.get());
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse Retry-After header '{}' for {}. Using default {}s.", retryAfterHeader.get(), symbol, retryAfter);
                }
            } else {
                // Fallback to parsing from body if header isn't present
                Matcher matcher = RETRY_AFTER_PATTERN.matcher(body);
                if (matcher.find()) {
                    try {
                        retryAfter = Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse retry duration from body for {}: '{}'. Using default {}s.", symbol, body, retryAfter);
                    }
                }
            }
            throw new RateLimitException("Rate limit exceeded. Retry after: " + retryAfter + "s", retryAfter);
        }

        if (statusCode == 401) {
            throw new IOException("Authentication failed. Check your Finnhub API key. Status: " + statusCode);
        }

        if (statusCode != 200) {
            throw new IOException("HTTP Error " + statusCode + ": " + body);
        }

        return parseStockQuote(symbol, body);
    }

    /**
     * Parses the JSON response into a StockQuote object.
     *
     * @param symbol the stock symbol
     * @param jsonBody the JSON response body
     * @return Optional containing the parsed stock quote
     */
    private Optional<StockQuote> parseStockQuote(String symbol, String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            // Finnhub returns an empty JSON object or all zeros if the symbol is not found or no data
            if (root.isEmpty() || !root.has("c") || root.get("c").isNull() || root.get("c").asDouble() == 0.0) {
                logger.info("No meaningful quote data (empty or zero price) for symbol: {}. Response: {}", symbol, jsonBody);
                return Optional.empty();
            }

            BigDecimal currentPrice = BigDecimal.valueOf(root.get("c").asDouble());
            BigDecimal change = root.has("d") && !root.get("d").isNull() ? BigDecimal.valueOf(root.get("d").asDouble()) : BigDecimal.ZERO;
            BigDecimal percentChange = root.has("dp") && !root.get("dp").isNull() ? BigDecimal.valueOf(root.get("dp").asDouble()) : BigDecimal.ZERO;

            // Extract new fields, providing default values (0.0 or 0L) if they are missing or null
            double highPrice = root.has("h") && !root.get("h").isNull() ? root.get("h").asDouble() : 0.0;
            double lowPrice = root.has("l") && !root.get("l").isNull() ? root.get("l").asDouble() : 0.0;
            double openPrice = root.has("o") && !root.get("o").isNull() ? root.get("o").asDouble() : 0.0;
            double previousClosePrice = root.has("pc") && !root.get("pc").isNull() ? root.get("pc").asDouble() : 0.0;
            long timestamp = root.has("t") && !root.get("t").isNull() ? root.get("t").asLong() : 0L;

            StockQuote quote = new StockQuote(
                symbol,
                currentPrice,
                change,
                percentChange,
                highPrice,        
                lowPrice,         
                openPrice,        
                previousClosePrice, 
                timestamp,        
                LocalDateTime.now() // Application's fetch timestamp
            );

            logger.debug("Parsed quote for {}: {}", symbol, quote);
            return Optional.of(quote);

        } catch (Exception e) {
            logger.error("Failed to parse JSON response for symbol: {}. Response: {}", symbol, jsonBody, e);
            return Optional.empty();
        }
    }

    /**
     * Calculates the delay for exponential backoff with jitter.
     *
     * @param attempt the current attempt number
     * @return delay in milliseconds
     */
    private int calculateDelay(int attempt) {
        // Cap the exponential component to prevent overflow and overly long delays (max 6)
        long exponentialDelay = BASE_DELAY_MS * (long) Math.pow(2, Math.min(attempt, 6));
        int jitter = ThreadLocalRandom.current().nextInt(0, BASE_DELAY_MS);
        
        return (int) Math.min(exponentialDelay + jitter, MAX_DELAY_MS);
    }

    /**
     * Exception thrown when API rate limits are exceeded.
     */
    public static class RateLimitException extends IOException {
        private final int retryAfterSeconds;

        public RateLimitException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * Shuts down the internal retry scheduler. Call this when the application is closing.
     */
    public void shutdown() {
        if (!retryScheduler.isShutdown()) {
            retryScheduler.shutdown(); // Use shutdown() for graceful termination
            try {
                if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    retryScheduler.shutdownNow(); // Force shutdown if not terminated gracefully
                }
            } catch (InterruptedException e) {
                logger.warn("FinnhubClient retry scheduler shutdown interrupted.", e);
                retryScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("FinnhubClient retryScheduler shut down.");
        }
    }
}

