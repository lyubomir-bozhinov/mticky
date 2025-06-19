// This is the code you should be using for ThemedTextInputDialog.java
package com.lyubomirbozhinov.mticky.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Borders; // Import for borders
import com.googlecode.lanterna.gui2.EmptySpace; // Import for padding

import java.util.Arrays;

public class ThemedTextInputDialog extends BasicWindow {
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

        Panel contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        contentPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());

        contentPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        Label messageLabel = new Label(message);
        messageLabel.setForegroundColor(themeLoader.getMainForegroundColor());
        messageLabel.setBackgroundColor(themeLoader.getMainBackgroundColor());
        contentPanel.addComponent(messageLabel);
        
        contentPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        textBox = new TextBox(new TerminalSize(initialValue.length() + 5, 1), initialValue);
        // TextBox colors will be determined by the overall TextGUI theme.
        contentPanel.addComponent(textBox);

        contentPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        buttonPanel.setFillColorOverride(themeLoader.getMainBackgroundColor());

        Button okButton = new Button("OK", () -> {
            if (this.callback != null) {
                this.callback.onInput(textBox.getText());
            }
            close();
        });
        // Button colors are handled by the TextGUI's theme and ButtonRenderer
        okButton.setTheme(null); // Optional: Prevents default Lanterna theme from overriding *if* you set a custom theme for buttons
        buttonPanel.addComponent(okButton);
        
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));

        Button cancelButton = new Button("Cancel", () -> {
            if (this.callback != null) {
                this.callback.onCancel();
            }
            close();
        });
        // Button colors are handled by the TextGUI's theme and ButtonRenderer
        cancelButton.setTheme(null); // Optional: Prevents default Lanterna theme from overriding *if* you set a custom theme for buttons
        buttonPanel.addComponent(cancelButton);
        contentPanel.addComponent(buttonPanel);
        
        contentPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        setComponent(contentPanel); // This line is correct for BasicWindow
    }

    public void showDialog(MultiWindowTextGUI textGUI) {
        textGUI.addWindow(this);
    }
}

