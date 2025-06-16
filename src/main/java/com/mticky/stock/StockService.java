package com.example.mticky.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class providing business logic for stock data operations.
 * Handles formatting, calculations, and validation of stock information.
 */
public class StockService {
  private static final Logger logger = LoggerFactory.getLogger(StockService.class);
  
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  
  private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
  private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("+0.00%;-0.00%");
  private static final DecimalFormat CHANGE_FORMAT = new DecimalFormat("+#,##0.00;-#,##0.00");
  
  static {
    PRICE_FORMAT.setGroupingUsed(true);
    CHANGE_FORMAT.setGroupingUsed(true);
  }
  
  /**
   * Validates a stock symbol format.
   *
   * @param symbol the symbol to validate
   * @return true if the symbol is valid, false otherwise
   */
  public boolean isValidStockSymbol(String symbol) {
    if (symbol == null || symbol.trim().isEmpty()) {
      return false;
    }
    
    String trimmed = symbol.trim().toUpperCase();
    
    // Basic validation: 1-5 alphanumeric+dot characters
    return trimmed.matches("^[A-Z0-9.]{1,5}$");
  }
  
  /**
   * Normalizes a stock symbol by trimming and converting to uppercase.
   *
   * @param symbol the symbol to normalize
   * @return the normalized symbol
   * @throws IllegalArgumentException if the symbol is invalid
   */
  public String normalizeStockSymbol(String symbol) {
    if (!isValidStockSymbol(symbol)) {
      throw new IllegalArgumentException("Invalid stock symbol: " + symbol);
    }
    
    return symbol.trim().toUpperCase();
  }
  
  /**
   * Formats a stock price for display.
   *
   * @param price the price to format
   * @return formatted price string
   */
  public String formatPrice(BigDecimal price) {
    Objects.requireNonNull(price, "Price cannot be null");
    return PRICE_FORMAT.format(price);
  }
  
  /**
   * Formats a price change for display.
   *
   * @param change the change to format
   * @return formatted change string with +/- prefix
   */
  public String formatChange(BigDecimal change) {
    Objects.requireNonNull(change, "Change cannot be null");
    return CHANGE_FORMAT.format(change);
  }
  
  /**
   * Formats a percentage change for display.
   *
   * @param percentChange the percentage change to format
   * @return formatted percentage string with +/- prefix
   */
  public String formatPercentChange(BigDecimal percentChange) {
    Objects.requireNonNull(percentChange, "Percent change cannot be null");
    return PERCENT_FORMAT.format(percentChange.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
  }
  
  /**
   * Formats a timestamp for display.
   *
   * @param timestamp the timestamp to format
   * @return formatted timestamp string
   */
  public String formatTimestamp(LocalDateTime timestamp) {
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    return timestamp.format(TIMESTAMP_FORMATTER);
  }
  
  /**
   * Determines if a stock quote represents a price increase.
   *
   * @param quote the stock quote
   * @return true if the price has increased, false otherwise
   */
  public boolean isPriceUp(StockQuote quote) {
    Objects.requireNonNull(quote, "Quote cannot be null");
    return quote.getChange().compareTo(BigDecimal.ZERO) > 0;
  }
  
  /**
   * Determines if a stock quote represents a price decrease.
   *
   * @param quote the stock quote
   * @return true if the price has decreased, false otherwise
   */
  public boolean isPriceDown(StockQuote quote) {
    Objects.requireNonNull(quote, "Quote cannot be null");
    return quote.getChange().compareTo(BigDecimal.ZERO) < 0;
  }
  
  /**
   * Calculates the absolute percentage change as a positive value.
   *
   * @param quote the stock quote
   * @return absolute percentage change
   */
  public BigDecimal getAbsolutePercentChange(StockQuote quote) {
    Objects.requireNonNull(quote, "Quote cannot be null");
    return quote.getPercentChange().abs();
  }
  
  /**
   * Creates a summary string for a stock quote.
   *
   * @param quote the stock quote
   * @return formatted summary string
   */
  public String createQuoteSummary(StockQuote quote) {
    Objects.requireNonNull(quote, "Quote cannot be null");
    
    return String.format("%s: %s (%s, %s)",
        quote.getSymbol(),
        formatPrice(quote.getCurrentPrice()),
        formatChange(quote.getChange()),
        formatPercentChange(quote.getPercentChange()));
  }
  
  /**
   * Determines if a quote is considered stale based on age.
   *
   * @param quote the stock quote
   * @param maxAgeMinutes maximum age in minutes before considering stale
   * @return true if the quote is stale, false otherwise
   */
  public boolean isQuoteStale(StockQuote quote, int maxAgeMinutes) {
    Objects.requireNonNull(quote, "Quote cannot be null");
    
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(maxAgeMinutes);
    return quote.getLastUpdated().isBefore(threshold);
  }
}
