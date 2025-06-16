package com.example.mticky.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for ConfigManager.
 */
class ConfigManagerTest {
  
  @TempDir
  Path tempDir;
  
  private ConfigManager configManager;
  
  @BeforeEach
  void setUp() {
    // Override the home directory for testing
    System.setProperty("user.home", tempDir.toString());
    configManager = new ConfigManager();
  }
  
  @Test
  void testInitializeCreatesDirectories() {
    configManager.initialize();
    
    assertTrue(Files.exists(configManager.getConfigDirectory()));
    assertTrue(Files.exists(configManager.getLogsDirectory()));
  }
  
  @Test
  void testInitialWatchlistHasDefaults() {
    configManager.initialize();
    
    Set<String> watchlist = configManager.getWatchlist();
    assertFalse(watchlist.isEmpty());
    assertTrue(watchlist.contains("AAPL"));
    assertTrue(watchlist.contains("GOOGL"));
    assertTrue(watchlist.contains("MSFT"));
  }
  
  @Test
  void testAddToWatchlist() {
    configManager.initialize();
    
    assertTrue(configManager.addToWatchlist("NVDA"));
    assertTrue(configManager.isInWatchlist("NVDA"));
    
    // Adding the same symbol again should return false
    assertFalse(configManager.addToWatchlist("NVDA"));
  }
  
  @Test
  void testRemoveFromWatchlist() {
    configManager.initialize();
    configManager.addToWatchlist("NVDA");
    
    assertTrue(configManager.removeFromWatchlist("NVDA"));
    assertFalse(configManager.isInWatchlist("NVDA"));
    
    // Removing non-existent symbol should return false
    assertFalse(configManager.removeFromWatchlist("NOTEXIST"));
  }
  
  @Test
  void testSymbolNormalization() {
    configManager.initialize();
    
    configManager.addToWatchlist("  aapl  ");
    assertTrue(configManager.isInWatchlist("AAPL"));
    assertTrue(configManager.isInWatchlist("aapl"));
    assertTrue(configManager.isInWatchlist("  AAPL  "));
  }
  
  @Test
  void testSaveAndLoad() throws IOException {
    // Initialize and add some symbols
    configManager.initialize();
    configManager.addToWatchlist("NVDA");
    configManager.addToWatchlist("AMD");
    configManager.save();
    
    // Create a new config manager and verify it loads the saved data
    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();
    
    Set<String> watchlist = newConfigManager.getWatchlist();
    assertTrue(watchlist.contains("NVDA"));
    assertTrue(watchlist.contains("AMD"));
  }
  
  @Test
  void testGetWatchlistReturnsImmutableCopy() {
    configManager.initialize();
    
    Set<String> watchlist1 = configManager.getWatchlist();
    Set<String> watchlist2 = configManager.getWatchlist();
    
    // Should be different instances
    assertNotSame(watchlist1, watchlist2);
    
    // Should have same contents
    assertEquals(watchlist1, watchlist2);
    
    // Modifying returned set shouldn't affect internal state
    watchlist1.clear();
    assertFalse(configManager.getWatchlist().isEmpty());
  }
  
  @Test
  void testGetWatchlistSize() {
    configManager.initialize();
    int initialSize = configManager.getWatchlistSize();
    
    configManager.addToWatchlist("NVDA");
    assertEquals(initialSize + 1, configManager.getWatchlistSize());
    
    configManager.removeFromWatchlist("NVDA");
    assertEquals(initialSize, configManager.getWatchlistSize());
  }
}