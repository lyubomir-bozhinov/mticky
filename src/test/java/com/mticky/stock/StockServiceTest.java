package com.example.mticky.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StockService.
 */
class StockServiceTest {
  
  private StockService stockService;
  
  @BeforeEach
  void setUp() {
    stockService = new StockService();
  }
  
  @Test
  void testIsValidStockSymbol() {
    // Valid symbols
    assertTrue(stockService.isValidStockSymbol("AAPL"));
    assertTrue(stockService.isValidStockSymbol("GOOGL"));
    assertTrue(stockService.isValidStockSymbol("MSFT"));
    assertTrue(stockService.isValidStockSymbol("BRK.A")); // Will be normalized
    assertTrue(stockService.isValidStockSymbol("  TSLA  ")); // With whitespace
    
    // Invalid symbols
    assertFalse(stockService.isValidStockSymbol(null));
    assertFalse(stockService.isValidStockSymbol(""));
    assertFalse(stockService.isValidStockSymbol("   "));
    assertFalse(stockService.isValidStockSymbol("TOOLONG"));
    assertFalse(stockService.isValidStockSymbol("123456"));
  }
  
  @Test
  void testNormalizeStockSymbol() {
    assertEquals("AAPL", stockService.normalizeStockSymbol("aapl"));
    assertEquals("GOOGL", stockService.normalizeStockSymbol("  GOOGL  "));
    assertEquals("MSFT", stockService.normalizeStockSymbol("msft"));
    
    assertThrows(IllegalArgumentException.class, 
        () -> stockService.normalizeStockSymbol(""));
    assertThrows(IllegalArgumentException.class, 
        () -> stockService.normalizeStockSymbol("TOOLONG"));
  }
  
  @Test
  void testFormatPrice() {
    assertEquals("1,234.56", stockService.formatPrice(new BigDecimal("1234.56")));
    assertEquals("0.99", stockService.formatPrice(new BigDecimal("0.99")));
    assertEquals("1,000.00", stockService.formatPrice(new BigDecimal("1000")));
    
    assertThrows(NullPointerException.class, 
        () -> stockService.formatPrice(null));
  }
  
  @Test
  void testFormatChange() {
    assertEquals("+123.45", stockService.formatChange(new BigDecimal("123.45")));
    assertEquals("-45.67", stockService.formatChange(new BigDecimal("-45.67")));
    assertEquals("+0.00", stockService.formatChange(BigDecimal.ZERO));
  }
  
  @Test
  void testFormatPercentChange() {
    assertEquals("+2.34%", stockService.formatPercentChange(new BigDecimal("2.34")));
    assertEquals("-1.56%", stockService.formatPercentChange(new BigDecimal("-1.56")));
    assertEquals("+0.00%", stockService.formatPercentChange(BigDecimal.ZERO));
  }
  
  @Test
  void testFormatTimestamp() {
    LocalDateTime timestamp = LocalDateTime.of(2023, 10, 5, 14, 30, 45);
    assertEquals("2023-10-05 14:30:45", stockService.formatTimestamp(timestamp));
    
    assertThrows(NullPointerException.class, 
        () -> stockService.formatTimestamp(null));
  }
  
  @Test
  void testIsPriceUp() {
    StockQuote upQuote = new StockQuote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("2.50"), 
        new BigDecimal("1.69"), 
        LocalDateTime.now());
    
    assertTrue(stockService.isPriceUp(upQuote));
    assertFalse(stockService.isPriceDown(upQuote));
  }
  
  @Test
  void testIsPriceDown() {
    StockQuote downQuote = new StockQuote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("-2.50"), 
        new BigDecimal("-1.64"), 
        LocalDateTime.now());
    
    assertTrue(stockService.isPriceDown(downQuote));
    assertFalse(stockService.isPriceUp(downQuote));
  }
  
  @Test
  void testGetAbsolutePercentChange() {
    StockQuote quote = new StockQuote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("-2.50"), 
        new BigDecimal("-1.64"), 
        LocalDateTime.now());
    
    assertEquals(new BigDecimal("1.64"), stockService.getAbsolutePercentChange(quote));
  }
  
  @Test
  void testCreateQuoteSummary() {
    StockQuote quote = new StockQuote("AAPL", 
        new BigDecimal("150.75"), 
        new BigDecimal("2.25"), 
        new BigDecimal("1.52"), 
        LocalDateTime.now());
    
    String summary = stockService.createQuoteSummary(quote);
    assertTrue(summary.contains("AAPL"));
    assertTrue(summary.contains("150.75"));
    assertTrue(summary.contains("+2.25"));
    assertTrue(summary.contains("+1.52%"));
  }
  
  @Test
  void testIsQuoteStale() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime oldTime = now.minusMinutes(10);
    
    StockQuote freshQuote = new StockQuote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("2.50"), 
        new BigDecimal("1.69"), 
        now);
    
    StockQuote staleQuote = new StockQuote("GOOGL", 
        new BigDecimal("2500.00"), 
        new BigDecimal("-10.00"), 
        new BigDecimal("-0.40"), 
        oldTime);
    
    assertFalse(stockService.isQuoteStale(freshQuote, 5));
    assertTrue(stockService.isQuoteStale(staleQuote, 5));
  }
}