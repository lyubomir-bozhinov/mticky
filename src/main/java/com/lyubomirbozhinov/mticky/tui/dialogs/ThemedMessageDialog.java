package com.lyubomirbozhinov.mticky.tui.dialogs;

import com.lyubomirbozhinov.mticky.tui.theme.ThemeLoader;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.TextColor;

import java.util.Arrays;

public class ThemedMessageDialog extends BasicWindow {
  private Runnable onClosedCallback;
  private static final int PAD_SPACES = 2;

  public ThemedMessageDialog(String title, String message, ThemeLoader themeLoader, Runnable onClosedCallback) {
    this(title, message, themeLoader, false, onClosedCallback); // Call the main constructor with showCancelButton = false
  }

  /**
     * Creates a custom themed message dialog.
     * @param title The title of the dialog window.
     * @param message The message displayed to the user.
     * @param themeLoader The ThemeLoader instance to get colors from.
     * @param showCancelButton If true, a Cancel button will be displayed.
     * @param onClosedCallback A Runnable to be executed when the dialog is closed (can be null).
     */
  public ThemedMessageDialog(String title, String message, ThemeLoader themeLoader, boolean showCancelButton, Runnable onClosedCallback) {
    super(title);
    this.onClosedCallback = onClosedCallback; // Store the callback
    setHints(Arrays.asList(Window.Hint.CENTERED));

    Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
    panel.setFillColorOverride(themeLoader.getMainBackgroundColor());

    panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    Panel messagePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    messagePanel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    messagePanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));
    Label messageLabel = new Label(message);
    messageLabel.setForegroundColor(themeLoader.getMainForegroundColor());
    messageLabel.setBackgroundColor(themeLoader.getMainBackgroundColor());
    messagePanel.addComponent(messageLabel);
    messagePanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));
    panel.addComponent(messagePanel);

    panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    buttonPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    buttonPanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));

    Button okButton = new Button("OK", () -> {
      close(); // Close the dialog first
      if (this.onClosedCallback != null) {
        this.onClosedCallback.run(); // Then invoke the callback
      }
    });

    buttonPanel.addComponent(okButton);

    if (showCancelButton) {
      Button cancelButton = new Button("Cancel", () -> {
        close();
      });
      buttonPanel.addComponent(cancelButton);
    }

    panel.addComponent(buttonPanel);

    panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    setComponent(panel);
  }

  /**
     * Displays the dialog. This method is now non-blocking.
     * @param textGUI The MultiWindowTextGUI instance to display the dialog on.
     */
  public void showDialog(MultiWindowTextGUI textGUI) {
    textGUI.addWindow(this);
    // The DefaultWindowManager will automatically focus the newly added window.
  }
}

