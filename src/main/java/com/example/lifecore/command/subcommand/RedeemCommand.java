package com.example.lifecore.command.subcommand;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.command.CommandSupport;
import com.example.lifecore.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * /lifesteal redeem - redeems the heart note held in the main hand.
 */
public final class RedeemCommand extends SubCommand {

    public RedeemCommand(LifeCorePlugin plugin, CommandSupport support) {
        super(plugin, support);
    }

    @Override
    public String name() {
        return "redeem";
    }

    @Override
    public String permission() {
        return "lifecore.redeem";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.notes().redeem((Player) sender, EquipmentSlot.HAND);
    }
}
