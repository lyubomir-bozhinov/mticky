package com.example.mticky.stock;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable data class representing a stock quote with price information.
 * Uses BigDecimal for precise financial calculations.
 */
public final class StockQuote {
  private final String symbol;
  private final BigDecimal currentPrice;
  private final BigDecimal change;
  private final BigDecimal percentChange;
  private final LocalDateTime lastUpdated;
  
  /**
   * Creates a new StockQuote instance.
   *
   * @param symbol the stock symbol
   * @param currentPrice the current stock price
   * @param change the absolute price change
   * @param percentChange the percentage price change
   * @param lastUpdated the timestamp when this quote was last updated
   */
  public StockQuote(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("c") BigDecimal currentPrice,
      @JsonProperty("d") BigDecimal change,
      @JsonProperty("dp") BigDecimal percentChange,
      @JsonProperty("lastUpdated") LocalDateTime lastUpdated) {
    this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
    this.currentPrice = Objects.requireNonNull(currentPrice, "Current price cannot be null");
    this.change = Objects.requireNonNull(change, "Change cannot be null");
    this.percentChange = Objects.requireNonNull(percentChange, "Percent change cannot be null");
    this.lastUpdated = Objects.requireNonNull(lastUpdated, "Last updated cannot be null");
  }
  
  /**
   * Gets the stock symbol.
   *
   * @return the stock symbol
   */
  public String getSymbol() {
    return symbol;
  }
  
  /**
   * Gets the current stock price.
   *
   * @return the current price
   */
  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }
  
  /**
   * Gets the absolute price change.
   *
   * @return the price change
   */
  public BigDecimal getChange() {
    return change;
  }
  
  /**
   * Gets the percentage price change.
   *
   * @return the percentage change
   */
  public BigDecimal getPercentChange() {
    return percentChange;
  }
  
  /**
   * Gets the timestamp when this quote was last updated.
   *
   * @return the last updated timestamp
   */
  public LocalDateTime getLastUpdated() {
    return lastUpdated;
  }
  
  /**
   * Creates a new StockQuote with updated timestamp.
   *
   * @param newTimestamp the new timestamp
   * @return a new StockQuote instance with updated timestamp
   */
  public StockQuote withUpdatedTimestamp(LocalDateTime newTimestamp) {
    return new StockQuote(symbol, currentPrice, change, percentChange, newTimestamp);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    StockQuote that = (StockQuote) obj;
    return Objects.equals(symbol, that.symbol)
        && Objects.equals(currentPrice, that.currentPrice)
        && Objects.equals(change, that.change)
        && Objects.equals(percentChange, that.percentChange)
        && Objects.equals(lastUpdated, that.lastUpdated);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(symbol, currentPrice, change, percentChange, lastUpdated);
  }
  
  @Override
  public String toString() {
    return String.format("StockQuote{symbol='%s', price=%s, change=%s, percentChange=%s, updated=%s}",
        symbol, currentPrice, change, percentChange, lastUpdated);
  }
}