package com.lyubomirbozhinov.mticky.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ThemedListSelectDialog extends BasicWindow {
  private static final int LEFT_PAD_SPACES = 2;

  private Table<String> selectionTable;
  private Consumer<String> onSelectedCallback;
  private StockTableCellRenderer cellRenderer;

  public ThemedListSelectDialog(String title, String message, List<String> itemsToDisplay, ThemeLoader themeLoader, Consumer<String> onSelectedCallback) {
    super(title);
    this.onSelectedCallback = onSelectedCallback;
    setHints(Arrays.asList(Window.Hint.CENTERED));

    Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
    panel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    Label messageLabel = new Label(" ".repeat(LEFT_PAD_SPACES) + message);
    messageLabel.setForegroundColor(themeLoader.getMainForegroundColor());
    messageLabel.setBackgroundColor(themeLoader.getMainBackgroundColor());
    panel.addComponent(messageLabel);

    selectionTable = new Table<>(" ");

    TableModel<String> tableModel = selectionTable.getTableModel();
    for (String item : itemsToDisplay) {
      tableModel.addRow(item);
    }

    this.cellRenderer = new StockTableCellRenderer(themeLoader, LEFT_PAD_SPACES * 2); 
    this.cellRenderer.setPositiveChangeColor(themeLoader.getPositiveChangeColor());
    this.cellRenderer.setNegativeChangeColor(themeLoader.getNegativeChangeColor());

    selectionTable.setTableCellRenderer(this.cellRenderer);

    int tableWidth = Math.max(message.length(), itemsToDisplay.stream().mapToInt(String::length).max().orElse(0) + 4); 
    tableWidth = Math.min(tableWidth + 10, 80); 
    int tableHeight = Math.min(itemsToDisplay.size(), 10) + 2; 
    selectionTable.setPreferredSize(new TerminalSize(tableWidth, tableHeight)); 
    panel.addComponent(selectionTable);

    selectionTable.setSelectAction(() -> {
      close();
      if (this.onSelectedCallback != null && selectionTable.getSelectedRow() != -1) {
        this.onSelectedCallback.accept(tableModel.getRow(selectionTable.getSelectedRow()).get(0));
      }
    });

    panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    buttonPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    buttonPanel.addComponent(new EmptySpace(new TerminalSize(LEFT_PAD_SPACES, 0)));

    Button selectButton = new Button("Select", () -> {
      close();
      if (this.onSelectedCallback != null && selectionTable.getSelectedRow() != -1) {
        this.onSelectedCallback.accept(tableModel.getRow(selectionTable.getSelectedRow()).get(0));
      }
    });
    buttonPanel.addComponent(selectButton);

    Button cancelButton = new Button("Cancel", () -> {
      close();
      if (this.onSelectedCallback != null) {
        this.onSelectedCallback.accept(null);
      }
    });
    buttonPanel.addComponent(cancelButton);

    panel.addComponent(buttonPanel);

    panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    setComponent(panel);
  }

  /**
     * Displays the dialog. This method is blocking.
     * @param textGUI The MultiWindowTextGUI instance to display the dialog on.
     */
  public void showDialog(MultiWindowTextGUI textGUI) {
    textGUI.addWindowAndWait(this);
  }
}
