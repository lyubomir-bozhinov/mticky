package com.lyubomirbozhinov.mticky.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableHeaderRenderer;

public class StockTableHeaderRenderer implements TableHeaderRenderer<String> {

  private final ThemeLoader themeLoader;
  private final int leftPad;
  private static final int MIN_HEADER_WIDTH = 5; // A reasonable minimum width for headers

  public StockTableHeaderRenderer(ThemeLoader themeLoader, int leftPad) {
    this.themeLoader = themeLoader;
    this.leftPad = leftPad;
  }

  @Override
  public void drawHeader(Table<String> table, String columnLabel, int columnIndex, TextGUIGraphics graphics) {
    TextColor currentFg = themeLoader.getHighlightForegroundColor();
    TextColor currentBg = themeLoader.getMainBackgroundColor();

    graphics.setBackgroundColor(currentBg);
    graphics.setForegroundColor(currentFg);

    TerminalSize cellSize = graphics.getSize();
    String displayString = " ".repeat(leftPad) + columnLabel.trim();

    if (displayString.length() > cellSize.getColumns()) {
      displayString = displayString.substring(0, cellSize.getColumns());
    } else {
      displayString = String.format("%-" + cellSize.getColumns() + "s", displayString);
    }

    graphics.putString(0, 0, displayString);
  }

  @Override
  public TerminalSize getPreferredSize(Table<String> table, String columnLabel, int columnIndex) {
    // Calculate preferred width based on the columnLabel's length + padding
    int preferredWidth = columnLabel.length() + leftPad + 1; // +1 for the right padding/buffer
    preferredWidth = Math.max(preferredWidth, MIN_HEADER_WIDTH); // Ensure minimum width

    // Headers are typically single line, so height is 1
    return new TerminalSize(preferredWidth, 1);
  }
}
