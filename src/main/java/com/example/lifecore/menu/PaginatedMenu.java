package com.example.lifecore.menu;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Menu showing a list of entries across pages using the layout's content slots and the
 * {@code previous} / {@code next} / {@code empty} items.
 */
public abstract class PaginatedMenu<T> extends Menu {

    protected int page;

    protected PaginatedMenu(LifeCorePlugin plugin, Player viewer, MenuLayout layout, int page) {
        super(plugin, viewer, layout);
        this.page = Math.max(0, page);
    }

    protected abstract List<T> entries();

    protected abstract void placeEntry(int slot, T entry);

    /** Static item keys handled by the subclass (not placed automatically). */
    protected Set<String> specialItems() {
        return Set.of();
    }

    @Override
    protected Placeholders placeholders() {
        int pages = pages();
        return super.placeholders().add("page", page + 1).add("pages", pages);
    }

    protected int pages() {
        int perPage = Math.max(1, layout.contentSlots().size());
        return Math.max(1, (entries().size() + perPage - 1) / perPage);
    }

    @Override
    protected void build() {
        List<T> entries = entries();
        List<Integer> slots = layout.contentSlots();
        int perPage = Math.max(1, slots.size());
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        if (page >= pages) {
            page = pages - 1;
        }
        Placeholders ph = placeholders();
        java.util.Set<String> skip = new java.util.HashSet<>(specialItems());
        skip.add("previous");
        skip.add("next");
        skip.add("empty");
        placeAllStatic(ph, skip);
        if (entries.isEmpty()) {
            placeStatic("empty", ph, null, null);
        }
        int start = page * perPage;
        for (int i = 0; i < slots.size() && start + i < entries.size(); i++) {
            placeEntry(slots.get(i), entries.get(start + i));
        }
        if (page > 0) {
            placeStatic("previous", ph, null, click -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            placeStatic("next", ph, null, click -> {
                page++;
                refresh();
            });
        }
    }
}
