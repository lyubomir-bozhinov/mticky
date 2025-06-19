package com.lyubomirbozhinov.mticky.tui;

import com.lyubomirbozhinov.mticky.api.FinnhubClient;
import com.lyubomirbozhinov.mticky.config.ConfigManager;
import com.lyubomirbozhinov.mticky.stock.StockQuote;
import com.lyubomirbozhinov.mticky.stock.StockService;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListener;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class StockMonitorTui {
  private static final Logger logger = LoggerFactory.getLogger(StockMonitorTui.class);

  private final ConfigManager configManager;
  private final ScheduledExecutorService executorService;
  private final int refreshInterval;
  private final FinnhubClient finnhubClient;
  private final StockService stockService;

  // Define padding amounts
  private static final int LEFT_PAD_SPACES = 1;
  private static final int RIGHT_PAD_SPACES = 1;
  private static final int TOTAL_HORIZONTAL_PADDING = LEFT_PAD_SPACES + RIGHT_PAD_SPACES;

  private Terminal terminal;
  private Screen screen;
  private MultiWindowTextGUI textGUI;
  private BasicWindow mainWindow;
  private Table<String> stockTable;
  private Label statusLabel;
  private Label commandLabel;

  private final Map<String, StockQuote> stockData = new ConcurrentHashMap<>();
  private volatile String currentStatus = "Starting...";
  private String selectedSymbol;

  public StockMonitorTui(ConfigManager configManager, ScheduledExecutorService executorService,
    int refreshInterval) {
    this.configManager = Objects.requireNonNull(configManager, "ConfigManager cannot be null");
    this.executorService = Objects.requireNonNull(executorService, "ExecutorService cannot be null");
    if (refreshInterval <= 0) {
      throw new IllegalArgumentException("Refresh interval must be positive");
    }
    this.refreshInterval = refreshInterval;
    this.finnhubClient = new FinnhubClient(System.getenv("FINNHUB_API_KEY"));
    this.stockService = new StockService();
  }

  public void start() throws IOException {
    logger.info("Starting TUI...");

    if (handleCIEnvironment()) {
      return;
    }

    initializeTerminal();
    initializeUiComponents();

    refreshAllStocks();
    updateTableDisplay();

    startDataRefreshScheduler();

    textGUI.addWindowAndWait(mainWindow);
  }

  public void stop() throws IOException {
    logger.info("Stopping TUI...");

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

    executorService.shutdownNow();
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

  private void initializeTerminal() throws IOException {
    terminal = new DefaultTerminalFactory().createTerminal();
    screen = new TerminalScreen(terminal);
    screen.startScreen();
    textGUI = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace());
  }

  private void initializeUiComponents() {
    mainWindow = new BasicWindow("mticky - Stock Monitor");
    mainWindow.setHints(java.util.Arrays.asList(Window.Hint.FULL_SCREEN));

    mainWindow.addWindowListener(createMainWindowListener());

    Panel mainPanel = new Panel(new BorderLayout());

    Panel statusPanel = new Panel();
    statusPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    statusLabel = new Label(" ".repeat(LEFT_PAD_SPACES) + currentStatus);
    statusPanel.addComponent(statusLabel);
    statusPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    mainPanel.addComponent(statusPanel, BorderLayout.Location.TOP);

    // Headers are formatted once here with padding. They will not dynamically resize.
    String headerSymbol = "Symbol";
    String headerPrice = "Price";
    String headerChange = "Δ$";
    String headerPercentChange = "Δ%";
    String headerLastUpdated = "Last Updated";

    stockTable = new Table<>(
      formatTableCell(headerSymbol, headerSymbol.length()),
      formatTableCell(headerPrice, headerPrice.length()),
      formatTableCell(headerChange, headerChange.length()),
      formatTableCell(headerPercentChange, headerPercentChange.length()),
      formatTableCell(headerLastUpdated, headerLastUpdated.length())
    );
    stockTable.setSelectAction(this::handleTableSelection);

    mainPanel.addComponent(stockTable, BorderLayout.Location.CENTER);

    commandLabel = new Label(" ".repeat(LEFT_PAD_SPACES) + "[A]dd [D]elete [Q]uit");
    mainPanel.addComponent(commandLabel, BorderLayout.Location.BOTTOM);

    mainWindow.setComponent(mainPanel);
    textGUI.addWindow(mainWindow);
  }

  private WindowListener createMainWindowListener() {
    return new WindowListener() {
      @Override
      public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {}

      @Override
      public void onMoved(Window window, TerminalPosition oldPosition, TerminalPosition newPosition) {}

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
    executorService.scheduleAtFixedRate(
      this::refreshAllStocks,
      refreshInterval,
      refreshInterval,
      TimeUnit.SECONDS
    );
  }

  private void refreshAllStocks() {
    Set<String> watchlist = configManager.getWatchlist();

    if (watchlist.isEmpty()) {
      updateStatus("No stocks in watchlist. Press 'a' to add some.");
      textGUI.getGUIThread().invokeLater(() -> stockTable.getTableModel().clear());
      return;
    }

    updateStatus("Refreshing " + watchlist.size() + " stocks...");

    for (String symbol : watchlist) {
      finnhubClient.getStockQuote(symbol)
        .thenAccept(optionalQuote -> {
          optionalQuote.ifPresent(quote -> stockData.put(symbol, quote));
          updateTableDisplay();
          if (optionalQuote.isEmpty()) {
            logger.warn("No quote available for symbol: {}", symbol);
          }
        })
        .exceptionally(throwable -> {
          logger.error("Failed to fetch quote for {}", symbol, throwable);
          updateStatus("Error fetching " + symbol + ": " + throwable.getMessage());
          return null;
        });
    }
  }

  private void updateTableDisplay() {
    textGUI.getGUIThread().invokeLater(() -> {
      String symbolToReSelect = null;
      int currentSelectedRow = stockTable.getSelectedRow();
      if (currentSelectedRow != -1 && currentSelectedRow < stockTable.getTableModel().getRowCount()) {
        symbolToReSelect = stockTable.getTableModel().getRow(currentSelectedRow).get(0).trim();
      }

      // Initialize content widths with header lengths to ensure data columns are at least header-width
      int symbolColContentWidth = "Symbol".length();
      int priceColContentWidth = "Price".length();
      int changeColContentWidth = "Δ$".length();
      int percentChangeColContentWidth = "Δ%".length();
      int lastUpdatedColContentWidth = "Last Updated".length();

      // Find maximum content length for each column across all data
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

      // Populate table with dynamically padded data using helper method
      for (Map.Entry<String, StockQuote> entry : stockData.entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .toList()) {
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
        updateStatus(String.format("Updated %d stocks at %s",
          stockData.size(),
          stockService.formatTimestamp(LocalDateTime.now())));
        return;
      }

      if (stockData.isEmpty()) {
        updateStatus("Ready - Press 'a' to add stocks to watchlist");
        return;
      }

      stockTable.setSelectedRow(0);
      updateStatus(String.format("Updated %d stocks at %s",
        stockData.size(),
        stockService.formatTimestamp(LocalDateTime.now())));
    });
  }

  private void updateStatus(String status) {
    currentStatus = status;
    textGUI.getGUIThread().invokeLater(() -> {
      if (statusLabel != null) {
        statusLabel.setText(" ".repeat(LEFT_PAD_SPACES) + currentStatus);
      }
    });
  }

  private void handleKeyPress(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Character) {
      handleCharacterKey(Character.toLowerCase(keyStroke.getCharacter()));
      return;
    }

    if (keyStroke.getKeyType() == KeyType.EOF ||
  (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character &&
    keyStroke.getCharacter() == 'c')) {
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
      case 'q':
      performShutdown("'q' key pressed");
      break;
      default:
      break;
    }
  }

  private void performShutdown(String reason) {
    logger.info("Initiating shutdown: {}", reason);
    try {
      stop();
    } catch (IOException e) {
      logger.error("Error during shutdown initiated by {}: {}", reason, e.getMessage());
    } finally {
      System.exit(0);
    }
  }

  private void handleAddStock() {
    String symbol = TextInputDialog.showDialog(textGUI, "Add Stock", "Enter stock symbol:", "");
    if (symbol == null || symbol.trim().isEmpty()) {
      return;
    }

    String normalizedSymbol = stockService.normalizeStockSymbol(symbol);

    if (!stockService.isValidStockSymbol(normalizedSymbol)) {
      showErrorDialog("Invalid Symbol", "The symbol '" + symbol + "' is not valid.");
      return;
    }

    if (!configManager.addToWatchlist(normalizedSymbol)) {
      updateStatus(normalizedSymbol + " is already in watchlist");
      return;
    }

    updateStatus("Added " + normalizedSymbol + " to watchlist");
    configManager.save();

    finnhubClient.getStockQuote(normalizedSymbol)
      .thenAccept(optionalQuote -> {
        optionalQuote.ifPresent(quote -> stockData.put(normalizedSymbol, quote));
        textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
        if (optionalQuote.isEmpty()) {
          showErrorDialog("No Data", "No stock data available for: " + normalizedSymbol);
        }
      })
      .exceptionally(ex -> {
        logger.error("Failed to fetch stock data for {}", normalizedSymbol, ex);
        showErrorDialog("Error", "Error fetching stock data: " + ex.getMessage());
        return null;
      });
  }

  private void handleDeleteStock() {
    String symbol = TextInputDialog.showDialog(textGUI, "Delete Stock", "Enter stock symbol to remove:", "");
    if (symbol == null || symbol.trim().isEmpty()) {
      return;
    }

    String normalizedSymbol = symbol.trim().toUpperCase();

    if (!configManager.removeFromWatchlist(normalizedSymbol)) {
      showErrorDialog("Not Found", normalizedSymbol + " is not in the watchlist.");
      return;
    }

    stockData.remove(normalizedSymbol);
    configManager.save();
    updateStatus("Removed " + normalizedSymbol + " from watchlist");
    textGUI.getGUIThread().invokeLater(this::updateTableDisplay);
  }

  private void showErrorDialog(String title, String message) {
    textGUI.getGUIThread().invokeLater(() ->
      MessageDialog.showMessageDialog(textGUI, title, message));
  }

  private void handleTableSelection() {
    // TO-DO: Decide what to do with ENTER on selected item
  }

  /**
     * Formats a table cell value to include left and right padding,
     * and ensures it's left-aligned within the calculated total width.
     *
     * @param value The original string content for the cell.
     * @param maxContentWidth The maximum length of content (excluding padding) found
     * for this column across all rows (including header).
     * @return The formatted string for display in the table cell.
     */
  private String formatTableCell(String value, int maxContentWidth) {
    String paddedValue = " ".repeat(LEFT_PAD_SPACES) + value;
    int targetWidth = maxContentWidth + TOTAL_HORIZONTAL_PADDING;
    return String.format("%-" + targetWidth + "s", paddedValue);
  }
}

