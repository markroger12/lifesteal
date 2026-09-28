package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.LifeCoreCommand;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /lifesteal help [page] - lists commands the sender may use.
 */
public final class HelpCommand extends SubCommand {

    private static final int PER_PAGE = 10;
    private final LifeCoreCommand root;

    public HelpCommand(LifeCorePlugin plugin, CommandSupport support, LifeCoreCommand root) {
        super(plugin, support);
        this.root = root;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("?");
    }

    @Override
    public String permission() {
        return "lifecore.help";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        List<SubCommand> visible = new ArrayList<>();
        for (SubCommand sub : root.commands()) {
            if (sub.isVisible(sender)) {
                visible.add(sub);
            }
        }
        int pages = Math.max(1, (visible.size() + PER_PAGE - 1) / PER_PAGE);
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        plugin.messages().send(sender, "help.header", Placeholders.of("page", page, "pages", pages, "label", label));
        for (int i = (page - 1) * PER_PAGE; i < Math.min(visible.size(), page * PER_PAGE); i++) {
            SubCommand sub = visible.get(i);
            plugin.messages().send(sender, "help.entry", Placeholders.of(
                    "usage", plugin.messages().raw(sub.usageKey()).replace("{label}", label),
                    "description", plugin.messages().raw("help.descriptions." + sub.name()),
                    "label", label));
        }
        plugin.messages().send(sender, "help.footer", Placeholders.of("page", page, "pages", pages, "label", label)
                .add("next", Math.min(pages, page + 1)));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? CommandSupport.filter(List.of("1", "2", "3"), args[0]) : List.of();
    }
}
