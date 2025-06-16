package com.example.mticky.api;

import com.example.mticky.stock.StockQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for interacting with the Finnhub API.
 * Handles rate limiting, retries, and error recovery.
 */
public class FinnhubClient {
  private static final Logger logger = LoggerFactory.getLogger(FinnhubClient.class);
  
  private static final String BASE_URL = "https://finnhub.io/api/v1";
  private static final int MAX_RETRIES = 3;
  private static final int BASE_DELAY_MS = 1000;
  private static final int MAX_DELAY_MS = 30000;
  
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  
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
   * Fetches a stock quote for the given symbol.
   *
   * @param symbol the stock symbol
   * @return CompletableFuture containing the stock quote, or empty if not found
   */
  public CompletableFuture<Optional<StockQuote>> getStockQuote(String symbol) {
    Objects.requireNonNull(symbol, "Symbol cannot be null");
    
    return CompletableFuture.supplyAsync(() -> {
      try {
        return fetchQuoteWithRetry(symbol.toUpperCase(), 0);
      } catch (Exception e) {
        logger.error("Failed to fetch quote for symbol: {}", symbol, e);
        return Optional.<StockQuote>empty();
      }
    });
  }
  
  /**
   * Fetches a stock quote with exponential backoff retry logic.
   *
   * @param symbol the stock symbol
   * @param attempt the current attempt number
   * @return Optional containing the stock quote
   * @throws Exception if all retry attempts are exhausted
   */
  private Optional<StockQuote> fetchQuoteWithRetry(String symbol, int attempt) throws Exception {
    try {
      String url = String.format("%s/quote?symbol=%s&token=%s", getBaseUrl(), symbol, apiKey);
      
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Accept", "application/json")
          .timeout(Duration.ofSeconds(15))
          .GET()
          .build();
      
      logger.debug("Fetching quote for symbol: {} (attempt: {})", symbol, attempt + 1);
      
      HttpResponse<String> response = httpClient.send(request, 
          HttpResponse.BodyHandlers.ofString());
      
      return handleResponse(symbol, response);
      
    } catch (IOException | InterruptedException e) {
      if (attempt < MAX_RETRIES - 1) {
        int delay = calculateDelay(attempt);
        logger.warn("Request failed for symbol {}, retrying in {}ms (attempt {}/{})", 
            symbol, delay, attempt + 1, MAX_RETRIES);
        
        try {
          Thread.sleep(delay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new Exception("Interrupted during retry delay", ie);
        }
        
        return fetchQuoteWithRetry(symbol, attempt + 1);
      } else {
        throw new Exception("Max retries exceeded for symbol: " + symbol, e);
      }
    }
  }
  
  /**
   * Handles the HTTP response and parses the stock quote.
   *
   * @param symbol the stock symbol
   * @param response the HTTP response
   * @return Optional containing the parsed stock quote
   * @throws Exception if response parsing fails
   */
  private Optional<StockQuote> handleResponse(String symbol, HttpResponse<String> response) 
      throws Exception {
    
    int statusCode = response.statusCode();
    String body = response.body();
    
    logger.debug("Response for {}: status={}, body={}", symbol, statusCode, body);
    
    if (statusCode == 429) {
      // Rate limit exceeded
      String retryAfter = response.headers().firstValue("Retry-After").orElse("60");
      logger.warn("Rate limit exceeded for symbol: {}. Retry after: {}s", symbol, retryAfter);
      throw new RateLimitException("Rate limit exceeded. Retry after: " + retryAfter + "s");
    }
    
    if (statusCode == 401) {
      throw new Exception("Authentication failed. Check your Finnhub API key.");
    }
    
    if (statusCode != 200) {
      throw new Exception("HTTP " + statusCode + ": " + body);
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
      
      // Check if the response contains valid data
      if (!root.has("c") || root.get("c").isNull() || root.get("c").asDouble() == 0.0) {
        logger.warn("Invalid or missing price data for symbol: {}", symbol);
        return Optional.empty();
      }
      
      BigDecimal currentPrice = BigDecimal.valueOf(root.get("c").asDouble());
      BigDecimal change = BigDecimal.valueOf(root.get("d").asDouble());
      BigDecimal percentChange = BigDecimal.valueOf(root.get("dp").asDouble());
      LocalDateTime lastUpdated = LocalDateTime.now();
      
      StockQuote quote = new StockQuote(symbol, currentPrice, change, percentChange, lastUpdated);
      
      logger.debug("Parsed quote for {}: {}", symbol, quote);
      return Optional.of(quote);
      
    } catch (Exception e) {
      logger.error("Failed to parse JSON response for symbol: {}", symbol, e);
      logger.debug("Response body: {}", jsonBody);
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
    int exponentialDelay = BASE_DELAY_MS * (int) Math.pow(2, attempt);
    int jitter = ThreadLocalRandom.current().nextInt(0, BASE_DELAY_MS);
    return Math.min(exponentialDelay + jitter, MAX_DELAY_MS);
  }
  
  /**
   * Exception thrown when API rate limits are exceeded.
   */
  public static class RateLimitException extends Exception {
    public RateLimitException(String message) {
      super(message);
    }
  }
}

