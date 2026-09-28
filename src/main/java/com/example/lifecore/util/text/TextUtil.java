package com.example.lifecore.util.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour and formatting utilities producing legacy section-sign strings that work on
 * Spigot, Paper and Purpur alike.
 * <p>
 * Supported syntax:
 * <ul>
 *     <li>{@code &a}, {@code &l} ... legacy codes</li>
 *     <li>{@code &#RRGGBB}, {@code <#RRGGBB>}, {@code {#RRGGBB}} hex colours</li>
 *     <li>{@code <gradient:#RRGGBB:#RRGGBB[:#RRGGBB...]>text</gradient>}</li>
 *     <li>{@code <center>} at the start of a line centres the line in chat</li>
 * </ul>
 */
public final class TextUtil {

    public static final char SECTION = '§';
    public static final String CENTER_TAG = "<center>";

    private static final Pattern GRADIENT = Pattern.compile("<gradient:(#[0-9A-Fa-f]{6}(?::#[0-9A-Fa-f]{6})+)>(.*?)</gradient>");
    private static final Pattern HEX_AMP = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern HEX_TAG = Pattern.compile("<#([0-9A-Fa-f]{6})>");
    private static final Pattern HEX_BRACE = Pattern.compile("\\{#([0-9A-Fa-f]{6})}");
    private static final Pattern STRIP = Pattern.compile("(?i)" + SECTION + "[0-9A-FK-ORX]");
    private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final int CENTER_PX = 154;

    private TextUtil() {
    }

    /**
     * Translates all supported colour syntaxes into legacy section-sign formatting.
     */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = input;
        if (text.indexOf('<') >= 0) {
            text = applyGradients(text);
            text = replaceHex(HEX_TAG, text);
        }
        if (text.indexOf('#') >= 0) {
            text = replaceHex(HEX_AMP, text);
            text = replaceHex(HEX_BRACE, text);
        }
        return translateAmpersand(text);
    }

    public static List<String> colorize(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(colorize(line));
        }
        return out;
    }

    /**
     * Colourises and, when the line starts with {@code <center>}, centres it for chat.
     */
    public static String formatChatLine(String input) {
        if (input == null) {
            return "";
        }
        if (input.regionMatches(true, 0, CENTER_TAG, 0, CENTER_TAG.length())) {
            return center(colorize(input.substring(CENTER_TAG.length())));
        }
        return colorize(input);
    }

    /** Removes the {@code <center>} tag without centring (used outside of chat). */
    public static String stripCenterTag(String input) {
        if (input != null && input.regionMatches(true, 0, CENTER_TAG, 0, CENTER_TAG.length())) {
            return input.substring(CENTER_TAG.length());
        }
        return input;
    }

    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        return STRIP.matcher(input).replaceAll("");
    }

    public static String translateAmpersand(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && LEGACY_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static String hex(String rrggbb) {
        StringBuilder builder = new StringBuilder(14);
        builder.append(SECTION).append('x');
        for (char c : rrggbb.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append(SECTION).append(c);
        }
        return builder.toString();
    }

    public static String hex(int rgb) {
        return hex(String.format(Locale.ROOT, "%06x", rgb & 0xFFFFFF));
    }

    private static String replaceHex(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 32);
        do {
            matcher.appendReplacement(out, Matcher.quoteReplacement(hex(matcher.group(1))));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    private static String applyGradients(String text) {
        Matcher matcher = GRADIENT.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() * 4);
        do {
            String[] stops = matcher.group(1).split(":");
            int[] colors = new int[stops.length];
            for (int i = 0; i < stops.length; i++) {
                colors[i] = Integer.parseInt(stops[i].substring(1), 16);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(gradient(matcher.group(2), colors)));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Applies a multi-stop gradient to the given text. Formatting codes inside the text
     * ({@code &l}, {@code &o}, ...) are preserved and re-applied after every colour.
     */
    public static String gradient(String text, int... colors) {
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == SECTION) && i + 1 < text.length() && isFormat(text.charAt(i + 1))) {
                i++;
                continue;
            }
            visible++;
        }
        if (visible == 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() * 16);
        StringBuilder formats = new StringBuilder();
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == SECTION) && i + 1 < text.length() && isFormat(text.charAt(i + 1))) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') {
                    formats.setLength(0);
                } else {
                    formats.append(SECTION).append(code);
                }
                i++;
                continue;
            }
            double progress = visible == 1 ? 0.0 : (double) index / (visible - 1);
            out.append(hex(interpolate(colors, progress))).append(formats).append(c);
            index++;
        }
        return out.toString();
    }

    private static boolean isFormat(char c) {
        return "KkLlMmNnOoRr".indexOf(c) > -1;
    }

    static int interpolate(int[] colors, double progress) {
        if (colors.length == 1) {
            return colors[0];
        }
        double scaled = progress * (colors.length - 1);
        int segment = Math.min((int) Math.floor(scaled), colors.length - 2);
        double local = scaled - segment;
        int from = colors[segment];
        int to = colors[segment + 1];
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * local);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * local);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * local);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Centres an already colourised line using the default Minecraft font widths.
     */
    public static String center(String colored) {
        if (colored == null || colored.isEmpty()) {
            return "";
        }
        int px = 0;
        boolean previousSection = false;
        boolean bold = false;
        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            if (c == SECTION) {
                previousSection = true;
                continue;
            }
            if (previousSection) {
                previousSection = false;
                char lower = Character.toLowerCase(c);
                if (lower == 'l') {
                    bold = true;
                } else if ("0123456789abcdefrx".indexOf(lower) > -1) {
                    bold = false;
                }
                continue;
            }
            px += FontWidths.width(c, bold) + 1;
        }
        int half = px / 2;
        int toCompensate = CENTER_PX - half;
        if (toCompensate <= 0) {
            return colored;
        }
        int spaceWidth = FontWidths.width(' ', false) + 1;
        StringBuilder builder = new StringBuilder(toCompensate / spaceWidth + colored.length());
        int compensated = 0;
        while (compensated < toCompensate) {
            builder.append(' ');
            compensated += spaceWidth;
        }
        return builder.append(colored).toString();
    }
}
