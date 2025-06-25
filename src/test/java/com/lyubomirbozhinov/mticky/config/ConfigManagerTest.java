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

class ConfigManagerTest {

  @TempDir
  Path tempDir;

  private ConfigManager configManager;
  private Path testUserHome;

  private static final String DEFAULT_THEME_NAME = "tokyo-night";
  private static final String THEME_KEY = "theme";
  private static final String WATCHLIST_KEY = "watchlist";
  private static final String FINNHUB_API_KEY = "finnhub_api_key";

  @BeforeEach
  void setUp() {
    testUserHome = tempDir;
    System.setProperty("user.home", testUserHome.toString());
    configManager = new ConfigManager();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("user.home");
  }

  @Test
  void testInitializeCreatesDirectories() {
    configManager.initialize();
    assertTrue(Files.exists(configManager.getConfigDirectory()));
    assertTrue(Files.exists(configManager.getLogsDirectory()));
    assertTrue(Files.exists(configManager.getThemesDirectory()));
  }

  @Test
  void testInitialWatchlistHasDefaultsWhenConfigFileMissing() {
    assertFalse(Files.exists(configManager.getConfigFile()));
    configManager.initialize();
    Set<String> watchlist = configManager.getWatchlist();
    assertFalse(watchlist.isEmpty());
    assertTrue(watchlist.contains("AAPL"));
    assertTrue(watchlist.contains("GOOGL"));
    assertTrue(watchlist.contains("MSFT"));
    assertEquals(5, watchlist.size());
  }

  @Test
  void testInitialWatchlistIsEmptyWhenExistingConfigFileIsEmpty() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty(WATCHLIST_KEY, "");
    props.setProperty(THEME_KEY, "test-theme");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Test config with empty watchlist");
    }
    configManager = new ConfigManager();
    configManager.initialize();
    Set<String> watchlist = configManager.getWatchlist();
    assertTrue(watchlist.isEmpty());
    assertEquals(0, configManager.getWatchlistSize());
  }

  @Test
  void testInitialWatchlistIsEmptyWhenExistingConfigFileHasNoWatchlistProperty() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty("some.other.prop", "value");
    props.setProperty(THEME_KEY, "test-theme");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Test config without watchlist property");
    }
    configManager = new ConfigManager();
    configManager.initialize();
    Set<String> watchlist = configManager.getWatchlist();
    assertTrue(watchlist.isEmpty());
    assertEquals(0, configManager.getWatchlistSize());
  }

  @Test
  void testAddToWatchlist() {
    configManager.initialize();
    assertTrue(configManager.addToWatchlist("NVDA"));
    assertTrue(configManager.isInWatchlist("NVDA"));
    assertFalse(configManager.addToWatchlist("NVDA"));
    assertEquals(6, configManager.getWatchlistSize());
  }

  @Test
  void testRemoveFromWatchlist() {
    configManager.initialize();
    configManager.addToWatchlist("NVDA");
    assertTrue(configManager.removeFromWatchlist("NVDA"));
    assertFalse(configManager.isInWatchlist("NVDA"));
    assertFalse(configManager.removeFromWatchlist("NOTEXIST"));
    assertEquals(5, configManager.getWatchlistSize());
  }

  @Test
  void testSymbolNormalization() {
    configManager.initialize();
    configManager.addToWatchlist("  brk.b  ");
    assertTrue(configManager.isInWatchlist("BRK.B"));
    assertTrue(configManager.isInWatchlist("brk.b"));
    assertTrue(configManager.isInWatchlist("  BRK.B  "));
    assertEquals(6, configManager.getWatchlistSize());
  }

  @Test
  void testSaveAndLoad() throws IOException {
    configManager.initialize();
    configManager.addToWatchlist("NVDA");
    configManager.addToWatchlist("AMD");
    configManager.setTheme("dark-mode");
    configManager.setFinnhubApiKey("mytestapikey123");
    int expectedWatchlistSize = configManager.getWatchlistSize();
    String expectedTheme = configManager.getTheme();
    String expectedApiKey = configManager.getFinnhubApiKey();
    configManager.save();

    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();

    Set<String> watchlist = newConfigManager.getWatchlist();
    assertTrue(watchlist.contains("NVDA"));
    assertTrue(watchlist.contains("AMD"));
    assertEquals(expectedWatchlistSize, newConfigManager.getWatchlistSize());
    assertEquals(expectedTheme, newConfigManager.getTheme());
    assertEquals(expectedApiKey, newConfigManager.getFinnhubApiKey());
  }

  @Test
  void testGetWatchlistReturnsImmutableCopy() {
    configManager.initialize();
    Set<String> watchlist1 = configManager.getWatchlist();
    Set<String> watchlist2 = configManager.getWatchlist();
    assertNotSame(watchlist1, watchlist2);
    assertEquals(watchlist1, watchlist2);
    assertThrows(UnsupportedOperationException.class, () -> watchlist1.clear());
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

    assertTrue(configManager.removeFromWatchlist("AAPL"));
    assertEquals(initialSize - 1, configManager.getWatchlistSize());
  }

  @Test
  void testGetTheme_DefaultWhenConfigFileMissing() {
    assertFalse(Files.exists(configManager.getConfigFile()));
    configManager.initialize();
    assertEquals(DEFAULT_THEME_NAME, configManager.getTheme());
  }

  @Test
  void testGetTheme_LoadsFromExistingConfigFile() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty(THEME_KEY, "solarized-dark");
    props.setProperty(WATCHLIST_KEY, "TSLA");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Test config with custom theme");
    }
    configManager = new ConfigManager();
    configManager.initialize();
    assertEquals("solarized-dark", configManager.getTheme());
  }

  @Test
  void testGetTheme_DefaultWhenThemePropertyMissing() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty(WATCHLIST_KEY, "TSLA");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Test config with no theme property");
    }
    configManager = new ConfigManager();
    configManager.initialize();
    assertEquals(DEFAULT_THEME_NAME, configManager.getTheme());
  }

  @Test
  void testSetTheme() {
    configManager.initialize();
    String newTheme = "tokyo-night";
    configManager.setTheme(newTheme);
    assertEquals(newTheme, configManager.getTheme());

    configManager.setTheme("  monokai ");
    assertEquals("monokai", configManager.getTheme());

    configManager.setTheme(null);
    assertEquals(DEFAULT_THEME_NAME, configManager.getTheme());
    configManager.setTheme("");
    assertEquals(DEFAULT_THEME_NAME, configManager.getTheme());
    configManager.setTheme("   ");
    assertEquals(DEFAULT_THEME_NAME, configManager.getTheme());
  }

  @Test
  void testThemePersistence() throws IOException {
    configManager.initialize();
    configManager.setTheme("custom-theme");
    configManager.save();

    Path configFile = configManager.getConfigFile();
    Properties loadedProps = new Properties();
    try (var is = Files.newInputStream(configFile)) {
      loadedProps.load(is);
    }
    assertEquals("custom-theme", loadedProps.getProperty(THEME_KEY));

    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();
    assertEquals("custom-theme", newConfigManager.getTheme());
  }

  @Test
  void testThemeIsSavedWithOtherProperties() throws IOException {
    configManager.initialize();
    configManager.addToWatchlist("TEST");
    configManager.setTheme("new-fancy-theme");
    configManager.save();

    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();

    assertTrue(newConfigManager.getWatchlist().contains("TEST"));
    assertEquals("new-fancy-theme", newConfigManager.getTheme());
  }

  @Test
  void testGetFinnhubApiKey_DefaultNull() {
    configManager.initialize();
    assertNull(configManager.getFinnhubApiKey());
  }

  @Test
  void testSetFinnhubApiKey() {
    configManager.initialize();
    String apiKey = "validApiKey123";
    configManager.setFinnhubApiKey(apiKey);
    assertEquals(apiKey, configManager.getFinnhubApiKey());

    configManager.setFinnhubApiKey("  anotherKey  ");
    assertEquals("anotherKey", configManager.getFinnhubApiKey());
  }

  @Test
  void testSetFinnhubApiKey_NullEmptyAndBlank() {
    configManager.initialize();
    configManager.setFinnhubApiKey("initialKey");

    configManager.setFinnhubApiKey(null);
    assertNull(configManager.getFinnhubApiKey());

    configManager.setFinnhubApiKey("initialKey");
    configManager.setFinnhubApiKey("");
    assertNull(configManager.getFinnhubApiKey());

    configManager.setFinnhubApiKey("initialKey");
    configManager.setFinnhubApiKey("   ");
    assertNull(configManager.getFinnhubApiKey());
  }

  @Test
  void testFinnhubApiKeyPersistence() throws IOException {
    configManager.initialize();
    String apiKey = "persistedApiKey456";
    configManager.setFinnhubApiKey(apiKey);
    configManager.save();

    Path configFile = configManager.getConfigFile();
    Properties loadedProps = new Properties();
    try (var is = Files.newInputStream(configFile)) {
      loadedProps.load(is);
    }
    assertEquals(apiKey, loadedProps.getProperty(FINNHUB_API_KEY));

    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();
    assertEquals(apiKey, newConfigManager.getFinnhubApiKey());
  }

  @Test
  void testFinnhubApiKeyRemovedWhenNullOrEmptyOnSave() throws IOException {
    configManager.initialize();
    configManager.setFinnhubApiKey("someKeyToSetAndRemove");
    configManager.save();

    configManager.setFinnhubApiKey(null);
    configManager.save();
    Path configFile = configManager.getConfigFile();
    Properties loadedProps = new Properties();
    try (var is = Files.newInputStream(configFile)) {
      loadedProps.load(is);
    }
    assertFalse(loadedProps.containsKey(FINNHUB_API_KEY));

    configManager.setFinnhubApiKey("someKeyToSetAndRemove");
    configManager.save();
    configManager.setFinnhubApiKey("");
    configManager.save();
    try (var is = Files.newInputStream(configFile)) {
      loadedProps.clear();
      loadedProps.load(is);
    }
    assertFalse(loadedProps.containsKey(FINNHUB_API_KEY));

    configManager.setFinnhubApiKey("someKeyToSetAndRemove");
    configManager.save();
    configManager.setFinnhubApiKey("   ");
    configManager.save();
    try (var is = Files.newInputStream(configFile)) {
      loadedProps.clear();
      loadedProps.load(is);
    }
    assertFalse(loadedProps.containsKey(FINNHUB_API_KEY));
  }

  @Test
  void testBundledThemesAreCopiedOnInitialize() throws IOException {
    assertFalse(Files.exists(configManager.getConfigFile()));
    configManager.initialize();
    Path themesDir = configManager.getThemesDirectory();
    assertTrue(Files.exists(themesDir));

    assertTrue(Files.exists(themesDir.resolve("catppuccin.theme")));
    assertTrue(Files.exists(themesDir.resolve("everforest.theme")));
    assertTrue(Files.exists(themesDir.resolve("tokyo-night.theme")));
    assertTrue(Files.exists(themesDir.resolve("rose-pine.theme")));
  }

  @Test
  void testBundledThemesAreNotOverwrittenIfAlreadyExist() throws IOException {
    configManager.initialize();
    Path themesDir = configManager.getThemesDirectory();
    Path existingThemeFile = themesDir.resolve("tokyo-night.theme");
    String modifiedContent = "MODIFIED_CONTENT_TO_CHECK_OVERWRITE";
    Files.writeString(existingThemeFile, modifiedContent);

    Files.deleteIfExists(configManager.getConfigFile());

    ConfigManager newConfigManager = new ConfigManager();
    newConfigManager.initialize();

    String currentContent = Files.readString(existingThemeFile);
    assertEquals(modifiedContent, currentContent);
  }

  @Test
  void testDefaultRefreshIntervalWhenConfigMissing() {
    // Ensure no config exists before initialization
    assertFalse(Files.exists(configManager.getConfigFile()));
    configManager.initialize();
    assertEquals(15, configManager.getRefreshIntervalSeconds());
  }

  @Test
  void testRefreshIntervalLoadsFromConfigFile() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty("refresh_interval_seconds", "30");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Config with refresh interval");
    }

    configManager = new ConfigManager();
    configManager.initialize();
    assertEquals(30, configManager.getRefreshIntervalSeconds());
  }

  @Test
  void testInvalidRefreshIntervalDefaultsTo15() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty("refresh_interval_seconds", "-10");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Config with invalid refresh interval");
    }

    configManager = new ConfigManager();
    configManager.initialize();
    assertEquals(15, configManager.getRefreshIntervalSeconds());
  }

  @Test
  void testNonNumericRefreshIntervalDefaultsTo15() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty("refresh_interval_seconds", "abc");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Config with non-numeric refresh interval");
    }

    configManager = new ConfigManager();
    configManager.initialize();
    assertEquals(15, configManager.getRefreshIntervalSeconds());
  }

  @Test
  void testSetValidRefreshInterval() {
    configManager.initialize();
    configManager.setRefreshIntervalSeconds(60);
    assertEquals(60, configManager.getRefreshIntervalSeconds());
  }

  @Test
  void testSetInvalidRefreshIntervalBelowMinimum() {
    configManager.initialize();
    configManager.setRefreshIntervalSeconds(0); // Invalid
    assertEquals(15, configManager.getRefreshIntervalSeconds());
  }

  @Test
  void testRefreshIntervalPersistence() throws IOException {
    configManager.initialize();
    configManager.setRefreshIntervalSeconds(45);
    configManager.save();

    Properties loadedProps = new Properties();
    try (var is = Files.newInputStream(configManager.getConfigFile())) {
      loadedProps.load(is);
    }
    assertEquals("45", loadedProps.getProperty("refresh_interval_seconds"));

    ConfigManager reloaded = new ConfigManager();
    reloaded.initialize();
    assertEquals(45, reloaded.getRefreshIntervalSeconds());
  }

  @Test
  void testRefreshIntervalDefaultsWhenMissingInFile() throws IOException {
    Path configFile = configManager.getConfigFile();
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    props.setProperty("theme", "some-theme");
    try (OutputStream os = Files.newOutputStream(configFile)) {
      props.store(os, "Config without refresh interval");
    }

    configManager = new ConfigManager();
    configManager.initialize();
    assertEquals(15, configManager.getRefreshIntervalSeconds());
  }

}

