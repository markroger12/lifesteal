package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * /lifesteal give &lt;player&gt; &lt;item&gt; [amount]
 * /lifesteal give &lt;player&gt; note &lt;hearts&gt;
 */
public final class GiveCommand extends SubCommand {

    public GiveCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "give";
    }

    @Override
    public String permission() {
        return "lifecore.admin.give";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            support.usage(sender, this, label);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "errors.player-offline", Placeholders.of("player", args[0]));
            return;
        }
        String reference = args[1].toLowerCase(java.util.Locale.ROOT);
        if (reference.equals("note")) {
            if (args.length < 3) {
                support.usage(sender, this, label);
                return;
            }
            OptionalDouble hearts = support.amount(sender, args[2], false);
            if (hearts.isEmpty()) {
                return;
            }
            String issuer = sender.getName();
            java.util.UUID issuerId = sender instanceof Player p ? p.getUniqueId() : new java.util.UUID(0, 0);
            ItemStack note = plugin.notes().createAdminNote(hearts.getAsDouble(), issuerId, issuer);
            deliver(sender, target, List.of(note), "note", 1);
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                amount = -1;
            }
            if (amount < 1 || amount > 2304) {
                plugin.messages().send(sender, "errors.invalid-number", Placeholders.of("input", args[2], "max", 2304));
                return;
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            Optional<ItemStack> item = plugin.items().create(reference, remaining);
            if (item.isEmpty()) {
                plugin.messages().send(sender, "items.unknown", Placeholders.of("id", args[1]));
                return;
            }
            stacks.add(item.get());
            remaining -= item.get().getAmount();
        }
        deliver(sender, target, stacks, reference, amount);
    }

    private void deliver(CommandSender sender, Player target, List<ItemStack> stacks, String reference, int amount) {
        plugin.scheduler().runAtEntity(target, () -> {
            for (ItemStack stack : stacks) {
                plugin.itemUse().giveOrDrop(target, stack);
            }
            plugin.messages().send(target, "items.received", Placeholders.of("item", reference, "amount", amount));
        });
        plugin.messages().send(sender, "admin.item-given", Placeholders.of("item", reference, "amount", amount, "player", target.getName()));
        plugin.log().info("admin", "Item given", "actor", sender.getName(), "target", target.getName(), "item", reference, "amount", amount);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return support.onlineNames(sender, args[0]);
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<>(plugin.items().giveableIds());
            ids.add("note");
            return CommandSupport.filter(ids, args[1]);
        }
        if (args.length == 3) {
            return args[1].equalsIgnoreCase("note") ? support.amounts(args[2]) : CommandSupport.filter(List.of("1", "8", "16", "64"), args[2]);
        }
        return List.of();
    }
}
