package com.example.lifecore.util;

import com.example.lifecore.util.text.Placeholders;
import com.example.lifecore.util.text.TextUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUtilTest {

    @Test
    void translatesLegacyAndHexColours() {
        assertEquals("§aHello", TextUtil.colorize("&aHello"));
        assertEquals("§x§f§f§0§0§0§0Red", TextUtil.colorize("&#FF0000Red"));
        assertEquals("§x§0§0§f§f§0§0Green", TextUtil.colorize("<#00FF00>Green"));
        assertEquals("§x§0§0§0§0§f§fBlue", TextUtil.colorize("{#0000FF}Blue"));
    }

    @Test
    void appliesGradientsPreservingFormatting() {
        String result = TextUtil.colorize("<gradient:#FF0000:#0000FF>&lAB</gradient>");
        assertTrue(result.startsWith("§x§f§f§0§0§0§0§lA"), result);
        assertTrue(result.endsWith("§x§0§0§0§0§f§f§lB"), result);
        assertEquals("AB", TextUtil.strip(result));
    }

    @Test
    void centersChatLines() {
        String centered = TextUtil.formatChatLine("<center>&aHi");
        assertTrue(centered.startsWith(" "));
        assertEquals("Hi", TextUtil.strip(centered).trim());
    }

    @Test
    void replacesPlaceholdersSinglePass() {
        Placeholders ph = Placeholders.of("player", "{hearts}", "hearts", "10");
        assertEquals("{hearts} has 10", ph.apply("{player} has {hearts}"));
        assertEquals("{unknown}", ph.apply("{unknown}"));
    }
}
