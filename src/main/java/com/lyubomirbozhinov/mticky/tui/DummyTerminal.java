package com.lyubomirbozhinov.mticky.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DummyTerminal implements Terminal {

    @Override
    public void enterPrivateMode() throws IOException {
        // No-op
    }

    @Override
    public void exitPrivateMode() throws IOException {
        // No-op
    }

    @Override
    public void clearScreen() throws IOException {
        // No-op
    }

    @Override
    public void setCursorPosition(int x, int y) throws IOException {
        // No-op
    }

    @Override
    public void setCursorPosition(TerminalPosition position) throws IOException {
        // No-op
    }

    @Override
    public TerminalPosition getCursorPosition() throws IOException {
        return new TerminalPosition(0, 0); // Default position
    }

    @Override
    public void setCursorVisible(boolean visible) throws IOException {
        // No-op
    }

    @Override
    public void putCharacter(char c) throws IOException {
        // No-op
    }

    @Override
    public void putString(String string) throws IOException {
        // No-op
    }

    @Override
    public TextGraphics newTextGraphics() throws IOException {
        return null; // Or a mock TextGraphics implementation
    }

    @Override
    public void enableSGR(SGR sgr) throws IOException {
        // No-op
    }

    @Override
    public void disableSGR(SGR sgr) throws IOException {
        // No-op
    }

    @Override
    public void resetColorAndSGR() throws IOException {
        // No-op
    }

    @Override
    public void setForegroundColor(TextColor color) throws IOException {
        // No-op
    }

    @Override
    public void setBackgroundColor(TextColor color) throws IOException {
        // No-op
    }

    @Override
    public void addResizeListener(TerminalResizeListener listener) {
        // No-op
    }

    @Override
    public void removeResizeListener(TerminalResizeListener listener) {
        // No-op
    }

    @Override
    public TerminalSize getTerminalSize() throws IOException {
        return new TerminalSize(80, 24); // Default size
    }

    @Override
    public byte[] enquireTerminal(int timeout, TimeUnit timeoutUnit) throws IOException {
        return new byte[0]; // No-op
    }

    @Override
    public void bell() throws IOException {
        // No-op
    }

    @Override
    public void flush() throws IOException {
        // No-op
    }

    @Override
    public void close() throws IOException {
        // No-op
    }

    @Override
    public KeyStroke pollInput() {
        return null; // No input
    }

    @Override
    public KeyStroke readInput() throws IOException {
        return null; // No input
    }

}

