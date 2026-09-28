package com.example.lifecore.util.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Thread-safe formatting of heart values and other decimals ("10", "10.5", "1,250").
 */
public final class NumberFormatter {

    private final ThreadLocal<DecimalFormat> hearts;
    private final ThreadLocal<DecimalFormat> ratio;

    public NumberFormatter(Locale locale) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
        this.hearts = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.#", symbols));
        this.ratio = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00", symbols));
    }

    public String hearts(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return hearts.get().format(value);
    }

    public String ratio(double value) {
        if (!Double.isFinite(value)) {
            return "0.00";
        }
        return ratio.get().format(value);
    }

    public String integer(long value) {
        return hearts.get().format(value);
    }
}
