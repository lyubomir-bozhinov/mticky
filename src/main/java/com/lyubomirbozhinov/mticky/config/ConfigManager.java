package com.lyubomirbozhinov.mticky.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional; // Added for Optional usage
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
  private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

  private static final String APP_DIR_NAME = ".mticky";
  private static final String CONFIG_FILE_NAME = "config.properties";
  private static final String LOGS_SUBDIR_NAME = "logs";
  private static final String THEMES_SUBDIR_NAME = "themes";
  private static final String FINNHUB_API_KEY = "finnhub_api_key";
  private static final String WATCHLIST_KEY = "watchlist";
  private static final String WATCHLIST_SEPARATOR = ",";
  private static final String THEME_KEY = "theme";
  private static final String DEFAULT_THEME = "tokyo-night";
  private static final String REFRESH_INTERVAL_KEY = "refresh_interval_seconds";
  private static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 15;

  private static final List<String> BUNDLED_THEME_FILENAMES = Arrays.asList(
    "catppuccin.theme",
    "everforest.theme",
    "tokyo-night.theme",
    "rose-pine.theme"
  );

  private final Path appConfigDir;
  private final Path appConfigFile;
  private final Path appLogsDir;
  private final Path themesDirectory;

  private final Properties applicationProperties;
  private final Set<String> watchlist;

  private String finnhubApiKey;

  private int refreshIntervalSeconds;

  public ConfigManager() {
    String homeDir = System.getProperty("user.home");
    this.appConfigDir = Paths.get(homeDir, APP_DIR_NAME);
    this.appConfigFile = appConfigDir.resolve(CONFIG_FILE_NAME);
    this.appLogsDir = appConfigDir.resolve(LOGS_SUBDIR_NAME);
    this.themesDirectory = appConfigDir.resolve(THEMES_SUBDIR_NAME);
    this.applicationProperties = new Properties();
    this.watchlist = ConcurrentHashMap.newKeySet();
    this.refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;
  }

  public void initialize() {
    try {
      ensureApplicationDirectoriesExist();

      if (Files.exists(appConfigFile)) {
        loadApplicationConfiguration();
        logger.info("Configuration loaded. Watchlist contains {} symbols, Theme: {}", watchlist.size(), getTheme());
        return;
      }

      initializeNewConfigAndSave(); // Extract new config initialization
    } catch (IOException e) {
      logger.error("Failed to initialize configuration", e);
      throw new RuntimeException("Configuration initialization failed", e);
    }
  }

  private void initializeNewConfigAndSave() throws IOException {
    logger.info("Configuration file not found, creating with defaults: {}", appConfigFile);
    addDefaultWatchlistItems();
    setDefaultTheme();
    copyBundledThemes();
    setDefaultRefreshInterval();
    save();
    logger.info("Configuration initialized. Watchlist contains {} symbols, Theme: {}", watchlist.size(), getTheme());
  }

  public void save() {
    try {
      saveWatchlistToProperties();
      saveThemeToProperties();
      saveRefreshIntervalToProperties();

      if (this.finnhubApiKey != null && !this.finnhubApiKey.trim().isEmpty()) {
        applicationProperties.setProperty(FINNHUB_API_KEY, this.finnhubApiKey.trim());
      } else {
        applicationProperties.remove(FINNHUB_API_KEY); // Remove key if it's null or empty
      }

      try (OutputStream output = Files.newOutputStream(appConfigFile)) {
        applicationProperties.store(output, "mticky configuration");
        logger.debug("Configuration saved to: {}", appConfigFile);
      }
    } catch (IOException e) {
      logger.error("Failed to save configuration", e);
      throw new RuntimeException("Configuration save failed", e);
    }
  }

  private void ensureApplicationDirectoriesExist() throws IOException {
    Files.createDirectories(appConfigDir);
    Files.createDirectories(appLogsDir);
    Files.createDirectories(themesDirectory);
    logger.info("Ensured application directories exist: {}", appConfigDir); // Consolidated logging
  }

  private void loadApplicationConfiguration() throws IOException {
    try (InputStream input = Files.newInputStream(appConfigFile)) {
      applicationProperties.load(input);
      loadWatchlistFromProperties();
      loadThemeFromProperties();
      loadRefreshIntervalFromProperties();

      this.finnhubApiKey = applicationProperties.getProperty(FINNHUB_API_KEY);

      logger.info("Configuration loaded from: {}", appConfigFile);
    }
  }

  private void loadWatchlistFromProperties() {
    String watchlistStr = applicationProperties.getProperty(WATCHLIST_KEY, "").trim();
    if (watchlistStr.isEmpty()) {
      logger.info("No watchlist data found in properties.");
      return;
    }

    Arrays.stream(watchlistStr.split(WATCHLIST_SEPARATOR))
      .map(String::trim)
      .map(String::toUpperCase)
      .filter(s -> !s.isEmpty())
      .forEach(watchlist::add);
    logger.info("Loaded {} symbols into watchlist from properties.", watchlist.size());
  }

  private void addDefaultWatchlistItems() {
    watchlist.addAll(Set.of("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"));
    logger.info("Added default stocks to watchlist");
  }

  private void setDefaultTheme() {
    applicationProperties.setProperty(THEME_KEY, DEFAULT_THEME);
    logger.info("Set default theme to: {}", DEFAULT_THEME);
  }

  private void setDefaultRefreshInterval() {
    applicationProperties.setProperty(REFRESH_INTERVAL_KEY, String.valueOf(DEFAULT_REFRESH_INTERVAL_SECONDS));
    this.refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;
    logger.info("Set default refresh interval to: {}s", DEFAULT_REFRESH_INTERVAL_SECONDS);
  }

  private void saveWatchlistToProperties() {
    String watchlistStr = watchlist.stream()
    .sorted()
    .collect(Collectors.joining(WATCHLIST_SEPARATOR));
    applicationProperties.setProperty(WATCHLIST_KEY, watchlistStr);
  }

  private void loadRefreshIntervalFromProperties() {
    String intervalStr = applicationProperties.getProperty(REFRESH_INTERVAL_KEY);
    if (intervalStr != null) {
      try {
        int loadedInterval = Integer.parseInt(intervalStr.trim());
        if (loadedInterval >= 1) {
          this.refreshIntervalSeconds = loadedInterval;
          logger.info("Loaded refresh interval: {}s", loadedInterval);
          return;
        } else {
          logger.warn("Invalid refresh interval value loaded from config: {}. Using default: {}s", intervalStr, DEFAULT_REFRESH_INTERVAL_SECONDS);
        }
      } catch (NumberFormatException e) {
        logger.warn("Failed to parse refresh interval from config: {}. Using default: {}s", intervalStr, DEFAULT_REFRESH_INTERVAL_SECONDS);
      }
    } else {
      logger.info("No refresh interval found in properties. Using default: {}s", DEFAULT_REFRESH_INTERVAL_SECONDS);
    }
    this.refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;
  }

  private void saveRefreshIntervalToProperties() {
    applicationProperties.setProperty(REFRESH_INTERVAL_KEY, String.valueOf(this.refreshIntervalSeconds));
    logger.debug("Refresh interval property prepared for saving: {}s", this.refreshIntervalSeconds);
  }

  private void loadThemeFromProperties() {
    String themeName = applicationProperties.getProperty(THEME_KEY);
    Optional.ofNullable(themeName)
      .filter(s -> !s.trim().isEmpty())
      .ifPresentOrElse(
        t -> logger.info("Loaded theme: {}", t),
      () -> {
          setDefaultTheme();
          logger.info("No theme found in properties, setting to default: {}", DEFAULT_THEME);
        }
      );
  }

  private void saveThemeToProperties() {
    logger.debug("Theme property prepared for saving: {}", applicationProperties.getProperty(THEME_KEY));
  }

  private void copyBundledThemes() {
    if (BUNDLED_THEME_FILENAMES.isEmpty()) {
      logger.warn("No .theme files defined in BUNDLED_THEME_FILENAMES. No themes copied.");
      return;
    }
    BUNDLED_THEME_FILENAMES.forEach(this::copySingleBundledTheme);
  }

  private void copySingleBundledTheme(String themeFileName) {
    Path targetPath = themesDirectory.resolve(themeFileName);
    if (Files.exists(targetPath)) {
      logger.debug("Theme file already exists, skipping copy: {}", themeFileName);
      return;
    }

    try (InputStream is = getClass().getResourceAsStream("/themes/" + themeFileName)) {
      Objects.requireNonNull(is, "Bundled theme resource not found: /themes/" + themeFileName + ". Please check your src/main/resources/themes folder.");
      Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
      logger.info("Copied bundled theme: {}", themeFileName);
    } catch (IOException e) {
      logger.error("Error copying bundled theme {}: {}", themeFileName, e.getMessage());
    } catch (NullPointerException e) {
      logger.warn(e.getMessage());
    }
  }

  public Set<String> getWatchlist() {
    return Collections.unmodifiableSet(new HashSet<>(watchlist));
  }

  public boolean addToWatchlist(String symbol) {
    String normalized = symbol.trim().toUpperCase();
    boolean added = watchlist.add(normalized);
    Optional.of(added)
      .filter(Boolean::valueOf)
      .ifPresent(b -> logger.info("Added {} to watchlist", normalized));
    Optional.of(added)
      .filter(b -> !b) // if not added
      .ifPresent(b -> logger.info("Symbol {} already in watchlist", normalized));
    return added;
  }

  public boolean removeFromWatchlist(String symbol) {
    String normalized = symbol.trim().toUpperCase();
    boolean removed = watchlist.remove(normalized);
    Optional.of(removed)
      .filter(Boolean::valueOf)
      .ifPresent(b -> logger.info("Removed {} from watchlist", normalized));
    Optional.of(removed)
      .filter(b -> !b) // if not removed
      .ifPresent(b -> logger.info("Symbol {} not found in watchlist", normalized));
    return removed;
  }

  public boolean isInWatchlist(String symbol) {
    return watchlist.contains(symbol.trim().toUpperCase());
  }

  public int getWatchlistSize() {
    return watchlist.size();
  }

  public String getProperty(String key, String defaultValue) {
    return applicationProperties.getProperty(key, defaultValue);
  }

  public void setProperty(String key, String value) {
    applicationProperties.setProperty(key, value);
  }

  public String getTheme() {
    return applicationProperties.getProperty(THEME_KEY, DEFAULT_THEME);
  }

  public void setTheme(String themeName) {
    Optional.ofNullable(themeName)
      .filter(s -> !s.trim().isEmpty())
      .ifPresentOrElse(
        t -> {
          applicationProperties.setProperty(THEME_KEY, t.trim());
          logger.info("Theme set to: {}", t.trim());
        },
      () -> {
          setDefaultTheme();
          logger.warn("Attempted to set an empty or null theme name, reverting to default.");
        }
      );
  }

  public Path getLogsDirectory() {
    return appLogsDir;
  }

  public Path getConfigDirectory() {
    return appConfigDir;
  }

  public Path getConfigFile() {
    return appConfigFile;
  }

  public Path getThemesDirectory() {
    return themesDirectory;
  }

  public String getFinnhubApiKey() {
    return this.finnhubApiKey;
  }

  public void setFinnhubApiKey(String finnhubApiKey) {
    this.finnhubApiKey = (finnhubApiKey != null && !finnhubApiKey.trim().isEmpty()) ? finnhubApiKey.trim() : null;
    logger.debug("Finnhub API Key set (value masked for logging)");
  }

  public int getRefreshIntervalSeconds() {
    return refreshIntervalSeconds;
  }

  public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
    if (refreshIntervalSeconds >= 1) {
      this.refreshIntervalSeconds = refreshIntervalSeconds;
      logger.info("Refresh interval set to: {}s", refreshIntervalSeconds);
    } else {
      logger.warn("Attempted to set invalid refresh interval: {}s. Keeping current value.", refreshIntervalSeconds);
    }
  }
}


