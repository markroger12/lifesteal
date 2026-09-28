package com.example.lifecore.manager.beacon;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.api.ReviveResult;
import com.example.lifecore.api.ReviveSource;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.event.ReviveBeaconCompleteEvent;
import com.example.lifecore.event.ReviveBeaconPlaceEvent;
import com.example.lifecore.hook.hologram.Hologram;
import com.example.lifecore.item.ItemIdentity;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.model.EliminatedPlayer;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.storage.ConsumeResult;
import com.example.lifecore.util.TimeUtil;
import com.example.lifecore.util.scheduler.ScheduledTask;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Revive beacon lifecycle: placement, target selection, countdown, defence, completion and recovery.
 * <p>
 * The countdown runs on the global thread once per second; everything that touches blocks,
 * holograms or nearby entities is dispatched to the thread owning the beacon's location.
 */
public final class BeaconManager {

    /**
     * Beacon settings from beacons.yml.
     */
    public record Settings(boolean enabled, int maxActivePerPlayer, boolean ownerCanCancel, boolean refundOnCancel,
                           boolean refundOnClose, boolean blockShiftCrafting, boolean requireOwnerNearby, int saveInterval,
                           int visualUpdateTicks, int tickSoundInterval, boolean hologramEnabled, double hologramHeight,
                           List<String> hologramLines, int barLength, String barSymbol, String barComplete,
                           String barRemaining, String colorHigh, String colorMedium, String colorLow) {
    }

    /** A beacon placed by a player who is still choosing the target. */
    public record PendingPlacement(UUID player, String tierId, Location location, ItemStack item, UUID itemId, long createdAt) {
    }

    private final LifeCorePlugin plugin;
    private final Map<UUID, ActiveBeacon> active = new ConcurrentHashMap<>();
    private final Map<ActiveBeacon.BlockKey, UUID> byBlock = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPlacement> pending = new ConcurrentHashMap<>();
    private final Set<ActiveBeacon.BlockKey> reserved = ConcurrentHashMap.newKeySet();
    private volatile Settings settings;
    private ScheduledTask countdownTask = ScheduledTask.NOOP;
    private ScheduledTask visualTask = ScheduledTask.NOOP;

    public BeaconManager(LifeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration config) {
        this.settings = new Settings(
                config.getBoolean("settings.enabled", true),
                Math.max(1, config.getInt("settings.max-active-per-player", 1)),
                config.getBoolean("settings.owner-can-cancel", true),
                config.getBoolean("settings.refund-on-cancel", true),
                config.getBoolean("settings.refund-on-close", true),
                config.getBoolean("settings.block-shift-crafting", true),
                config.getBoolean("settings.require-owner-nearby", true),
                Math.max(1, config.getInt("settings.save-interval", 10)),
                Math.max(5, Math.min(200, config.getInt("settings.visual-update-ticks", 20))),
                Math.max(0, config.getInt("settings.tick-sound-interval", 5)),
                config.getBoolean("hologram.enabled", true),
                config.getDouble("hologram.height", 2.4),
                List.copyOf(config.getStringList("hologram.lines")),
                Math.max(5, Math.min(60, config.getInt("hologram.progress-bar.length", 20))),
                config.getString("hologram.progress-bar.symbol", "|"),
                config.getString("hologram.progress-bar.complete-color", "&a"),
                config.getString("hologram.progress-bar.remaining-color", "&8"),
                config.getString("hologram.durability-colors.high", "&a"),
                config.getString("hologram.durability-colors.medium", "&e"),
                config.getString("hologram.durability-colors.low", "&c"));
    }

    public Settings settings() {
        return settings;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stopTasks();
        countdownTask = plugin.scheduler().runGlobalTimer(this::tickCountdown, 20L, 20L);
        visualTask = plugin.scheduler().runGlobalTimer(this::tickVisuals, 10L, settings.visualUpdateTicks());
    }

    private void stopTasks() {
        countdownTask.cancel();
        visualTask.cancel();
    }

    /** Loads persisted beacons and resumes them. */
    public void restore() {
        plugin.database().submit("load beacons", s -> s.loadBeacons()).whenComplete((records, error) -> {
            if (error != null) {
                plugin.log().error("[beacons] Could not load active beacons: " + error.getMessage());
                return;
            }
            plugin.scheduler().runGlobal(() -> {
                int restored = 0;
                for (var record : records) {
                    if (plugin.items().beacon(record.tier()).isEmpty()) {
                        plugin.log().warn("[beacons] Beacon " + record.id() + " uses removed tier '" + record.tier()
                                + "' - it was cancelled.");
                        plugin.database().execute("delete beacon", s -> s.deleteBeacon(record.id()));
                        continue;
                    }
                    if (Bukkit.getWorld(record.world()) == null) {
                        plugin.log().warn("[beacons] World '" + record.world() + "' of beacon " + record.id()
                                + " is not loaded - the beacon stays saved and resumes when the world exists at startup.");
                        continue;
                    }
                    ActiveBeacon beacon = ActiveBeacon.fromRecord(record);
                    register(beacon);
                    restored++;
                }
                if (restored > 0) {
                    plugin.log().info("[beacons] Resumed " + restored + " active revive beacon(s).");
                }
            });
        });
    }

    /** Persists every beacon and removes holograms. Blocks stay so beacons resume after a restart. */
    public void shutdown() {
        stopTasks();
        for (ActiveBeacon beacon : active.values()) {
            var record = beacon.toRecord();
            plugin.database().execute("save beacon", s -> s.saveBeacon(record));
            Hologram hologram = beacon.hologram();
            if (hologram != null) {
                try {
                    hologram.remove();
                } catch (RuntimeException ignored) {
                    // world already unloading
                }
            }
        }
        for (PendingPlacement placement : new ArrayList<>(pending.values())) {
            Player player = Bukkit.getPlayer(placement.player());
            if (player != null) {
                try {
                    player.getInventory().addItem(placement.item());
                } catch (RuntimeException ex) {
                    plugin.log().warn("[beacons] Could not return a pending beacon to " + player.getName() + " during shutdown: " + ex.getMessage());
                }
            }
        }
        pending.clear();
    }

    private void register(ActiveBeacon beacon) {
        active.put(beacon.id(), beacon);
        byBlock.put(beacon.blockKey(), beacon.id());
    }

    private void persist(ActiveBeacon beacon) {
        var record = beacon.toRecord();
        plugin.database().execute("save beacon " + beacon.id(), s -> s.saveBeacon(record));
    }

    // ------------------------------------------------------------------ queries

    public Collection<ActiveBeacon> active() {
        return Collections.unmodifiableCollection(active.values());
    }

    public Optional<ActiveBeacon> get(UUID id) {
        return Optional.ofNullable(active.get(id));
    }

    public Optional<ActiveBeacon> at(Block block) {
        UUID id = byBlock.get(ActiveBeacon.BlockKey.of(block.getLocation()));
        return id == null ? Optional.empty() : Optional.ofNullable(active.get(id));
    }

    public Optional<ActiveBeacon> forTarget(UUID target) {
        return active.values().stream().filter(b -> !b.isFinished() && b.target().equals(target)).findFirst();
    }

    public Set<UUID> targetedPlayers() {
        return active.values().stream().filter(b -> !b.isFinished()).map(ActiveBeacon::target).collect(Collectors.toSet());
    }

    public long countOwned(UUID owner) {
        return active.values().stream().filter(b -> !b.isFinished() && b.owner().equals(owner)).count();
    }

    public boolean hasPending(UUID player) {
        return pending.containsKey(player);
    }

    // ------------------------------------------------------------------ placement

    /**
     * Handles placement of a beacon item. The vanilla placement is always cancelled; the block is
     * placed by LifeCore once the player has chosen whom to revive.
     */
    public void handlePlace(BlockPlaceEvent event, ItemIdentity identity) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack inHand = event.getItemInHand();
        if (!identity.valid() || identity.uniqueId() == null) {
            plugin.itemUse().handleInvalid(player, hand, inHand, "forged revive beacon");
            return;
        }
        if (!settings.enabled()) {
            plugin.messages().send(player, "beacon.disabled");
            return;
        }
        if (!plugin.itemUse().preChecks(player, LifeCoreSettings.CombatAction.BEACON)) {
            return;
        }
        if (!plugin.worlds().get(player.getWorld()).beacons()) {
            plugin.messages().send(player, "beacon.disabled-world");
            plugin.sounds().play(player, "error");
            return;
        }
        Optional<BeaconTier> found = plugin.items().beacon(identity.id());
        if (found.isEmpty()) {
            plugin.messages().send(player, "items.unknown", Placeholders.of("id", identity.id()));
            return;
        }
        BeaconTier tier = found.get();
        if (!tier.permission().isEmpty() && !player.hasPermission(tier.permission())) {
            plugin.messages().send(player, "errors.no-permission");
            return;
        }
        PlayerData data = plugin.players().get(player);
        if (data == null || data.isEliminated()) {
            plugin.messages().send(player, "items.eliminated");
            return;
        }
        if (countOwned(player.getUniqueId()) >= settings.maxActivePerPlayer()) {
            plugin.messages().send(player, "beacon.max-active", Placeholders.of("max", settings.maxActivePerPlayer()));
            plugin.sounds().play(player, "error");
            return;
        }
        long cooldown = plugin.cooldowns().remaining("beacon", player.getUniqueId(), tier.id());
        if (cooldown > 0) {
            plugin.messages().send(player, "beacon.cooldown", Placeholders.of("time", plugin.messages().duration(cooldown)));
            plugin.sounds().play(player, "error");
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "beacon.already-selecting");
            return;
        }
        Location location = event.getBlockPlaced().getLocation();
        ActiveBeacon.BlockKey key = ActiveBeacon.BlockKey.of(location);
        if (byBlock.containsKey(key) || !reserved.add(key)) {
            plugin.messages().send(player, "beacon.location-occupied");
            return;
        }
        ItemStack single = inHand.clone();
        single.setAmount(1);
        if (!plugin.itemUse().consumeOne(player, hand, inHand)) {
            reserved.remove(key);
            return;
        }
        PendingPlacement placement = new PendingPlacement(player.getUniqueId(), tier.id(), location, single,
                identity.uniqueId(), System.currentTimeMillis());
        pending.put(player.getUniqueId(), placement);
        plugin.database().submit("list eliminated", s -> s.listEliminated(270)).whenComplete((list, error) ->
                plugin.scheduler().runAtEntity(player, () -> {
                    if (pending.get(player.getUniqueId()) != placement) {
                        return;
                    }
                    if (error != null) {
                        cancelPending(player, true, "errors.database-unavailable");
                        return;
                    }
                    Set<UUID> taken = targetedPlayers();
                    List<EliminatedPlayer> candidates = list.stream()
                            .filter(e -> !taken.contains(e.uuid()) && !e.uuid().equals(player.getUniqueId()))
                            .toList();
                    if (candidates.isEmpty()) {
                        cancelPending(player, true, "beacon.no-eliminated");
                        return;
                    }
                    plugin.menus().openBeaconTargets(player, tier, candidates);
                }));
    }

    /** Aborts a pending placement, optionally returning the item. */
    public void cancelPending(Player player, boolean refund, @Nullable String messageKey) {
        PendingPlacement placement = pending.remove(player.getUniqueId());
        if (placement == null) {
            return;
        }
        reserved.remove(ActiveBeacon.BlockKey.of(placement.location()));
        if (refund) {
            plugin.scheduler().runAtEntity(player, () -> plugin.itemUse().giveOrDrop(player, placement.item()));
        }
        if (messageKey != null) {
            plugin.messages().send(player, messageKey);
        }
    }

    /** Called when the target selection menu closes without a choice. */
    public void handleSelectionClosed(Player player) {
        cancelPending(player, settings.refundOnClose(), "beacon.selection-cancelled");
    }

    /** The player picked whom to revive: consume the beacon item id and start the ritual. */
    public void selectTarget(Player player, EliminatedPlayer target) {
        PendingPlacement placement = pending.remove(player.getUniqueId());
        if (placement == null) {
            return;
        }
        ActiveBeacon.BlockKey key = ActiveBeacon.BlockKey.of(placement.location());
        Optional<BeaconTier> tier = plugin.items().beacon(placement.tierId());
        if (tier.isEmpty() || forTarget(target.uuid()).isPresent()) {
            reserved.remove(key);
            plugin.itemUse().giveOrDrop(player, placement.item());
            plugin.messages().send(player, tier.isEmpty() ? "beacon.tier-removed" : "beacon.target-taken",
                    Placeholders.of("target", target.name()));
            return;
        }
        ReviveBeaconPlaceEvent event = new ReviveBeaconPlaceEvent(player, placement.location(), placement.tierId(), target.uuid(), target.name());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            reserved.remove(key);
            plugin.itemUse().giveOrDrop(player, placement.item());
            return;
        }
        UUID owner = player.getUniqueId();
        String ownerName = player.getName();
        plugin.database().submit("consume beacon " + placement.itemId(), s -> s.consumeItem(placement.itemId(),
                        LifeCoreItemType.BEACON.id(), 0, owner, System.currentTimeMillis()))
                .whenComplete((result, error) -> plugin.scheduler().runAtLocation(placement.location(), () -> {
                    reserved.remove(key);
                    Player online = Bukkit.getPlayer(owner);
                    if (error != null) {
                        refundOriginal(online, placement, "errors.database-unavailable");
                        return;
                    }
                    if (result == ConsumeResult.ALREADY_CONSUMED) {
                        plugin.log().warn("security", "Duplicate revive beacon placement", "player", ownerName, "item", placement.itemId());
                        plugin.messages().broadcastPermission("security.duplicate-beacon-alert",
                                Placeholders.of("player", ownerName, "item", placement.itemId()), "lifecore.notify");
                        if (online != null) {
                            plugin.messages().send(online, "beacon.duplicate");
                        }
                        return;
                    }
                    Block block = placement.location().getBlock();
                    if (!canPlaceInto(block) || byBlock.containsKey(key)) {
                        refundFresh(owner, tier.get(), placement.location(), "beacon.location-occupied");
                        return;
                    }
                    start(owner, ownerName, target, tier.get(), placement, block);
                }));
    }

    private boolean canPlaceInto(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return true;
        }
        try {
            return block.isReplaceable();
        } catch (LinkageError ignored) {
            return false;
        }
    }

    private void start(UUID owner, String ownerName, EliminatedPlayer target, BeaconTier tier, PendingPlacement placement, Block block) {
        block.setType(tier.block(), false);
        Location location = placement.location();
        ActiveBeacon beacon = new ActiveBeacon(UUID.randomUUID(), tier.id(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), owner, ownerName, target.uuid(),
                target.name(), System.currentTimeMillis(), placement.itemId(), tier.durationSeconds(), tier.durability(), tier.durability());
        register(beacon);
        persist(beacon);
        plugin.cooldowns().set("beacon", owner, tier.id(), tier.cooldownMillis());
        Location center = center(location);
        plugin.sounds().playAt(center, tier.soundStart());
        plugin.particles().play(tier.particle(), center);
        Placeholders ph = placeholders(beacon, tier);
        if (tier.broadcastStart()) {
            plugin.messages().broadcast("beacon.broadcast-start", ph);
        }
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            plugin.messages().send(player, "beacon.started", ph);
        }
        plugin.itemUse().runCommands(tier.commandsStart(), ph);
        updateVisuals(beacon, tier, false);
        plugin.log().info("beacons", "Revive beacon started", "owner", ownerName, "target", target.name(), "tier", tier.id(),
                "location", location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
    }

    // ------------------------------------------------------------------ ticking

    private void tickCountdown() {
        for (ActiveBeacon beacon : active.values()) {
            if (beacon.isFinished()) {
                continue;
            }
            Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
            if (tier.isEmpty()) {
                cancel(beacon, false, "beacon.tier-removed");
                continue;
            }
            boolean needsOwner = settings.requireOwnerNearby() && tier.get().activationRadius() > 0;
            int remaining = !needsOwner || beacon.isOwnerNearby() ? beacon.tickDown() : beacon.remainingSeconds();
            if (beacon.shouldSave(settings.saveInterval())) {
                persist(beacon);
            }
            if (remaining <= 0) {
                complete(beacon, tier.get());
            }
        }
    }

    private void tickVisuals() {
        for (ActiveBeacon beacon : active.values()) {
            if (beacon.isFinished()) {
                continue;
            }
            Location location = beacon.location();
            Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
            if (location == null || tier.isEmpty()) {
                continue;
            }
            boolean tickSound = beacon.shouldPlayTickSound(Math.max(1, settings.tickSoundInterval() * 20 / settings.visualUpdateTicks()))
                    && settings.tickSoundInterval() > 0;
            plugin.scheduler().runAtLocation(location, () -> updateVisuals(beacon, tier.get(), tickSound));
        }
    }

    /** Refreshes block, hologram, particles and the owner-nearby flag. Runs on the location's thread. */
    private void updateVisuals(ActiveBeacon beacon, BeaconTier tier, boolean tickSound) {
        if (beacon.isFinished()) {
            return;
        }
        Location location = beacon.location();
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        boolean needsOwner = settings.requireOwnerNearby() && tier.activationRadius() > 0;
        if (!world.isChunkLoaded(beacon.x() >> 4, beacon.z() >> 4)) {
            beacon.setOwnerNearby(!needsOwner);
            return;
        }
        Block block = location.getBlock();
        if (block.getType() != tier.block()) {
            block.setType(tier.block(), false);
        }
        Location center = center(location);
        if (needsOwner) {
            double r = tier.activationRadius();
            UUID owner = beacon.owner();
            beacon.setOwnerNearby(!world.getNearbyEntities(center, r, r, r, e -> e.getUniqueId().equals(owner)).isEmpty());
        } else {
            beacon.setOwnerNearby(true);
        }
        if (settings.hologramEnabled()) {
            List<String> lines = renderHologram(beacon, tier);
            Hologram hologram = beacon.hologram();
            try {
                if (hologram == null || !hologram.isValid()) {
                    Location holoLocation = center.clone().add(0, settings.hologramHeight(), 0);
                    beacon.setHologram(plugin.holograms().create(beacon.id().toString(), holoLocation, lines));
                } else {
                    hologram.setLines(lines);
                }
            } catch (RuntimeException ex) {
                plugin.log().warn("[beacons] Hologram update failed for beacon " + beacon.id() + ": " + ex.getMessage());
            }
        }
        plugin.particles().play(tier.particle(), center);
        if (tickSound) {
            plugin.sounds().playAt(center, tier.soundTick());
        }
    }

    private List<String> renderHologram(ActiveBeacon beacon, BeaconTier tier) {
        List<String> template = tier.hologramLines().isEmpty() ? settings.hologramLines() : tier.hologramLines();
        Placeholders ph = placeholders(beacon, tier);
        OfflineTarget viewer = new OfflineTarget(beacon.target());
        List<String> lines = new ArrayList<>(template.size());
        for (String line : template) {
            lines.add(plugin.messages().formatItemText(viewer.player(), line, ph));
        }
        return lines;
    }

    private record OfflineTarget(UUID uuid) {
        org.bukkit.OfflinePlayer player() {
            return Bukkit.getOfflinePlayer(uuid);
        }
    }

    public Placeholders placeholders(ActiveBeacon beacon, BeaconTier tier) {
        int percent = beacon.durabilityPercent();
        String durabilityColor = percent > 60 ? settings.colorHigh() : percent > 25 ? settings.colorMedium() : settings.colorLow();
        int total = Math.max(1, tier.durationSeconds());
        double progress = 1.0 - (double) beacon.remainingSeconds() / total;
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * settings.barLength());
        String bar = settings.barComplete() + settings.barSymbol().repeat(filled)
                + settings.barRemaining() + settings.barSymbol().repeat(settings.barLength() - filled);
        boolean needsOwner = settings.requireOwnerNearby() && tier.activationRadius() > 0;
        String status = needsOwner && !beacon.isOwnerNearby()
                ? plugin.messages().raw("beacon.status-paused") : plugin.messages().raw("beacon.status-active");
        Location location = beacon.location();
        return new Placeholders()
                .add("tier", tier.displayName())
                .add("target", beacon.targetName())
                .add("owner", beacon.ownerName())
                .add("time", TimeUtil.formatClock(beacon.remainingSeconds()))
                .add("durability", percent)
                .add("durability_color", durabilityColor)
                .add("progress_bar", bar)
                .add("status", status)
                .add("radius", plugin.messages().hearts(tier.activationRadius()))
                .add("world", beacon.worldName())
                .add("x", beacon.x())
                .add("y", beacon.y())
                .add("z", beacon.z())
                .add("id", beacon.id().toString().substring(0, 8))
                .add("location", location == null ? "?" : beacon.worldName() + " " + beacon.x() + " " + beacon.y() + " " + beacon.z());
    }

    private static Location center(Location block) {
        return block.clone().add(0.5, 0.0, 0.5);
    }

    // ------------------------------------------------------------------ outcomes

    private void complete(ActiveBeacon beacon, BeaconTier tier) {
        if (!beacon.finish()) {
            return;
        }
        Location location = beacon.location();
        Location eventLocation = location != null ? location : new Location(null, beacon.x(), beacon.y(), beacon.z());
        ReviveBeaconCompleteEvent event = new ReviveBeaconCompleteEvent(beacon.id(), eventLocation,
                tier.id(), beacon.owner(), beacon.target(), beacon.targetName());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            removeBeacon(beacon);
            notifyOwner(beacon, "beacon.cancelled", placeholders(beacon, tier));
            return;
        }
        plugin.revive().revive(beacon.target(), ReviveSource.BEACON, beacon.owner(), beacon.ownerName(), tier.reviveHearts())
                .thenAccept(result -> plugin.scheduler().runGlobal(() -> handleCompletion(beacon, tier, result)));
    }

    private void handleCompletion(ActiveBeacon beacon, BeaconTier tier, ReviveResult result) {
        Placeholders ph = placeholders(beacon, tier);
        switch (result) {
            case SUCCESS -> {
                Location location = beacon.location();
                if (location != null) {
                    plugin.scheduler().runAtLocation(location, () -> {
                        Location center = center(location);
                        plugin.particles().play("beacon-complete", center);
                        plugin.sounds().playAt(center, tier.soundComplete());
                    });
                }
                if (tier.broadcastComplete()) {
                    plugin.messages().broadcast("beacon.broadcast-complete", ph);
                } else {
                    notifyOwner(beacon, "beacon.completed", ph);
                }
                plugin.itemUse().runCommands(tier.commandsComplete(), ph);
                plugin.players().edit(beacon.owner(), data -> {
                    data.addRevivePerformed();
                    return Boolean.TRUE;
                });
                removeBeacon(beacon);
            }
            case ALREADY_IN_PROGRESS, ERROR -> beacon.unfinish();
            case NOT_ELIMINATED, NOT_FOUND -> {
                refundFresh(beacon.owner(), tier, beacon.location(), "beacon.target-not-eliminated");
                removeBeacon(beacon);
            }
            default -> {
                notifyOwner(beacon, "beacon.cancelled", ph);
                removeBeacon(beacon);
            }
        }
    }

    /** Applies damage to a beacon (enemy break attempt or explosion). */
    public void damage(ActiveBeacon beacon, double amount, @Nullable Player attacker) {
        Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
        if (tier.isEmpty() || beacon.isFinished() || amount <= 0) {
            return;
        }
        double left = beacon.damage(amount);
        Placeholders ph = placeholders(beacon, tier.get()).add("attacker", attacker == null
                ? plugin.messages().raw("beacon.explosion") : attacker.getName());
        Location location = beacon.location();
        if (location != null) {
            plugin.sounds().playAt(center(location), tier.get().soundDamaged());
        }
        notifyOwner(beacon, "beacon.damaged", ph);
        if (attacker != null) {
            plugin.messages().send(attacker, "beacon.damaged-attacker", ph);
        }
        if (left <= 0) {
            fail(beacon, tier.get());
        }
    }

    private void fail(ActiveBeacon beacon, BeaconTier tier) {
        if (!beacon.finish()) {
            return;
        }
        Placeholders ph = placeholders(beacon, tier);
        Location location = beacon.location();
        if (location != null) {
            Location center = center(location);
            plugin.particles().play("beacon-fail", center);
            plugin.sounds().playAt(center, tier.soundFail());
        }
        if (tier.broadcastFail()) {
            plugin.messages().broadcast("beacon.broadcast-fail", ph);
        } else {
            notifyOwner(beacon, "beacon.failed", ph);
        }
        plugin.itemUse().runCommands(tier.commandsFail(), ph);
        removeBeacon(beacon);
        plugin.log().info("beacons", "Revive beacon destroyed", "owner", beacon.ownerName(), "target", beacon.targetName());
    }

    /**
     * Cancels a beacon (owner break, admin action, removed tier).
     *
     * @return true if the beacon was cancelled by this call
     */
    public boolean cancel(ActiveBeacon beacon, boolean refund, String messageKey) {
        if (!beacon.finish()) {
            return false;
        }
        Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
        if (refund && tier.isPresent()) {
            refundFresh(beacon.owner(), tier.get(), beacon.location(), null);
        }
        notifyOwner(beacon, messageKey, tier.map(t -> placeholders(beacon, t)).orElse(Placeholders.of("target", beacon.targetName())));
        removeBeacon(beacon);
        return true;
    }

    /** Owner broke their own beacon. */
    public boolean handleOwnerBreak(ActiveBeacon beacon) {
        if (!settings.ownerCanCancel()) {
            return false;
        }
        return cancel(beacon, settings.refundOnCancel(), "beacon.owner-cancelled");
    }

    /** Called by the revive manager when a targeted player was revived by other means. */
    public void onTargetRevived(UUID target) {
        for (ActiveBeacon beacon : active.values()) {
            if (!beacon.isFinished() && beacon.target().equals(target)) {
                plugin.scheduler().runGlobal(() -> cancel(beacon, true, "beacon.target-revived-elsewhere"));
            }
        }
    }

    private void removeBeacon(ActiveBeacon beacon) {
        active.remove(beacon.id());
        byBlock.remove(beacon.blockKey(), beacon.id());
        plugin.database().execute("delete beacon " + beacon.id(), s -> s.deleteBeacon(beacon.id()));
        Location location = beacon.location();
        if (location == null) {
            return;
        }
        plugin.scheduler().runAtLocation(location, () -> {
            Hologram hologram = beacon.hologram();
            if (hologram != null) {
                hologram.remove();
                beacon.setHologram(null);
            }
            Optional<BeaconTier> tier = plugin.items().beacon(beacon.tierId());
            Block block = location.getBlock();
            Material expected = tier.map(BeaconTier::block).orElse(Material.BEACON);
            if (block.getType() == expected) {
                block.setType(Material.AIR, false);
            }
        });
    }

    private void refundOriginal(@Nullable Player player, PendingPlacement placement, String messageKey) {
        if (player != null) {
            plugin.scheduler().runAtEntity(player, () -> {
                plugin.itemUse().giveOrDrop(player, placement.item());
                plugin.messages().send(player, messageKey);
            });
        } else {
            World world = placement.location().getWorld();
            if (world != null) {
                world.dropItemNaturally(placement.location(), placement.item());
            }
        }
    }

    private void refundFresh(UUID owner, BeaconTier tier, @Nullable Location fallback, @Nullable String messageKey) {
        ItemStack item = plugin.items().createBeacon(tier);
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            plugin.scheduler().runAtEntity(player, () -> {
                plugin.itemUse().giveOrDrop(player, item);
                if (messageKey != null) {
                    plugin.messages().send(player, messageKey);
                }
            });
        } else if (fallback != null) {
            plugin.scheduler().runAtLocation(fallback, () -> {
                World world = fallback.getWorld();
                if (world != null) {
                    world.dropItemNaturally(center(fallback), item);
                }
            });
        }
    }

    private void notifyOwner(ActiveBeacon beacon, String key, Placeholders placeholders) {
        Player owner = Bukkit.getPlayer(beacon.owner());
        if (owner != null) {
            plugin.messages().send(owner, key, placeholders);
        }
    }

    /** Remaining revive time (seconds) for a target, or -1 if no beacon targets them. */
    public long reviveTimeFor(UUID target) {
        return forTarget(target).map(b -> (long) b.remainingSeconds()).orElse(-1L);
    }

    public Collection<ActiveBeacon> snapshot() {
        return List.copyOf(active.values());
    }
}
