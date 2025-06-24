package com.lyubomirbozhinov.mticky.tui.table;

import com.lyubomirbozhinov.mticky.tui.theme.ThemeLoader;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableCellRenderer;

public class StockTableCellRenderer implements TableCellRenderer<String> {

  private final ThemeLoader themeLoader;
  private final int leftPad;
  private static final int MIN_CELL_WIDTH = 5; // A reasonable minimum width for cells

  // NEW: Fields to store custom change colors
  private TextColor positiveChangeColor;
  private TextColor negativeChangeColor;

  public StockTableCellRenderer(ThemeLoader themeLoader, int leftPad) {
    this.themeLoader = themeLoader;
    this.leftPad = leftPad;
    // Initialize with default fallback colors in case setters are not called
    this.positiveChangeColor = TextColor.ANSI.GREEN; // Default green
    this.negativeChangeColor = TextColor.ANSI.RED;   // Default red
  }

  // NEW: Setter methods for custom change colors
  public void setPositiveChangeColor(TextColor color) {
    this.positiveChangeColor = color;
  }

  public void setNegativeChangeColor(TextColor color) {
    this.negativeChangeColor = color;
  }

  @Override
  public void drawCell(Table<String> table, String cell, int columnIndex, int rowIndex, TextGUIGraphics graphics) {
    String cellText = cell;

    TextColor currentFg;
    TextColor currentBg;

    if (table.getSelectedRow() == rowIndex) {
      currentFg = themeLoader.getSelectedForegroundColor();
      currentBg = themeLoader.getSelectedBackgroundColor();
    } else {
      // Apply color based on stock change for specific columns
      if (columnIndex == 2 || columnIndex == 3) { // Assuming Δ$ is col 2, Δ% is col 3
        try {
          // Attempt to parse the change value to determine color
          String rawValue = cellText.trim();
          // Remove leading/trailing padding from formatTableCell if present
          // In StockMonitorTui, formatTableCell adds padding, so we need to trim it.

          // Remove currency symbols, percentage signs, or other non-numeric chars for robust parsing
          // Keeps digits, '.', '-', '+'
          rawValue = rawValue.replaceAll("[^\\d.\\-+]", "");

          double value = Double.parseDouble(rawValue);
          if (value > 0) {
            currentFg = positiveChangeColor; // Use the injected positive color
          } else if (value < 0) {
            currentFg = negativeChangeColor; // Use the injected negative color
          } else {
            currentFg = themeLoader.getMainForegroundColor(); // No change
          }
        } catch (NumberFormatException e) {
          // Fallback to default if parsing fails (e.g., "N/A" or malformed data)
          currentFg = themeLoader.getMainForegroundColor();
        }
      } else {
        currentFg = themeLoader.getMainForegroundColor();
      }
      currentBg = themeLoader.getMainBackgroundColor(); // Background for non-selected rows
    }

    graphics.setBackgroundColor(currentBg);
    graphics.setForegroundColor(currentFg);

    TerminalSize cellSize = graphics.getSize();

    // Apply left padding and trim if necessary
    String displayString = " ".repeat(leftPad) + cellText.trim();
    if (displayString.length() > cellSize.getColumns()) {
      displayString = displayString.substring(0, cellSize.getColumns());
    } else {
      // Pad the string to fill the entire cell width, ensuring background fills
      displayString = String.format("%-" + cellSize.getColumns() + "s", displayString);
    }

    graphics.putString(0, 0, displayString);
  }

  @Override
  public TerminalSize getPreferredSize(Table<String> table, String cell, int columnIndex, int rowIndex) {
    // Calculate preferred width based on the cell's content length + padding
    // Assuming cell content already has been 'formatted' for width from StockMonitorTui's updateTableDisplay
    // So, we mostly just need to return the actual string length for the table layout to work.
    // The `leftPad` is accounted for in the `drawCell` method's string manipulation.
    int contentWidth = cell.trim().length();
    int preferredWidth = contentWidth + leftPad; // Add left padding to preferred width
    preferredWidth = Math.max(preferredWidth, MIN_CELL_WIDTH); // Ensure minimum width

    // Cells are typically single line, so height is 1
    return new TerminalSize(preferredWidth, 1);
  }
}

