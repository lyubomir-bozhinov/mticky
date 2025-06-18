package com.lyubomirbozhinov.mticky.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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
  private Path testUserHome; // Store the test user home path

  @BeforeEach
  void setUp() {
    // Use the temporary directory as the user's home for testing
    testUserHome = tempDir;
    System.setProperty("user.home", testUserHome.toString());
    configManager = new ConfigManager();
  }

  @AfterEach
  void tearDown() {
    // Clean up the system property after each test
    System.clearProperty("user.home");
  }

  @Test
  void testInitializeCreatesDirectories() {
    configManager.initialize();

    assertTrue(Files.exists(configManager.getConfigDirectory()), "Config directory should be created");
    assertTrue(Files.exists(configManager.getLogsDirectory()), "Logs directory should be created");
  }

  @Test
  void testInitialWatchlistHasDefaultsWhenConfigFileMissing() {
    // Ensure config file does NOT exist before initialize
    assertFalse(Files.exists(configManager.getConfigFile()), "Config file should not exist initially");

    configManager.initialize();

    Set<String> watchlist = configManager.getWatchlist();
    assertFalse(watchlist.isEmpty(), "Watchlist should not be empty for a fresh installation");
    assertTrue(watchlist.contains("AAPL"), "Watchlist should contain AAPL by default");
    assertTrue(watchlist.contains("GOOGL"), "Watchlist should contain GOOGL by default");
    assertTrue(watchlist.contains("MSFT"), "Watchlist should contain MSFT by default");
    assertEquals(5, watchlist.size(), "Watchlist should have 5 default symbols"); // Assert specific default count
  }

  @Test
  void testInitialWatchlistIsEmptyWhenExistingConfigFileIsEmpty() throws IOException {
    // Scenario: Config file exists but has an empty watchlist property
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent()); // Ensure parent directory exists

    // Create a properties file with an explicitly empty watchlist
    Properties props = new Properties();
    props.setProperty("watchlist", ""); // Set watchlist to an empty string
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Test config with empty watchlist");
    }

    assertTrue(Files.exists(configFile), "Config file should exist for this test");

    // Re-instantiate ConfigManager to pick up the existing (empty) config
    configManager = new ConfigManager();
    configManager.initialize();

    Set<String> watchlist = configManager.getWatchlist();
    assertTrue(watchlist.isEmpty(), "Watchlist should be empty if config file has empty watchlist property");
    assertEquals(0, configManager.getWatchlistSize(), "Watchlist size should be 0");
  }

  @Test
  void testInitialWatchlistIsEmptyWhenExistingConfigFileHasNoWatchlistProperty() throws IOException {
    // Scenario: Config file exists but has no watchlist property at all
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent()); // Ensure parent directory exists

    // Create a properties file without the watchlist property
    Properties props = new Properties();
    props.setProperty("some.other.prop", "value");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Test config without watchlist property");
    }

    assertTrue(Files.exists(configFile), "Config file should exist for this test");

    // Re-instantiate ConfigManager to pick up the existing config
    configManager = new ConfigManager();
    configManager.initialize();

    Set<String> watchlist = configManager.getWatchlist();
    assertTrue(watchlist.isEmpty(), "Watchlist should be empty if config file has no watchlist property");
    assertEquals(0, configManager.getWatchlistSize(), "Watchlist size should be 0");
  }

  @Test
  void testAddToWatchlist() {
    configManager.initialize();

    assertTrue(configManager.addToWatchlist("NVDA"), "Should be able to add NVDA");
    assertTrue(configManager.isInWatchlist("NVDA"), "NVDA should be in watchlist");

    // Adding the same symbol again should return false
    assertFalse(configManager.addToWatchlist("NVDA"), "Adding existing symbol should return false");
    assertEquals(6, configManager.getWatchlistSize(), "Watchlist size should be original defaults + 1"); // Verify size
  }

  @Test
  void testRemoveFromWatchlist() {
    configManager.initialize();
    configManager.addToWatchlist("NVDA"); // Add a symbol to remove

    assertTrue(configManager.removeFromWatchlist("NVDA"), "Should be able to remove NVDA");
    assertFalse(configManager.isInWatchlist("NVDA"), "NVDA should no longer be in watchlist");

    // Removing non-existent symbol should return false
    assertFalse(configManager.removeFromWatchlist("NOTEXIST"), "Removing non-existent symbol should return false");
    assertEquals(5, configManager.getWatchlistSize(), "Watchlist size should be back to original defaults");
  }

  @Test
  void testSymbolNormalization() {
    configManager.initialize();

    // Change "aapl" to a symbol NOT in the default watchlist
    // Using BRK.B which is not in the default list (AAPL, GOOGL, MSFT, AMZN, TSLA)
    configManager.addToWatchlist("  brk.b  ");
    assertTrue(configManager.isInWatchlist("BRK.B"), "Normalized 'brk.b' should be found as 'BRK.B'");
    assertTrue(configManager.isInWatchlist("brk.b"), "Case-insensitive check for 'brk.b' should work");
    assertTrue(configManager.isInWatchlist("  BRK.B  "), "Trimmed and case-insensitive check for '  BRK.B  ' should work");
    // Original defaults (5) + 1 new symbol = 6
    assertEquals(6, configManager.getWatchlistSize(), "Watchlist size should be original defaults + 1 after adding normalized symbol");
  }

  @Test
  void testSaveAndLoad() throws IOException {
    // Initialize and add some symbols
    configManager.initialize();
    configManager.addToWatchlist("NVDA");
    configManager.addToWatchlist("AMD");
    int expectedSize = configManager.getWatchlistSize(); // Capture current size
    configManager.save();

    // Create a new config manager and verify it loads the saved data
    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();

    Set<String> watchlist = newConfigManager.getWatchlist();
    assertTrue(watchlist.contains("NVDA"), "Loaded watchlist should contain NVDA");
    assertTrue(watchlist.contains("AMD"), "Loaded watchlist should contain AMD");
    assertEquals(expectedSize, newConfigManager.getWatchlistSize(), "Loaded watchlist should have the correct size");
  }

  @Test
  void testGetWatchlistReturnsImmutableCopy() {
    configManager.initialize();

    Set<String> watchlist1 = configManager.getWatchlist();
    Set<String> watchlist2 = configManager.getWatchlist();

    // Should be different instances
    assertNotSame(watchlist1, watchlist2, "Returned sets should be different instances");

    // Should have same contents
    assertEquals(watchlist1, watchlist2, "Returned sets should have the same contents");

    // Modifying returned set shouldn't affect internal state
    // Attempting to modify a Collections.unmodifiableSet will throw UnsupportedOperationException
    assertThrows(UnsupportedOperationException.class, () -> watchlist1.clear(),
      "Modifying the returned set should throw UnsupportedOperationException");
    assertFalse(configManager.getWatchlist().isEmpty(), "Internal watchlist should not be affected by external modifications");
  }

  @Test
  void testGetWatchlistSize() {
    configManager.initialize();
    int initialSize = configManager.getWatchlistSize(); // This will be 5 (default stocks)

    configManager.addToWatchlist("NVDA");
    assertEquals(initialSize + 1, configManager.getWatchlistSize(), "Watchlist size should increase by 1 after adding");

    configManager.removeFromWatchlist("NVDA");
    assertEquals(initialSize, configManager.getWatchlistSize(), "Watchlist size should return to initial after removing added symbol");

    // Test with removing a default stock
    assertTrue(configManager.removeFromWatchlist("AAPL"), "Should be able to remove a default stock");
    assertEquals(initialSize - 1, configManager.getWatchlistSize(), "Watchlist size should decrease by 1 after removing default");
  }
}

