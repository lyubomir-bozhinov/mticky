package com.lyubomirbozhinov.mticky.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application configuration and stock watchlist persistence,
 * handling loading/saving settings to the user's home directory.
 */
public class ConfigManager {
  private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

  private static final String APP_DIR_NAME = ".mticky";
  private static final String CONFIG_FILE_NAME = "config.properties";
  private static final String LOGS_SUBDIR_NAME = "logs";

  private static final String WATCHLIST_KEY = "watchlist";
  private static final String WATCHLIST_SEPARATOR = ",";

  private final Path appConfigDir;
  private final Path appConfigFile;
  private final Path appLogsDir;

  private final Properties applicationProperties;
  private final Set<String> watchlist;

  /**
     * Creates a new ConfigManager instance.
     */
  public ConfigManager() {
    String homeDir = System.getProperty("user.home");
    this.appConfigDir = Paths.get(homeDir, APP_DIR_NAME);
    this.appConfigFile = appConfigDir.resolve(CONFIG_FILE_NAME);
    this.appLogsDir = appConfigDir.resolve(LOGS_SUBDIR_NAME);
    this.applicationProperties = new Properties();
    this.watchlist = ConcurrentHashMap.newKeySet();
  }

  /**
     * Initializes the configuration manager by creating necessary directories,
     * loading settings, and setting up defaults if needed.
     */
  public void initialize() {
    try {
      ensureApplicationDirectoriesExist();

      // Load configuration, if the file doesn't exist, populate with defaults.
      // An empty watchlist implies explicit user action, not a missing config.
      if (!Files.exists(appConfigFile)) {
        logger.info("Configuration file not found, creating with defaults: {}", appConfigFile);
        addDefaultWatchlistItems();
        save(); // Persist defaults immediately
        logger.info("Configuration initialized. Watchlist contains {} symbols", watchlist.size());
        return; // Early return for initial setup
      }

      // If file exists, load it
      loadApplicationConfiguration();
      logger.info("Configuration initialized. Watchlist contains {} symbols", watchlist.size());

    } catch (IOException e) {
      logger.error("Failed to initialize configuration", e);
      throw new RuntimeException("Configuration initialization failed", e);
    }
  }

  /**
     * Saves the current configuration (including watchlist) to the properties file.
     */
  public void save() {
    try {
      saveWatchlistToProperties();

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
    if (!Files.exists(appConfigDir)) {
      Files.createDirectories(appConfigDir);
      logger.info("Created application configuration directory: {}", appConfigDir);
    }

    if (!Files.exists(appLogsDir)) {
      Files.createDirectories(appLogsDir);
      logger.info("Created application logs directory: {}", appLogsDir);
    }
  }

  private void loadApplicationConfiguration() throws IOException {
    // This method is only called if appConfigFile is known to exist.
    try (InputStream input = Files.newInputStream(appConfigFile)) {
      applicationProperties.load(input);
      loadWatchlistFromProperties();
      logger.info("Configuration loaded from: {}", appConfigFile);
    }
  }

  private void loadWatchlistFromProperties() {
    String watchlistStr = applicationProperties.getProperty(WATCHLIST_KEY, "");
    if (watchlistStr.trim().isEmpty()) { // Early return for empty string
      logger.info("No watchlist data found in properties.");
      return;
    }

    String[] symbols = watchlistStr.split(WATCHLIST_SEPARATOR);
    for (String symbol : symbols) {
      String trimmed = symbol.trim().toUpperCase();
      if (!trimmed.isEmpty()) { // Only add non-empty symbols
        watchlist.add(trimmed);
      }
    }
    logger.info("Loaded {} symbols into watchlist from properties.", watchlist.size());
  }

  private void addDefaultWatchlistItems() {
    watchlist.addAll(Set.of("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"));
    logger.info("Added default stocks to watchlist");
  }

  private void saveWatchlistToProperties() {
    String watchlistStr = watchlist.stream()
    .sorted()
    .collect(Collectors.joining(WATCHLIST_SEPARATOR));
    applicationProperties.setProperty(WATCHLIST_KEY, watchlistStr);
  }

  /**
     * Gets an unmodifiable copy of the current watchlist.
     *
     * @return a new set containing all watchlist symbols
     */
  public Set<String> getWatchlist() {
    return Collections.unmodifiableSet(new HashSet<>(watchlist));
  }

  /**
     * Adds a symbol to the watchlist.
     *
     * @param symbol the symbol to add
     * @return true if the symbol was added, false if it already existed
     */
  public boolean addToWatchlist(String symbol) {
    String normalized = symbol.trim().toUpperCase();
    boolean added = watchlist.add(normalized);

    if (added) {
      logger.info("Added {} to watchlist", normalized);
    } else {
      logger.info("Symbol {} already in watchlist", normalized);
    }
    return added;
  }

  /**
     * Removes a symbol from the watchlist.
     *
     * @param symbol the symbol to remove
     * @return true if the symbol was removed, false if it didn't exist
     */
  public boolean removeFromWatchlist(String symbol) {
    String normalized = symbol.trim().toUpperCase();
    boolean removed = watchlist.remove(normalized);

    if (removed) {
      logger.info("Removed {} from watchlist", normalized);
    } else {
      logger.info("Symbol {} not found in watchlist", normalized);
    }
    return removed;
  }

  /**
     * Checks if a symbol is in the watchlist.
     *
     * @param symbol the symbol to check
     * @return true if the symbol is in the watchlist
     */
  public boolean isInWatchlist(String symbol) {
    return watchlist.contains(symbol.trim().toUpperCase());
  }

  /**
     * Gets the size of the watchlist.
     *
     * @return the number of symbols in the watchlist
     */
  public int getWatchlistSize() {
    return watchlist.size();
  }

  /**
     * Gets a property value from the configuration.
     *
     * @param key The property key.
     * @param defaultValue The default value if the key is not found.
     * @return The property value or the default value.
     */
  public String getProperty(String key, String defaultValue) {
    return applicationProperties.getProperty(key, defaultValue);
  }

  /**
     * Sets a property value in the configuration.
     *
     * @param key The property key.
     * @param value The property value.
     */
  public void setProperty(String key, String value) {
    applicationProperties.setProperty(key, value);
  }

  /**
     * Gets the logs directory path.
     *
     * @return the logs directory path
     */
  public Path getLogsDirectory() {
    return appLogsDir;
  }

  /**
     * Gets the configuration directory path.
     *
     * @return the configuration directory path
     */
  public Path getConfigDirectory() {
    return appConfigDir;
  }

  /**
     * Gets the configuration file path.
     *
     * @return the configuration file path
     */
  public Path getConfigFile() {
    return appConfigFile;
  }
}

