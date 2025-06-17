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
    private final double highPrice; 
    private final double lowPrice;  
    private final double openPrice; 
    private final double previousClosePrice; 
    private final long timestamp; // Finnhub's Unix timestamp
    private final LocalDateTime lastUpdated; // Application's fetch timestamp

    /**
     * Creates a new StockQuote instance.
     *
     * @param symbol the stock symbol
     * @param currentPrice the current stock price
     * @param change the absolute price change
     * @param percentChange the percentage price change
     * @param highPrice the highest price of the day
     * @param lowPrice the lowest price of the day
     * @param openPrice the opening price of the day
     * @param previousClosePrice the previous closing price
     * @param timestamp the Finnhub Unix timestamp for the quote
     * @param lastUpdated the application-level timestamp when this quote was last updated
     */
    public StockQuote(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("c") BigDecimal currentPrice,
            @JsonProperty("d") BigDecimal change,
            @JsonProperty("dp") BigDecimal percentChange,
            @JsonProperty("h") double highPrice,          
            @JsonProperty("l") double lowPrice,           
            @JsonProperty("o") double openPrice,          
            @JsonProperty("pc") double previousClosePrice, 
            @JsonProperty("t") long timestamp,            
            @JsonProperty("lastUpdated") LocalDateTime lastUpdated) {
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.currentPrice = Objects.requireNonNull(currentPrice, "Current price cannot be null");
        this.change = Objects.requireNonNull(change, "Change cannot be null");
        this.percentChange = Objects.requireNonNull(percentChange, "Percent change cannot be null");
        this.highPrice = highPrice; 
        this.lowPrice = lowPrice;
        this.openPrice = openPrice;
        this.previousClosePrice = previousClosePrice;
        this.timestamp = timestamp;
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "Last updated cannot be null");
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getChange() {
        return change;
    }

    public BigDecimal getPercentChange() {
        return percentChange;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getPreviousClosePrice() {
        return previousClosePrice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Creates a new StockQuote with updated application-level timestamp.
     * All other fields remain the same.
     *
     * @param newLastUpdated the new application-level timestamp
     * @return a new StockQuote instance with updated timestamp
     */
    public StockQuote withUpdatedTimestamp(LocalDateTime newLastUpdated) {
        return new StockQuote(symbol, currentPrice, change, percentChange,
                              highPrice, lowPrice, openPrice, previousClosePrice, timestamp, 
                              newLastUpdated); // Update only this one
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StockQuote that = (StockQuote) obj;
        return Double.compare(that.highPrice, highPrice) == 0 &&
               Double.compare(that.lowPrice, lowPrice) == 0 &&
               Double.compare(that.openPrice, openPrice) == 0 &&
               Double.compare(that.previousClosePrice, previousClosePrice) == 0 &&
               timestamp == that.timestamp &&
               Objects.equals(symbol, that.symbol) &&
               Objects.equals(currentPrice, that.currentPrice) &&
               Objects.equals(change, that.change) &&
               Objects.equals(percentChange, that.percentChange) &&
               Objects.equals(lastUpdated, that.lastUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, currentPrice, change, percentChange, highPrice, lowPrice, openPrice, previousClosePrice, timestamp, lastUpdated);
    }

    @Override
    public String toString() {
        return String.format("StockQuote{" +
                             "symbol='%s', currentPrice=%s, change=%s, percentChange=%s, " +
                             "highPrice=%s, lowPrice=%s, openPrice=%s, previousClosePrice=%s, " +
                             "timestamp=%d, lastUpdated=%s}",
                             symbol, currentPrice, change, percentChange,
                             highPrice, lowPrice, openPrice, previousClosePrice,
                             timestamp, lastUpdated);
    }
}

