package com.lyubomirbozhinov.mticky.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application configuration and stock watchlist persistence.
 * Handles loading/saving settings to the user's home directory.
 */
public class ConfigManager {
  private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
  
  private static final String CONFIG_DIR = ".mticky";
  private static final String CONFIG_FILE = "config.properties";
  private static final String LOGS_DIR = "logs";
  
  private static final String WATCHLIST_KEY = "watchlist";
  private static final String WATCHLIST_SEPARATOR = ",";
  
  private final Path configDir;
  private final Path configFile;
  private final Path logsDir;
  private final Properties properties;
  private final Set<String> watchlist;
  
  /**
   * Creates a new ConfigManager instance.
   */
  public ConfigManager() {
    String homeDir = System.getProperty("user.home");
    this.configDir = Paths.get(homeDir, CONFIG_DIR);
    this.configFile = configDir.resolve(CONFIG_FILE);
    this.logsDir = configDir.resolve(LOGS_DIR);
    this.properties = new Properties();
    this.watchlist = ConcurrentHashMap.newKeySet();
  }
  
  /**
   * Initializes the configuration manager by creating directories and loading settings.
   */
  public void initialize() {
    try {
      createDirectories();
      loadConfiguration();
      logger.info("Configuration initialized. Watchlist contains {} symbols", watchlist.size());
    } catch (IOException e) {
      logger.error("Failed to initialize configuration", e);
      throw new RuntimeException("Configuration initialization failed", e);
    }
  }
  
  /**
   * Creates necessary directories if they don't exist.
   */
  private void createDirectories() throws IOException {
    if (!Files.exists(configDir)) {
      Files.createDirectories(configDir);
      logger.info("Created configuration directory: {}", configDir);
    }
    
    if (!Files.exists(logsDir)) {
      Files.createDirectories(logsDir);
      logger.info("Created logs directory: {}", logsDir);
    }
  }
  
  /**
   * Loads configuration from the properties file.
   */
  private void loadConfiguration() throws IOException {
    if (Files.exists(configFile)) {
      try (InputStream input = Files.newInputStream(configFile)) {
        properties.load(input);
        loadWatchlist();
        logger.info("Configuration loaded from: {}", configFile);
      }
    } else {
      logger.info("Configuration file not found, starting with defaults: {}", configFile);
      // Create default configuration
      setDefaultConfiguration();
      save();
    }
  }
  
  /**
   * Sets default configuration values.
   */
  private void setDefaultConfiguration() {
    // Add some popular stocks as examples
    watchlist.addAll(Set.of("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"));
    logger.info("Added default stocks to watchlist");
  }
  
  /**
   * Loads the watchlist from properties.
   */
  private void loadWatchlist() {
    String watchlistStr = properties.getProperty(WATCHLIST_KEY, "");
    if (!watchlistStr.trim().isEmpty()) {
      String[] symbols = watchlistStr.split(WATCHLIST_SEPARATOR);
      for (String symbol : symbols) {
        String trimmed = symbol.trim().toUpperCase();
        if (!trimmed.isEmpty()) {
          watchlist.add(trimmed);
        }
      }
    }
  }
  
  /**
   * Saves the current configuration to the properties file.
   */
  public void save() {
    try {
      saveWatchlist();
      
      try (OutputStream output = Files.newOutputStream(configFile)) {
        properties.store(output, "mticky configuration");
        logger.debug("Configuration saved to: {}", configFile);
      }
    } catch (IOException e) {
      logger.error("Failed to save configuration", e);
      throw new RuntimeException("Configuration save failed", e);
    }
  }
  
  /**
   * Saves the watchlist to properties.
   */
  private void saveWatchlist() {
    String watchlistStr = watchlist.stream()
        .sorted()
        .collect(Collectors.joining(WATCHLIST_SEPARATOR));
    properties.setProperty(WATCHLIST_KEY, watchlistStr);
  }
  
  /**
   * Gets a copy of the current watchlist.
   *
   * @return a new set containing all watchlist symbols
   */
  public Set<String> getWatchlist() {
    return new HashSet<>(watchlist);
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
      logger.debug("Symbol {} already in watchlist", normalized);
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
      logger.debug("Symbol {} not found in watchlist", normalized);
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
   * Gets the logs directory path.
   *
   * @return the logs directory path
   */
  public Path getLogsDirectory() {
    return logsDir;
  }
  
  /**
   * Gets the configuration directory path.
   *
   * @return the configuration directory path
   */
  public Path getConfigDirectory() {
    return configDir;
  }
}
