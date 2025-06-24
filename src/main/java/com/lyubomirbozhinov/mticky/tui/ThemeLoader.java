package com.lyubomirbozhinov.mticky.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.PropertyTheme;
import com.googlecode.lanterna.graphics.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.ArrayList;

public class ThemeLoader {

  private static final Logger logger = LoggerFactory.getLogger(ThemeLoader.class);
  private static final String THEME_FILE_EXTENSION = ".theme";

  private final Properties rawThemeProperties = new Properties();
  private final Properties guiThemeProperties = new Properties();

  private final TextColor fallbackForegroundColor;
  private final TextColor fallbackBackgroundColor;

  // Dedicated fields for non-guiThemeProperties colors
  private TextColor positiveChangeFgColor;
  private TextColor negativeChangeFgColor;
  private TextColor tableHeaderBgColor;
  private TextColor tableHeaderFgColor;
  private TextColor tableSelectionBgColor;
  private TextColor highlightFgColor;
  private TextColor inactiveFgColor;
  private TextColor titleColor;
  private TextColor divLineColor;
  private TextColor selectedFgColor;
  private TextColor selectedBgColor;

  public ThemeLoader() {
    this.fallbackForegroundColor = TextColor.ANSI.WHITE;
    this.fallbackBackgroundColor = TextColor.ANSI.BLACK;

    resetDedicatedFieldsToDefaults();
  }

  private void resetDedicatedFieldsToDefaults() {
    this.positiveChangeFgColor = TextColor.ANSI.GREEN;
    this.negativeChangeFgColor = TextColor.ANSI.RED;
    this.tableHeaderBgColor = TextColor.ANSI.BLUE;
    this.tableHeaderFgColor = TextColor.ANSI.WHITE;
    this.tableSelectionBgColor = TextColor.ANSI.CYAN;
    this.highlightFgColor = fallbackForegroundColor;
    this.inactiveFgColor = fallbackForegroundColor;
    this.titleColor = fallbackForegroundColor;
    this.divLineColor = fallbackForegroundColor;
    this.selectedFgColor = fallbackForegroundColor;
    this.selectedBgColor = fallbackBackgroundColor;
  }

  public void loadTheme(String themeName, Path themeDirectory) {
    rawThemeProperties.clear();
    guiThemeProperties.clear();
    resetDedicatedFieldsToDefaults();

    if (themeName == null || themeName.trim().isEmpty()) {
      logger.warn("Attempted to load null or empty theme name. Using fallback colors/default theme.");
      return;
    }

    String fileName = themeName.trim() + THEME_FILE_EXTENSION;
    Path themePath = themeDirectory.resolve(fileName);

    if (!Files.exists(themePath)) {
      logger.warn("Theme file not found: {}. Using fallback colors/default theme.", themePath);
      return;
    }

    try (InputStream is = Files.newInputStream(themePath)) {
      rawThemeProperties.load(is);
      logger.info("Loaded custom theme properties from: {}", themePath);

      // Dedicated field parsing
      this.positiveChangeFgColor = parseColor(rawThemeProperties.getProperty("theme[positive_change_fg]"), TextColor.ANSI.GREEN);
      this.negativeChangeFgColor = parseColor(rawThemeProperties.getProperty("theme[negative_change_fg]"), TextColor.ANSI.RED);
      this.tableHeaderBgColor = parseColor(rawThemeProperties.getProperty("theme[div_line]"), TextColor.ANSI.BLUE);
      this.tableHeaderFgColor = parseColor(rawThemeProperties.getProperty("theme[main_fg]"), TextColor.ANSI.WHITE);
      this.tableSelectionBgColor = parseColor(rawThemeProperties.getProperty("theme[selected_bg]"), TextColor.ANSI.CYAN);
      this.highlightFgColor = parseColor(rawThemeProperties.getProperty("theme[hi_fg]"), fallbackForegroundColor);
      this.inactiveFgColor = parseColor(rawThemeProperties.getProperty("theme[inactive_fg]"), fallbackForegroundColor);
      this.titleColor = parseColor(rawThemeProperties.getProperty("theme[title]"), fallbackForegroundColor);
      this.divLineColor = parseColor(rawThemeProperties.getProperty("theme[div_line]"), fallbackForegroundColor);
      this.selectedFgColor = parseColor(rawThemeProperties.getProperty("theme[selected_fg]"), fallbackForegroundColor);
      this.selectedBgColor = parseColor(rawThemeProperties.getProperty("theme[selected_bg]"), fallbackBackgroundColor);

      // Only map `main_bg` and `main_fg` into guiThemeProperties
      transformCustomKeysToPropertyThemeKeys();
    } catch (IOException e) {
      logger.error("Error loading theme from {}. Using fallback colors/default theme. Error: {}", themePath, e.getMessage());
    }
  }


  private void transformCustomKeysToPropertyThemeKeys() {
    guiThemeProperties.clear();

    String mainBgValue = rawThemeProperties.getProperty("theme[main_bg]");
    String mainFgValue = rawThemeProperties.getProperty("theme[main_fg]");

    if (mainBgValue != null) {
      guiThemeProperties.setProperty("background", mainBgValue);
    }
    if (mainFgValue != null) {
      guiThemeProperties.setProperty("foreground", mainFgValue);
    }

    String selectedBgValue = rawThemeProperties.getProperty("theme[selected_bg]");
    String selectedFgValue = rawThemeProperties.getProperty("theme[selected_fg]");
    String hiFgValue = rawThemeProperties.getProperty("theme[hi_fg]");

    if (mainBgValue != null) {
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.background", mainBgValue);
    }
    if (mainFgValue != null) {
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.foreground", mainFgValue);
    }

    if (selectedBgValue != null) {
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.background[FOCUSED]", selectedBgValue);
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.background[SELECTED]", selectedBgValue);
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.background[ACTIVE]", selectedBgValue);
    }

    if (selectedFgValue != null) {
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.foreground[FOCUSED]", selectedFgValue);
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.foreground[SELECTED]", selectedFgValue);
    }

    if (hiFgValue != null) {
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.Button.foreground[ACTIVE]", hiFgValue);
    }

    String divLineValue = rawThemeProperties.getProperty("theme[div_line]");
    if (divLineValue != null) {
      guiThemeProperties.setProperty("com.googlecode.lanterna.gui2.AbstractBorder.foreground[ACTIVE]", divLineValue);
    }

    // Keep this logging. It shows exactly what is being passed to PropertyTheme.
    logger.debug("--- Final guiThemeProperties Content ---");
    guiThemeProperties.forEach((key, value) -> logger.debug("  {}: {}", key, value));
    logger.debug("--------------------------------------");
  }

  public List<String> getAvailableThemes(Path themeDirectory) {
    List<String> themeNames = new ArrayList<>();

    if (!Files.isDirectory(themeDirectory)) {
      logger.warn("Theme directory not found or not a directory: {}. No themes available from file system.", themeDirectory);
      return themeNames;
    }

    try (Stream<Path> paths = Files.list(themeDirectory)) {
      paths.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(THEME_FILE_EXTENSION))
        .map(path -> path.getFileName().toString().substring(0, path.getFileName().toString().length() - THEME_FILE_EXTENSION.length()))
        .sorted()
        .forEach(themeNames::add);
    } catch (IOException e) {
      logger.error("Error listing theme files in {}: {}", themeDirectory, e.getMessage());
    }

    return themeNames;
  }

  public Theme createGuiTheme() {
    return new PropertyTheme(guiThemeProperties);
  }

  // --- General background/foreground accessors used by Lanterna's Theme ---
  public TextColor getMainBackgroundColor() {
    return parseColor(guiThemeProperties.getProperty("background"), fallbackBackgroundColor);
  }

  public TextColor getMainForegroundColor() {
    return parseColor(guiThemeProperties.getProperty("foreground"), fallbackForegroundColor);
  }

  // --- Accessors for dedicated color fields ---

  public TextColor getPositiveChangeColor() {
    return positiveChangeFgColor;
  }

  public TextColor getNegativeChangeColor() {
    return negativeChangeFgColor;
  }

  public TextColor getTableHeaderBackgroundColor() {
    return tableHeaderBgColor;
  }

  public TextColor getTableHeaderForegroundColor() {
    return tableHeaderFgColor;
  }

  public TextColor getTableSelectionBackgroundColor() {
    return tableSelectionBgColor;
  }

  public TextColor getHighlightForegroundColor() {
    return highlightFgColor;
  }

  public TextColor getInactiveForegroundColor() {
    return inactiveFgColor;
  }

  public TextColor getTitleColor() {
    return titleColor;
  }

  public TextColor getDivLineColor() {
    return divLineColor;
  }

  public TextColor getSelectedForegroundColor() {
    return selectedFgColor;
  }

  public TextColor getSelectedBackgroundColor() {
    return selectedBgColor;
  }

  private TextColor parseColor(String colorString, TextColor fallback) {
    if (colorString == null || colorString.trim().isEmpty()) {
      return fallback;
    }
    try {
      return TextColor.Factory.fromString(colorString.trim());
    } catch (IllegalArgumentException e) {
      logger.warn("Could not parse color string '{}'. Using fallback. Error: {}", colorString, e.getMessage());
      return fallback;
    }
  }
}



