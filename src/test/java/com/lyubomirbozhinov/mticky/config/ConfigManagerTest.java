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

    // Constants for default theme (should match ConfigManager's internal constants)
    private static final String DEFAULT_THEME_NAME = "tokyo-night";
    private static final String THEME_KEY = "theme";
    private static final String WATCHLIST_KEY = "watchlist";

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
        props.setProperty(WATCHLIST_KEY, ""); // Set watchlist to an empty string
        props.setProperty(THEME_KEY, "test-theme"); // Add a dummy theme to avoid default
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
        props.setProperty(THEME_KEY, "test-theme"); // Add a dummy theme to avoid default
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
        configManager.setTheme("dark-mode"); // Set a custom theme
        int expectedWatchlistSize = configManager.getWatchlistSize(); // Capture current size
        String expectedTheme = configManager.getTheme();
        configManager.save();

        // Create a new config manager and verify it loads the saved data
        ConfigManager newConfigManager = new ConfigManager();
        newConfigManager.initialize(); // Initialize will load from the saved file

        Set<String> watchlist = newConfigManager.getWatchlist();
        assertTrue(watchlist.contains("NVDA"), "Loaded watchlist should contain NVDA");
        assertTrue(watchlist.contains("AMD"), "Loaded watchlist should contain AMD");
        assertEquals(expectedWatchlistSize, newConfigManager.getWatchlistSize(), "Loaded watchlist should have the correct size");
        assertEquals(expectedTheme, newConfigManager.getTheme(), "Loaded theme should be the saved theme");
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

    // New tests for Theme functionality

    @Test
    void testGetTheme_DefaultWhenConfigFileMissing() {
        // Ensure config file does NOT exist before initialize
        assertFalse(Files.exists(configManager.getConfigFile()), "Config file should not exist initially");

        configManager.initialize();
        assertEquals(DEFAULT_THEME_NAME, configManager.getTheme(), "Default theme should be returned when config file is missing");
    }

    @Test
    void testGetTheme_LoadsFromExistingConfigFile() throws IOException {
        Path configFile = configManager.getConfigFile();
        Files.createDirectories(configFile.getParent());

        Properties props = new Properties();
        props.setProperty(THEME_KEY, "solarized-dark");
        props.setProperty(WATCHLIST_KEY, "TSLA"); // Add a dummy watchlist to satisfy requirements
        try (OutputStream os = Files.newOutputStream(configFile)) {
            props.store(os, "Test config with custom theme");
        }

        // Re-instantiate ConfigManager to pick up the existing config
        configManager = new ConfigManager();
        configManager.initialize();

        assertEquals("solarized-dark", configManager.getTheme(), "Theme should be loaded from the existing config file");
    }

    @Test
    void testGetTheme_DefaultWhenThemePropertyMissing() throws IOException {
        Path configFile = configManager.getConfigFile();
        Files.createDirectories(configFile.getParent());

        Properties props = new Properties();
        props.setProperty(WATCHLIST_KEY, "TSLA"); // Ensure other properties exist but no theme
        try (OutputStream os = Files.newOutputStream(configFile)) {
            props.store(os, "Test config with no theme property");
        }

        // Re-instantiate ConfigManager to pick up the existing config
        configManager = new ConfigManager();
        configManager.initialize();

        assertEquals(DEFAULT_THEME_NAME, configManager.getTheme(), "Default theme should be returned when theme property is missing");
    }

    @Test
    void testSetTheme() {
        configManager.initialize(); // Ensure default properties are set

        String newTheme = "tokyo-night";
        configManager.setTheme(newTheme);
        assertEquals(newTheme, configManager.getTheme(), "Theme should be updated after setting");

        // Test with leading/trailing spaces
        configManager.setTheme("  monokai ");
        assertEquals("monokai", configManager.getTheme(), "Theme should be trimmed");

        // Test with null or empty string (should revert to default)
        configManager.setTheme(null);
        assertEquals(DEFAULT_THEME_NAME, configManager.getTheme(), "Setting null theme should revert to default");
        configManager.setTheme("");
        assertEquals(DEFAULT_THEME_NAME, configManager.getTheme(), "Setting empty theme should revert to default");
        configManager.setTheme("   ");
        assertEquals(DEFAULT_THEME_NAME, configManager.getTheme(), "Setting blank theme should revert to default");
    }

    @Test
    void testThemePersistence() throws IOException {
        configManager.initialize();
        configManager.setTheme("custom-theme");
        configManager.save();

        // Verify the theme is present in the saved file
        Path configFile = configManager.getConfigFile();
        assertTrue(Files.exists(configFile), "Config file should exist after saving");

        Properties loadedProps = new Properties();
        try (var is = Files.newInputStream(configFile)) {
            loadedProps.load(is);
        }
        assertEquals("custom-theme", loadedProps.getProperty(THEME_KEY), "Saved config file should contain the custom theme");

        // Create a new ConfigManager instance and load to verify
        ConfigManager newConfigManager = new ConfigManager();
        newConfigManager.initialize();
        assertEquals("custom-theme", newConfigManager.getTheme(), "New ConfigManager should load the saved custom theme");
    }

    @Test
    void testThemeIsSavedWithOtherProperties() throws IOException {
        configManager.initialize();
        configManager.addToWatchlist("TEST");
        configManager.setTheme("new-fancy-theme");
        configManager.save();

        // Load the config with a fresh manager
        ConfigManager newConfigManager = new ConfigManager();
        newConfigManager.initialize();

        assertTrue(newConfigManager.getWatchlist().contains("TEST"), "Watchlist should be correctly loaded");
        assertEquals("new-fancy-theme", newConfigManager.getTheme(), "Theme should be correctly loaded");
    }
}

