package com.lyubomirbozhinov.mticky.tui;

import com.lyubomirbozhinov.mticky.api.FinnhubClient;
import com.lyubomirbozhinov.mticky.config.ConfigManager;
import com.lyubomirbozhinov.mticky.stock.StockQuote;
import com.lyubomirbozhinov.mticky.stock.StockService;

import com.lyubomirbozhinov.mticky.tui.dialogs.ThemedListSelectDialog;
import com.lyubomirbozhinov.mticky.tui.dialogs.ThemedMessageDialog;
import com.lyubomirbozhinov.mticky.tui.dialogs.ThemedTextInputDialog;
import com.lyubomirbozhinov.mticky.tui.table.StockTableCellRenderer;
import com.lyubomirbozhinov.mticky.tui.table.StockTableHeaderRenderer;
import com.lyubomirbozhinov.mticky.tui.theme.ThemeLoader;
import com.lyubomirbozhinov.mticky.tui.util.DummyTerminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListener;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.function.Consumer;

public class StockMonitorTui {

  private static final Logger logger = LoggerFactory.getLogger(StockMonitorTui.class);

  private final ConfigManager configManager;
  private final ScheduledExecutorService executorService;
  private final StockService stockService;
  private final ThemeLoader themeLoader;

  private static final int LEFT_PAD_SPACES = 1;
  private static final int RIGHT_PAD_SPACES = 1;
  private static final int TOTAL_HORIZONTAL_PADDING = LEFT_PAD_SPACES + RIGHT_PAD_SPACES;

  private ScheduledFuture<?> dataRefreshFuture;
  private FinnhubClient finnhubClient;
  private Terminal terminal;
  private Screen screen;
  private MultiWindowTextGUI textGUI;
  private BasicWindow mainWindow;
  private Table<String> stockTable;
  private Label statusLabel;
  private Label commandLabel;
  private Panel headerPanel;

  private final Map<String, StockQuote> stockData = new ConcurrentHashMap<>();
  private volatile String currentStatus = "Starting...";
  private String selectedSymbol;

  private StockTableHeaderRenderer stockTableHeaderRenderer;
  private StockTableCellRenderer stockTableCellRenderer;

  private String[] columnLabels;

  public StockMonitorTui(ConfigManager configManager, ScheduledExecutorService executorService) {
    this.configManager = Objects.requireNonNull(configManager, "ConfigManager cannot be null");
    this.executorService = Objects.requireNonNull(executorService, "ExecutorService cannot be null");
    this.stockService = new StockService();
    this.themeLoader = new ThemeLoader();

    this.columnLabels = new String[]{
      "Symbol",
      "Price",
      "Δ$",
      "Δ%",
      "Last Updated"
    };

    this.stockTableHeaderRenderer = new StockTableHeaderRenderer(themeLoader, LEFT_PAD_SPACES);
    this.stockTableCellRenderer = new StockTableCellRenderer(themeLoader, LEFT_PAD_SPACES);
  }

  public void start() throws IOException {
    logger.info("Starting TUI...");

    if (handleCIEnvironment()) {
      return;
    }

    initializeTerminal();
    loadThemeProperties();

    // Apply theme early for pre-init dialogs
    try {
      textGUI.setTheme(themeLoader.createGuiTheme());
    } catch (Exception e) {
      logger.warn("Could not apply initial global GUI theme: {}", e.getMessage(), e);
    }

    boolean apiKeyProvided = ensureApiKeyPresent();
    if (!apiKeyProvided) {
      logger.info("API Key not provided or cancelled. Exiting application.");
      if (terminal != null) terminal.close();
      if (screen != null) screen.stopScreen();
      return;
    }

    this.finnhubClient = new FinnhubClient(configManager.getFinnhubApiKey());
    logger.info("FinnhubClient initialized.");

    initializeUiComponents();
    refreshAllStocks();
    startDataRefreshScheduler();

    textGUI.addWindowAndWait(mainWindow);

    stop(); // Cleanup after mainWindow closes
  }

  public void stop() throws IOException {
    logger.info("Stopping TUI...");

    // Cancel the scheduled task before shutting down the executor
    if (dataRefreshFuture != null && !dataRefreshFuture.isDone()) {
      dataRefreshFuture.cancel(true);
      logger.info("Data refresh scheduler cancelled.");
    }

    executorService.shutdownNow();

    if (screen != null) {
      try {
        screen.stopScreen();
      } catch (IOException e) {
        logger.warn("Failed to stop screen cleanly", e);
      }
    }

    if (terminal != null) {
      try {
        terminal.close();
      } catch (IOException e) {
        logger.warn("Failed to close terminal cleanly", e);
      }
    }
  }

  private boolean handleCIEnvironment() throws IOException {
    boolean isCI = "true".equalsIgnoreCase(System.getenv("CI"));
    if (!isCI) {
      return false;
    }

    System.out.println("MTICKY: Detected CI environment, enabling headless mode.");
    Terminal dummyTerminal = new DummyTerminal();
    Screen dummyScreen = new TerminalScreen(dummyTerminal);
    dummyScreen.startScreen();
    dummyScreen.stopScreen();

    System.out.println("MTICKY: Exiting after minimal startup for reflection config.");
    System.exit(0);
    return true;
  }

  private boolean ensureApiKeyPresent() {
    String apiKey = configManager.getFinnhubApiKey();
    if (apiKey != null && !apiKey.trim().isEmpty()) {
      return true;
    }

    final AtomicBoolean keyProvided = new AtomicBoolean(false);

    new ThemedTextInputDialog(
      "Welcome to mticky!",
      "Please enter your Finnhub.io API key:",
      "",
      themeLoader,
      new ThemedTextInputDialog.TextInputDialogCallback() {
        @Override
        public void onInput(String inputKey) {
          if (inputKey == null || inputKey.trim().isEmpty()) {
            updateStatus("API key not provided. Application will exit.");
            return;
          }
          configManager.setFinnhubApiKey(inputKey.trim());
          configManager.save();
          keyProvided.set(true);
          updateStatus("API key saved.");
        }

        @Override
        public void onCancel() {
          updateStatus("API key input cancelled. Application will exit.");
        }
      }
    ).showModal(textGUI);

    return keyProvided.get();
  }

  private void initializeTerminal() throws IOException {
    terminal = new DefaultTerminalFactory().createTerminal();
    screen = new TerminalScreen(terminal);
    screen.startScreen();
    textGUI = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace());
  }

  private void loadThemeProperties() {
    String themeName = configManager.getTheme();
    themeLoader.loadTheme(themeName, configManager.getThemesDirectory());
  }

  private void initializeUiComponents() {
    mainWindow = new BasicWindow("mticky - A simple Java stock monitor for terminal dwellers");
    mainWindow.setHints(Arrays.asList(Window.Hint.FULL_SCREEN));
    mainWindow.addWindowListener(createMainWindowListener());

    Panel mainPanel = new Panel(new BorderLayout());
    headerPanel = new Panel(new LinearLayout(Direction.VERTICAL));

    headerPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    statusLabel = new Label(" ".repeat(LEFT_PAD_SPACES) + currentStatus);
    headerPanel.addComponent(statusLabel.withBorder(Borders.singleLine()));
    headerPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    mainPanel.addComponent(headerPanel, BorderLayout.Location.TOP);

    stockTable = new Table<>(columnLabels);
    stockTable.setTableHeaderRenderer(stockTableHeaderRenderer);
    stockTable.setTableCellRenderer(stockTableCellRenderer);
    stockTable.setSelectAction(this::handleTableSelection);

    mainPanel.addComponent(stockTable, BorderLayout.Location.CENTER);

    commandLabel = new Label(" ".repeat(LEFT_PAD_SPACES) + "[A]dd [D]elete [T]heme [R]efresh Interval [Q]uit");
    mainPanel.addComponent(commandLabel.withBorder(Borders.singleLine()), BorderLayout.Location.BOTTOM);

    mainWindow.setComponent(mainPanel);
    applyColorsToComponents();
  }

  private WindowListener createMainWindowListener() {
    return new WindowListener() {
      @Override
      public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) { /* No-op */ }

      @Override
      public void onMoved(Window window, TerminalPosition oldPosition, TerminalPosition newPosition) { /* No-op */ }

      @Override
      public void onInput(Window window, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
        handleKeyPress(keyStroke);
        deliverEvent.set(true);
      }

      @Override
      public void onUnhandledInput(Window window, KeyStroke keyStroke, AtomicBoolean isProcessed) {
        isProcessed.set(false);
      }
    };
  }

  private void startDataRefreshScheduler() {
    // Cancel existing task if any
    if (dataRefreshFuture != null && !dataRefreshFuture.isDone()) {
      dataRefreshFuture.cancel(true);
      logger.info("Existing data refresh task cancelled.");
    }

    // Schedule new task with current interval from ConfigManager
    int currentInterval = configManager.getRefreshIntervalSeconds();
    dataRefreshFuture = executorService.scheduleAtFixedRate(
      this::refreshAllStocks,
      currentInterval,
      currentInterval,
      TimeUnit.SECONDS
    );
    logger.info("Data refresh scheduled to run every {} seconds.", currentInterval);
  }

  private void refreshAllStocks() {
    if (finnhubClient == null) {
      updateStatus("API Key missing. Cannot refresh stocks.");
      textGUI.getGUIThread().invokeLater(() -> stockData.clear());
      textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
      return;
    }

    Set<String> watchlist = configManager.getWatchlist();
    if (watchlist.isEmpty()) {
      updateStatus("No stocks in watchlist. Press 'a' to add some.");
      textGUI.getGUIThread().invokeLater(() -> stockTable.getTableModel().clear());
      return;
    }

    updateStatus("Refreshing " + watchlist.size() + " stocks...");

    List<String> failedSymbols = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(watchlist.size());

    for (String symbol : watchlist) {
      finnhubClient.getStockQuote(symbol)
        .thenAccept(optionalQuote -> {
          if (optionalQuote.isPresent()) {
            stockData.put(symbol, optionalQuote.get());
          } else {
            logger.warn("No quote available for symbol: {}", symbol);
            synchronized (failedSymbols) { failedSymbols.add(symbol); }
          }
          latch.countDown();
        })
        .exceptionally(throwable -> {
          logger.warn("Failed to fetch quote for {}", symbol, throwable);
          synchronized (failedSymbols) { failedSymbols.add(symbol); }
          latch.countDown();
          return null;
        });
    }

    executorService.submit(() -> {
      try {
        latch.await(configManager.getRefreshIntervalSeconds(), TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Stock refresh interrupted while waiting for all quotes.", e);
      } finally {
        textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
        if (!failedSymbols.isEmpty()) {
          updateStatus("Updated with errors: " + String.join(", ", failedSymbols));
        } else if (!watchlist.isEmpty()) {
          updateStatus(String.format("Updated %d stocks at %s", stockData.size(), stockService.formatTimestamp(LocalDateTime.now())));
        } else {
          updateStatus("Ready - Press 'a' to add stocks to watchlist");
        }
      }
    });
  }

  private void updateTableDisplay() {
    textGUI.getGUIThread().invokeLater(() -> {
      String symbolToReSelect = null;
      int currentSelectedRow = stockTable.getSelectedRow();
      if (currentSelectedRow != -1 && stockTable.getTableModel().getRowCount() > 0 && currentSelectedRow < stockTable.getTableModel().getRowCount()) {
        symbolToReSelect = stockTable.getTableModel().getRow(currentSelectedRow).get(0).trim();
      }

      int symbolColContentWidth = "Symbol".length();
      int priceColContentWidth = "Price".length();
      int changeColContentWidth = "Δ$".length();
      int percentChangeColContentWidth = "Δ%".length();
      int lastUpdatedColContentWidth = "Last Updated".length();

      for (StockQuote quote : stockData.values()) {
        symbolColContentWidth = Math.max(symbolColContentWidth, quote.getSymbol().length());
        priceColContentWidth = Math.max(priceColContentWidth, stockService.formatPrice(quote.getCurrentPrice()).length());
        changeColContentWidth = Math.max(changeColContentWidth, stockService.formatChange(quote.getChange()).length());
        percentChangeColContentWidth = Math.max(percentChangeColContentWidth, stockService.formatPercentChange(quote.getPercentChange()).length());
        lastUpdatedColContentWidth = Math.max(lastUpdatedColContentWidth, stockService.formatTimestamp(quote.getLastUpdated()).length());
      }

      stockTable.getTableModel().clear();

      int newSelectionIndex = -1;
      int currentRowIndex = 0;

      for (Map.Entry<String, StockQuote> entry : stockData.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        StockQuote quote = entry.getValue();

        String[] row = {
          formatTableCell(quote.getSymbol(), symbolColContentWidth),
          formatTableCell(stockService.formatPrice(quote.getCurrentPrice()), priceColContentWidth),
          formatTableCell(stockService.formatChange(quote.getChange()), changeColContentWidth),
          formatTableCell(stockService.formatPercentChange(quote.getPercentChange()), percentChangeColContentWidth),
          formatTableCell(stockService.formatTimestamp(quote.getLastUpdated()), lastUpdatedColContentWidth)
        };
        stockTable.getTableModel().addRow(row);

        if (symbolToReSelect != null && symbolToReSelect.equals(quote.getSymbol())) {
          newSelectionIndex = currentRowIndex;
        }
        currentRowIndex++;
      }

      if (newSelectionIndex != -1) {
        stockTable.setSelectedRow(newSelectionIndex);
        return;
      }

      if (stockTable.getTableModel().getRowCount() > 0) {
        stockTable.setSelectedRow(0);
      }
    });
  }

  private void updateStatus(String status) {
    currentStatus = status;
    textGUI.getGUIThread().invokeLater(() -> {
      if (statusLabel != null) {
        statusLabel.setText(" ".repeat(LEFT_PAD_SPACES) + currentStatus);
        try {
          screen.refresh();
        } catch (IOException e) {
          logger.error("Error refreshing screen after status update: {}", e.getMessage(), e);
        }
      }
    });
  }

  private void handleKeyPress(KeyStroke keyStroke) {
    if (keyStroke == null) return;

    if (keyStroke.getKeyType() == KeyType.Character) {
      handleCharacterKey(Character.toLowerCase(keyStroke.getCharacter()));
      return;
    }

    if (keyStroke.getKeyType() == KeyType.EOF || (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character && keyStroke.getCharacter() == 'c')) {
      performShutdown("Ctrl+C or EOF detected");
    }
  }

  private void handleCharacterKey(char keyChar) {
    switch (keyChar) {
      case 'a':
      handleAddStock();
      break;
      case 'd':
      handleDeleteStock();
      break;
      case 't':
      handleChangeTheme();
      break;
      case 'r':
      handleChangeRefreshInterval();
      break;
      case 'q':
      performShutdown("'q' key pressed");
      break;
      default:
      break;
    }
  }

  private void performShutdown(String reason) {
    logger.info("Initiating shutdown: {}", reason);
    new ThemedMessageDialog("Confirm Exit", "Are you sure you want to exit?", themeLoader, true, () -> {
      if (mainWindow != null) {
        mainWindow.close();
      }
    }).showDialog(textGUI);
  }

  private void handleAddStock() {
    ThemedTextInputDialog addDialog = new ThemedTextInputDialog(
      "Add Stock",
      "Enter stock symbol:",
      "",
      themeLoader,
      new ThemedTextInputDialog.TextInputDialogCallback() {
        @Override
        public void onInput(String symbol) {
          if (symbol == null || symbol.trim().isEmpty()) {
            showInfoMessage("Warning", "Stock symbol cannot be empty.");
            return;
          }

          String normalizedSymbol = stockService.normalizeStockSymbol(symbol);

          if (configManager.isInWatchlist(normalizedSymbol)) {
            showErrorDialog("Already Added", normalizedSymbol + " is already being monitored.");
            return;
          }

          if (!configManager.addToWatchlist(normalizedSymbol)) {
            updateStatus("Failed to add " + normalizedSymbol + " to watchlist (internal error).");
            return;
          }

          updateStatus("Adding " + normalizedSymbol + " to watchlist...");

          finnhubClient.getStockQuote(normalizedSymbol)
            .thenAccept(optionalQuote -> {
              if (optionalQuote.isPresent()) {
                configManager.save();
                stockData.put(normalizedSymbol, optionalQuote.get());
                textGUI.getGUIThread().invokeLater(StockMonitorTui.this::updateTableDisplay);
                showInfoMessage("Success", "Stock '" + normalizedSymbol + "' added and data fetched.");
              } else {
                showErrorDialog("No Data", "Stock '" + normalizedSymbol + "' not found or no data available.");
                configManager.removeFromWatchlist(normalizedSymbol);
                configManager.save();
                stockData.remove(normalizedSymbol);
                textGUI.getGUIThread().invokeLater(StockMonitorTui.this::updateTableDisplay);
              }
            })
            .exceptionally(ex -> {
              logger.error("Failed to fetch stock data for {}", normalizedSymbol, ex);
              configManager.removeFromWatchlist(normalizedSymbol);
              configManager.save();
              stockData.remove(normalizedSymbol);
              textGUI.getGUIThread().invokeLater(StockMonitorTui.this::updateTableDisplay);
              showErrorDialog("Error", "Failed to fetch data for " + normalizedSymbol + ": " + ex.getMessage() + "\nStock removed from watchlist.");
              return null;
            });
        }

        @Override
        public void onCancel() {
          updateStatus("Add stock operation cancelled.");
        }
      }
    );
    addDialog.showDialog(textGUI);
  }

  private void handleDeleteStock() {
    if (configManager.getWatchlist().isEmpty()) {
      new ThemedMessageDialog("Delete Stock", "Watchlist is empty. Nothing to delete.", themeLoader, null).showDialog(textGUI);
      return;
    }

    List<String> stocks = List.copyOf(configManager.getWatchlist());

    new ThemedListSelectDialog("Delete Stock", "Select stock to delete:", stocks, themeLoader, createDeleteStockAction()).showDialog(textGUI);
  }

  private Consumer<String> createDeleteStockAction() {
    return selectedSymbol -> {
      if (selectedSymbol == null) {
        updateStatus("Delete stock operation cancelled.");
        return;
      }

      String normalizedSymbol = selectedSymbol.trim().toUpperCase();
      if (!configManager.removeFromWatchlist(normalizedSymbol)) {
        showErrorDialog("Not Found", normalizedSymbol + " is not in the watchlist.");
        return;
      }

      stockData.remove(normalizedSymbol);
      configManager.save();
      textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
      showInfoMessage("Success", "Stock '" + normalizedSymbol + "' removed from watchlist.");
    };
  }

  private void showErrorDialog(String title, String message) {
    textGUI.getGUIThread().invokeLater(() -> new ThemedMessageDialog(title, message, themeLoader, null).showDialog(textGUI));
  }

  private void showInfoMessage(String title, String message) {
    showInfoMessage(title, message, null);
  }

  private void showInfoMessage(String title, String message, Runnable onClosed) {
    textGUI.getGUIThread().invokeLater(() -> new ThemedMessageDialog(title, message, themeLoader, onClosed).showDialog(textGUI));
  }

  private void handleTableSelection() {
    int selectedRow = stockTable.getSelectedRow();
    if (selectedRow != -1) {
      String symbol = stockTable.getTableModel().getRow(selectedRow).get(0).trim();
      updateStatus("Selected: " + symbol);
    }
  }

  private void handleChangeTheme() {
    String currentTheme = configManager.getTheme();
    List<String> availableThemesList = themeLoader.getAvailableThemes(configManager.getThemesDirectory());

    if (availableThemesList.isEmpty()) {
      showErrorDialog("Themes", "No themes available.");
      return;
    }

    new ThemedListSelectDialog(
      "Select Theme",
      "Choose a theme:",
      availableThemesList,
      themeLoader,
      createChangeThemeAction(currentTheme)
    ).showDialog(textGUI);
  }

  private Consumer<String> createChangeThemeAction(String currentTheme) {
    return selectedThemeName -> {
      if (selectedThemeName == null || selectedThemeName.trim().isEmpty()) {
        updateStatus("Theme change cancelled or invalid input.");
        return;
      }

      String trimmedNewTheme = selectedThemeName.trim();
      if (trimmedNewTheme.equalsIgnoreCase(currentTheme)) {
        updateStatus("Theme is already set to: " + trimmedNewTheme);
        return;
      }

      updateStatus("Applying theme: " + trimmedNewTheme + "...");
      configManager.setTheme(trimmedNewTheme);
      configManager.save();
      themeLoader.loadTheme(trimmedNewTheme, configManager.getThemesDirectory());

      textGUI.getGUIThread().invokeLater(() -> {
        applyColorsToComponents();
        stockTableHeaderRenderer = new StockTableHeaderRenderer(themeLoader, LEFT_PAD_SPACES);
        stockTableCellRenderer = new StockTableCellRenderer(themeLoader, LEFT_PAD_SPACES);

        if (stockTableCellRenderer != null) {
          stockTableCellRenderer.setPositiveChangeColor(themeLoader.getPositiveChangeColor());
          stockTableCellRenderer.setNegativeChangeColor(themeLoader.getNegativeChangeColor());
        }

        // Preserve table data and selection when recreating
        List<List<String>> currentTableRows = new ArrayList<>();
        for (int r = 0; r < stockTable.getTableModel().getRowCount(); r++) {
          currentTableRows.add(new ArrayList<>(stockTable.getTableModel().getRow(r)));
        }
        int currentSelectedRow = stockTable.getSelectedRow();

        Table<String> newStockTable = new Table<>(columnLabels);
        newStockTable.setTableHeaderRenderer(stockTableHeaderRenderer);
        newStockTable.setTableCellRenderer(stockTableCellRenderer);
        newStockTable.setSelectAction(this::handleTableSelection);

        for (List<String> row : currentTableRows) {
          newStockTable.getTableModel().addRow(row.toArray(new String[0]));
        }
        newStockTable.setSelectedRow(currentSelectedRow);

        Panel mainPanel = (Panel) mainWindow.getComponent();
        mainPanel.getChildren().stream()
          .filter(c -> c instanceof Table)
          .findFirst()
          .ifPresent(mainPanel::removeComponent);

        mainPanel.addComponent(newStockTable, BorderLayout.Location.CENTER);
        stockTable = newStockTable;
        mainWindow.setComponent(mainPanel);

        stockTable.invalidate();
        try {
          screen.doResizeIfNecessary();
          screen.newTextGraphics().setBackgroundColor(themeLoader.getMainBackgroundColor());
          screen.clear();
          screen.refresh();
        } catch (IOException e) {
          logger.warn("Failed to refresh screen after theme change", e);
        }
        updateStatus("Theme applied: " + trimmedNewTheme);
      });
    };
  }

  private void handleChangeRefreshInterval() {
    String currentInterval = String.valueOf(configManager.getRefreshIntervalSeconds());
    new ThemedTextInputDialog(
      "Refresh Interval",
      "Enter new refresh interval in seconds (e.g., 5, 10, 30):",
      currentInterval,
      themeLoader,
      new ThemedTextInputDialog.TextInputDialogCallback() {
        @Override
        public void onInput(String input) {
          if (input == null || input.trim().isEmpty()) {
            showInfoMessage("Warning", "Refresh interval cannot be empty.");
            return;
          }
          try {
            int newInterval = Integer.parseInt(input.trim());
            if (newInterval <= 0) {
              showErrorDialog("Invalid Input", "Refresh interval must be a positive number.");
              return;
            }
            if (newInterval == configManager.getRefreshIntervalSeconds()) {
              updateStatus("Refresh interval already set to " + newInterval + " seconds.");
              return;
            }
            configManager.setRefreshIntervalSeconds(newInterval);
            configManager.save();
            updateStatus("Refresh interval set to " + newInterval + " seconds. Restarting data fetch...");
            startDataRefreshScheduler(); // Restart the scheduler with the new interval
            showInfoMessage("Success", "Refresh interval updated to " + newInterval + " seconds.");
          } catch (NumberFormatException e) {
            showErrorDialog("Invalid Input", "Please enter a valid number for the interval.");
          }
        }

        @Override
        public void onCancel() {
          updateStatus("Refresh interval change cancelled.");
        }
      }
    ).showModal(textGUI);
  }

  private String formatTableCell(String value, int maxContentWidth) {
    String paddedValue = " ".repeat(LEFT_PAD_SPACES) + value;
    int effectiveContentWidth = Math.max(value.length(), maxContentWidth);
    int targetWidth = effectiveContentWidth + TOTAL_HORIZONTAL_PADDING;
    return String.format("%-" + targetWidth + "s", paddedValue);
  }

  private void applyColorsToComponents() {
    if (mainWindow == null) return;

    try {
      textGUI.setTheme(themeLoader.createGuiTheme());
    } catch (Exception e) {
      logger.warn("Could not create or apply PropertyTheme to GUI: {}", e.getMessage(), e);
    }

    if (stockTableCellRenderer != null) {
      stockTableCellRenderer.setPositiveChangeColor(themeLoader.getPositiveChangeColor());
      stockTableCellRenderer.setNegativeChangeColor(themeLoader.getNegativeChangeColor());
    }

    TextColor mainBg = themeLoader.getMainBackgroundColor();
    TextColor mainFg = themeLoader.getMainForegroundColor();
    TextColor highlightFg = themeLoader.getHighlightForegroundColor();

    Panel mainPanel = (Panel) mainWindow.getComponent();
    if (mainPanel != null) {
      mainPanel.setFillColorOverride(mainBg);
    }

    if (headerPanel != null) {
      headerPanel.setFillColorOverride(mainBg);
      headerPanel.getChildren().stream()
        .filter(c -> c instanceof Label)
        .map(c -> (Label) c)
        .forEach(label -> {
          label.setForegroundColor(mainFg);
          label.setBackgroundColor(mainBg);
        });
    }

    if (statusLabel != null) {
      statusLabel.setForegroundColor(mainFg);
      statusLabel.setBackgroundColor(mainBg);
    }
    if (commandLabel != null) {
      commandLabel.setForegroundColor(highlightFg);
      commandLabel.setBackgroundColor(mainBg);
    }
  }
}
