package com.example.lifecore.item.note;

import com.example.lifecore.item.ItemTemplate;

import java.time.format.DateTimeFormatter;

/**
 * Appearance and feedback settings of withdrawal notes (items.yml heart-note).
 */
public record HeartNoteSettings(ItemTemplate template, DateTimeFormatter dateFormat, String soundWithdraw,
                                String soundRedeem, String particleWithdraw, String particleRedeem) {
}
