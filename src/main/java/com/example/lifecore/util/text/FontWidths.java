package com.example.lifecore.util.text;

/**
 * Pixel widths of the default Minecraft font, used for chat centring.
 */
final class FontWidths {

    private FontWidths() {
    }

    static int width(char c, boolean bold) {
        int base = base(c);
        return bold && c != ' ' ? base + 1 : base;
    }

    private static int base(char c) {
        switch (c) {
            case 'i', '!', ',', '.', ':', ';', '|', '\'':
                return 1;
            case 'l', '`':
                return 2;
            case 'I', 't', '[', ']', ' ':
                return 3;
            case 'f', 'k', '"', '(', ')', '<', '>', '{', '}', '*':
                return 4;
            case '@', '~':
                return 6;
            default:
                return c > 0x7F ? 7 : 5;
        }
    }
}
