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
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.TextColor;

import java.util.Arrays;

public class ThemedTextInputDialog extends BasicWindow {
  private static final int PAD_SPACES = 1;

  private TextBox textBox;
  private TextInputDialogCallback callback;

  public static interface TextInputDialogCallback {
    void onInput(String result);
    void onCancel();
  }

  public ThemedTextInputDialog(String title, String message, String initialValue, ThemeLoader themeLoader, TextInputDialogCallback callback) {
    super(title);
    this.callback = callback;
    setHints(Arrays.asList(Window.Hint.CENTERED));

    Panel mainContentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    mainContentPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());

    mainContentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    Panel messagePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    messagePanel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    messagePanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));
    Label messageLabel = new Label(message);
    messageLabel.setForegroundColor(themeLoader.getMainForegroundColor());
    messageLabel.setBackgroundColor(themeLoader.getMainBackgroundColor());
    messagePanel.addComponent(messageLabel);
    messagePanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));
    mainContentPanel.addComponent(messagePanel);

    mainContentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    Panel textBoxPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    textBoxPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    textBoxPanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));
    textBox = new TextBox(new TerminalSize(initialValue.length() + 6, 1), initialValue);
    textBoxPanel.addComponent(textBox);
    mainContentPanel.addComponent(textBoxPanel);

    mainContentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    buttonPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());
    buttonPanel.addComponent(new EmptySpace(new TerminalSize(PAD_SPACES, 0)));

    Button okButton = new Button("OK", () -> {
      if (this.callback != null) {
        this.callback.onInput(textBox.getText());
      }
      close();
    });
    buttonPanel.addComponent(okButton);

    //buttonPanel.addComponent(new EmptySpace(new TerminalSize(1, 0)));

    Button cancelButton = new Button("Cancel", () -> {
      if (this.callback != null) {
        this.callback.onCancel();
      }
      close();
    });
    buttonPanel.addComponent(cancelButton);
    mainContentPanel.addComponent(buttonPanel);

    mainContentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    setComponent(mainContentPanel);
  }

  public void showDialog(MultiWindowTextGUI textGUI) {
    textGUI.addWindow(this);
  }
}

