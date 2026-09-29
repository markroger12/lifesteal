package com.example.lifecore.integration;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.HeartChangeReason;
import com.example.lifecore.api.LeaderboardType;
import com.example.lifecore.api.LifeCoreAPI;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.api.ReviveResult;
import com.example.lifecore.item.ItemIdentity;
import com.example.lifecore.manager.player.AdminActions;
import com.example.lifecore.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end tests running the real plugin on a MockBukkit server with a real SQLite database.
 */
class LifeCoreIntegrationTest {

    private ServerMock server;
    private LifeCorePlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(LifeCorePlugin.class);
        // Keep the general scenarios independent of the mock server's address assignment;
        // IP protection has its own dedicated test below.
        editConfig("config.yml", "anti-exploit.same-ip", false);
        editConfig("config.yml", "anti-exploit.prevent-alt-farming", false);
        plugin.reloadAll(server.getConsoleSender());
    }

    private void editConfig(String file, String path, Object value) throws Exception {
        java.io.File target = new java.io.File(plugin.getDataFolder(), file);
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target);
        yaml.set(path, value);
        yaml.save(target);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------ helpers

    private void await(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for " + what);
            }
            server.getScheduler().performOneTick();
            try {
                Thread.sleep(2);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail(ex);
            }
        }
    }

    /** Waits for a future while ticking the server (futures often complete via scheduled main-thread tasks). */
    private <T> T resolve(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        await("future", future::isDone);
        return future.get();
    }

    private PlayerMock join(String name) {
        PlayerMock player = server.addPlayer(name);
        await("data of " + name, () -> plugin.players().get(player) != null);
        server.getScheduler().performTicks(5);
        return player;
    }

    private PlayerData data(PlayerMock player) {
        PlayerData data = plugin.players().get(player);
        assertNotNull(data, "data of " + player.getName());
        return data;
    }

    private void setHearts(PlayerMock player, double hearts) throws Exception {
        resolve(plugin.admin().modifyHearts(player.getUniqueId(), AdminActions.Operation.SET, hearts, server.getConsoleSender(),
                HeartChangeReason.ADMIN));
        server.getScheduler().performTicks(2);
    }

    /**
     * Kills the victim the way a real server reports a PvP death: the last damage cause is a player
     * attack whose direct entity is the killer (MockBukkit derives getKiller() from it).
     */
    private void kill(PlayerMock victim, PlayerMock killer) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(killer).withDirectEntity(killer).build();
        victim.setLastDamageCause(new org.bukkit.event.entity.EntityDamageByEntityEvent(killer, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 1000));
        victim.setKiller(killer);
        victim.setHealth(0);
        server.getScheduler().performTicks(3);
    }

    /** A real server fires PlayerQuitEvent after a kick; MockBukkit's kick() does not, so fire it here. */
    private void completeKick(PlayerMock player) {
        await("kick of " + player.getName(), () -> !player.isOnline());
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(player,
                net.kyori.adventure.text.Component.empty(), org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED));
        server.getScheduler().performTicks(2);
    }

    private void flushDatabase() throws Exception {
        resolve(plugin.database().submit("barrier", storage -> Boolean.TRUE));
    }

    // ------------------------------------------------------------------ tests

    @Test
    void joinLoadsStartingHeartsAndAppliesMaxHealth() {
        PlayerMock player = join("Steve");
        assertEquals(10.0, data(player).getHearts());
        assertEquals(20.0, plugin.hearts().maxHealth(player));
        assertEquals(20.0, plugin.hearts().getMaxHearts(player));
    }

    @Test
    void playerKillTransfersHeartsAndRecordsStatistics() {
        PlayerMock killer = join("Killer");
        PlayerMock victim = join("Victim");
        kill(victim, killer);
        assertEquals(11.0, data(killer).getHearts());
        assertEquals(9.0, data(victim).getHearts());
        assertEquals(1, data(killer).getKills());
        assertEquals(1, data(victim).getDeaths());
        assertEquals("Killer", data(victim).getLastKiller());
        assertEquals(22.0, plugin.hearts().maxHealth(killer));
    }

    @Test
    void repeatedKillsOfTheSamePlayerAreNotRewarded() {
        PlayerMock killer = join("Farmer");
        PlayerMock victim = join("Alt");
        kill(victim, killer);
        victim.respawn();
        server.getScheduler().performTicks(40);
        kill(victim, killer);
        assertEquals(11.0, data(killer).getHearts(), "second kill within the delay gives nothing");
        assertEquals(9.0, data(victim).getHearts(), "victim keeps hearts when the kill is blocked");
        assertEquals(2, data(killer).getKills(), "statistics still count");
    }

    @Test
    void sameIpKillsAreNotRewarded() throws Exception {
        editConfig("config.yml", "anti-exploit.same-ip", true);
        plugin.reloadAll(server.getConsoleSender());
        PlayerMock killer = join("MainAccount");
        PlayerMock victim = join("AltAccount");
        plugin.antiExploit().ips().record(killer.getUniqueId(), "203.0.113.7");
        plugin.antiExploit().ips().record(victim.getUniqueId(), "203.0.113.7");
        kill(victim, killer);
        assertEquals(10.0, data(killer).getHearts(), "same-IP kill gives no hearts");
        assertEquals(10.0, data(victim).getHearts(), "and costs the victim nothing");
        assertEquals(1, data(killer).getKills());
    }

    @Test
    void altAccountsThatSharedAnIpAreNotRewarded() throws Exception {
        editConfig("config.yml", "anti-exploit.prevent-alt-farming", true);
        plugin.reloadAll(server.getConsoleSender());
        PlayerMock killer = join("Main2");
        PlayerMock victim = join("Alt2");
        plugin.antiExploit().ips().record(victim.getUniqueId(), "198.51.100.4");
        plugin.antiExploit().ips().record(killer.getUniqueId(), "198.51.100.4");
        plugin.antiExploit().ips().record(killer.getUniqueId(), "198.51.100.99");
        kill(victim, killer);
        assertEquals(10.0, data(killer).getHearts(), "accounts that recently shared an address are treated as alts");
    }

    @Test
    void deathRightAfterRespawnIsStillProcessed() {
        PlayerMock player = join("Unlucky");
        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 100));
        player.setHealth(0);
        server.getScheduler().performTicks(2);
        player.respawn();
        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 100));
        player.setHealth(0);
        server.getScheduler().performTicks(2);
        assertEquals(8.0, data(player).getHearts(), "both deaths cost a heart even within the duplicate window");
    }

    @Test
    void naturalDeathsUseTheConfiguredCauseLoss() {
        PlayerMock player = join("Swimmer");
        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.LAVA,
                DamageSource.builder(DamageType.LAVA).build(), 100));
        player.setHealth(0);
        server.getScheduler().performTicks(3);
        assertEquals(8.0, data(player).getHearts(), "lava costs 2 hearts by default");
        assertEquals("LAVA", data(player).getLastDeathCause());
    }

    @Test
    void duplicateDeathEventsAreProcessedOnce() {
        PlayerMock killer = join("Hunter");
        PlayerMock victim = join("Prey");
        kill(victim, killer);
        var event = new org.bukkit.event.entity.PlayerDeathEvent(victim, DamageSource.builder(DamageType.GENERIC).build(),
                new java.util.ArrayList<>(), 0, "duplicate");
        Bukkit.getPluginManager().callEvent(event);
        server.getScheduler().performTicks(3);
        assertEquals(9.0, data(victim).getHearts());
        assertEquals(1, data(victim).getDeaths());
    }

    @Test
    void reachingZeroHeartsEliminatesAndBans() throws Exception {
        PlayerMock killer = join("Champion");
        PlayerMock victim = join("Loser");
        setHearts(victim, 1);
        kill(victim, killer);
        PlayerData victimData = resolve(plugin.players().loadOffline(victim.getUniqueId())).orElseThrow();
        assertTrue(victimData.isEliminated());
        assertEquals(1, victimData.getEliminations());
        assertTrue(victimData.getBanExpiresAt() > System.currentTimeMillis() + 23L * 3_600_000L, "24h default ban");
        assertFalse(plugin.elimination().checkLogin(victimData, false).allowed(), "banned players cannot log in");
        assertTrue(plugin.elimination().checkLogin(victimData, false).kickMessage().contains("ELIMINATED"));
        await("eliminated player kicked", () -> !victim.isOnline());
    }

    @Test
    void expiredBanRevivesOnLogin() throws Exception {
        PlayerMock player = join("Returner");
        PlayerData data = data(player);
        data.eliminate(System.currentTimeMillis() - 10_000, "LAVA", "", System.currentTimeMillis() - 1);
        var decision = plugin.elimination().checkLogin(data, false);
        assertTrue(decision.allowed());
        assertTrue(decision.reviveOnJoin());
        plugin.revive().handleJoin(player, data, true);
        server.getScheduler().performTicks(2);
        assertFalse(data.isEliminated());
        assertEquals(3.0, data.getHearts());
    }

    @Test
    void adminCanReviveOfflinePlayers() throws Exception {
        PlayerMock player = join("Ghost");
        UUID uuid = player.getUniqueId();
        setHearts(player, 1);
        resolve(plugin.admin().eliminate(uuid, server.getConsoleSender()));
        completeKick(player);
        await("evicted", () -> plugin.players().get(uuid) == null);
        ReviveResult result = resolve(plugin.admin().revive(uuid, server.getConsoleSender(), -1));
        assertEquals(ReviveResult.SUCCESS, result);
        PlayerData stored = resolve(plugin.players().loadOffline(uuid)).orElseThrow();
        assertFalse(stored.isEliminated());
        assertEquals(3.0, stored.getHearts());
        assertTrue(stored.isPendingRevive(), "revive effects are applied on the next join");
        assertEquals(ReviveResult.NOT_ELIMINATED, resolve(plugin.admin().revive(uuid, server.getConsoleSender(), -1)));
    }

    @Test
    void withdrawAndRedeemNotesExactlyOnce() throws Exception {
        PlayerMock player = join("Banker");
        plugin.notes().withdraw(player, 2);
        assertEquals(8.0, data(player).getHearts());
        ItemStack note = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.items().identify(item).map(i -> i.type() == LifeCoreItemType.NOTE).orElse(false)) {
                note = item;
            }
        }
        assertNotNull(note, "a heart note was given");
        ItemStack copy = note.clone();
        player.getInventory().clear();
        player.getInventory().setItemInMainHand(note);
        plugin.notes().redeem(player, EquipmentSlot.HAND);
        await("redeem", () -> data(player).getHearts() == 10.0);
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());

        player.getInventory().setItemInMainHand(copy);
        plugin.notes().redeem(player, EquipmentSlot.HAND);
        flushDatabase();
        server.getScheduler().performTicks(5);
        assertEquals(10.0, data(player).getHearts(), "a duplicated note must not be redeemable twice");
    }

    @Test
    void withdrawRespectsMinimumRemainingHearts() throws Exception {
        PlayerMock player = join("Careful");
        setHearts(player, 3);
        plugin.notes().withdraw(player, 2);
        assertEquals(3.0, data(player).getHearts(), "must keep at least 2 hearts");
    }

    @Test
    void forgedItemsAreRejected() throws Exception {
        PlayerMock player = join("Forger");
        plugin.notes().withdraw(player, 1);
        ItemStack note = plugin.notes().createAdminNote(1, player.getUniqueId(), player.getName());
        ItemMeta meta = note.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.itemKeys().value, PersistentDataType.DOUBLE, 100.0);
        note.setItemMeta(meta);
        Optional<ItemIdentity> identity = plugin.items().identify(note);
        assertTrue(identity.isPresent());
        assertFalse(identity.get().valid(), "tampered values break the signature");
        double before = data(player).getHearts();
        player.getInventory().setItemInMainHand(note);
        plugin.notes().redeem(player, EquipmentSlot.HAND);
        server.getScheduler().performTicks(3);
        assertEquals(before, data(player).getHearts());
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir(), "forged items are confiscated");

        ItemStack plainPaper = new ItemStack(Material.PAPER);
        assertTrue(plugin.items().identify(plainPaper).isEmpty());
    }

    @Test
    void heartItemsGrantHeartsAndRespectTheCap() throws Exception {
        PlayerMock player = join("Eater");
        ItemStack heart = plugin.items().create("small_heart", 2).orElseThrow();
        player.getInventory().setItemInMainHand(heart);
        ItemIdentity identity = plugin.items().identify(heart).orElseThrow();
        assertTrue(identity.valid());
        plugin.heartItems().use(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand(), identity);
        assertEquals(11.0, data(player).getHearts());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());

        setHearts(player, 20);
        plugin.cooldowns().clear("heart-item", player.getUniqueId(), "small_heart");
        plugin.heartItems().use(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand(), identity);
        assertEquals(20.0, data(player).getHearts());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount(), "denied at max hearts - item kept");
    }

    @Test
    void headTextureTurnsHeartItemsIntoCustomHeads() throws Exception {
        String hash = "5a9c0f3e7b21d4c8a6e0f1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f6";
        editConfig("items.yml", "heart-items.small_heart.item.head-texture", "http://textures.minecraft.net/texture/" + hash);
        plugin.reloadAll(server.getConsoleSender());

        ItemStack first = plugin.items().create("small_heart", 1).orElseThrow();
        ItemStack second = plugin.items().create("small_heart", 1).orElseThrow();
        assertEquals(Material.PLAYER_HEAD, first.getType());
        com.destroystokyo.paper.profile.PlayerProfile profile = ((SkullMeta) first.getItemMeta()).getPlayerProfile();
        assertNotNull(profile);
        String value = profile.getProperties().stream().filter(p -> p.getName().equals("textures"))
                .map(com.destroystokyo.paper.profile.ProfileProperty::getValue).findFirst().orElseThrow();
        assertTrue(new String(java.util.Base64.getDecoder().decode(value)).contains(hash));
        assertTrue(first.isSimilar(second), "hearts made at different times still stack");

        PlayerMock player = join("HeadEater");
        player.getInventory().setItemInMainHand(first);
        ItemIdentity identity = plugin.items().identify(first).orElseThrow();
        assertTrue(identity.valid());
        plugin.heartItems().use(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand(), identity);
        assertEquals(11.0, data(player).getHearts());

        editConfig("items.yml", "heart-items.small_heart.item.head-texture", "not a texture");
        plugin.reloadAll(server.getConsoleSender());
        assertEquals(Material.RED_DYE, plugin.items().create("small_heart", 1).orElseThrow().getType(),
                "an unusable texture falls back to the configured material");
    }

    @Test
    void scrollsSacrificeHeartsForEffects() {
        PlayerMock player = join("Mystic");
        ItemStack scroll = plugin.items().create("sacrificial_scroll", 1).orElseThrow();
        player.getInventory().setItemInMainHand(scroll);
        plugin.scrolls().use(player, EquipmentSlot.HAND, scroll, plugin.items().identify(scroll).orElseThrow());
        assertEquals(8.0, data(player).getHearts());
        assertEquals(3, player.getActivePotionEffects().size());
    }

    @Test
    void adminCommandsAndTabCompletionWork() {
        PlayerMock admin = join("Admin");
        admin.setOp(true);
        PlayerMock target = join("Target");
        server.getScheduler().performTicks(20);
        admin.performCommand("lifesteal setheart Target 15");
        await("setheart", () -> data(target).getHearts() == 15.0);
        admin.performCommand("ls addheart Target 2.5");
        await("addheart", () -> data(target).getHearts() == 17.5);
        List<String> subCommands = server.getCommandTabComplete(admin, "lifesteal ");
        assertTrue(subCommands.contains("setheart"));
        assertTrue(subCommands.contains("reload"));
        List<String> names = server.getCommandTabComplete(admin, "lifesteal setheart Ta");
        assertTrue(names.contains("Target"));

        PlayerMock regular = join("Regular");
        List<String> visible = server.getCommandTabComplete(regular, "lifesteal ");
        assertFalse(visible.contains("setheart"), "players only see commands they may use");
        assertTrue(visible.contains("withdraw"));
    }

    @Test
    void invalidCommandInputIsRejected() {
        PlayerMock admin = join("Strict");
        admin.setOp(true);
        PlayerMock target = join("Victim2");
        admin.performCommand("lifesteal setheart Victim2 NaN");
        admin.performCommand("lifesteal setheart Victim2 -5");
        admin.performCommand("lifesteal setheart Victim2 1e9");
        server.getScheduler().performTicks(10);
        assertEquals(10.0, data(target).getHearts());
    }

    @Test
    void dataPersistsAcrossReconnects() throws Exception {
        PlayerMock player = join("Loyal");
        UUID uuid = player.getUniqueId();
        setHearts(player, 14.5);
        data(player).addKill();
        player.disconnect();
        server.getScheduler().performTicks(2);
        flushDatabase();
        assertTrue(plugin.players().get(uuid) == null, "evicted on quit");
        PlayerMock again = new PlayerMock(server, "Loyal", uuid);
        server.addPlayer(again);
        await("reload data", () -> plugin.players().get(uuid) != null);
        assertEquals(14.5, plugin.players().get(uuid).getHearts());
        assertEquals(1, plugin.players().get(uuid).getKills());
    }

    @Test
    void publicApiWorksForOnlineAndOfflinePlayers() throws Exception {
        PlayerMock player = join("ApiUser");
        UUID uuid = player.getUniqueId();
        assertTrue(LifeCoreAPI.isAvailable());
        assertEquals(10.0, LifeCoreAPI.getHearts(uuid));
        resolve(LifeCoreAPI.addHearts(uuid, 3));
        assertEquals(13.0, LifeCoreAPI.getHearts(uuid));
        resolve(LifeCoreAPI.removeHearts(uuid, 1));
        assertEquals(12.0, LifeCoreAPI.getHearts(uuid));
        assertFalse(LifeCoreAPI.isEliminated(uuid));
        assertTrue(LifeCoreAPI.getPlayerData(uuid).isPresent());
        assertEquals(-1, LifeCoreAPI.getHearts(UUID.randomUUID()));
    }

    @Test
    void leaderboardsRefreshFromDatabaseAndOnlinePlayers() throws Exception {
        PlayerMock a = join("Alpha");
        PlayerMock b = join("Bravo");
        setHearts(b, 18);
        plugin.leaderboards().refresh();
        await("leaderboard", () -> !plugin.leaderboards().get(LeaderboardType.HEARTS).isEmpty());
        assertEquals("Bravo", plugin.leaderboards().get(LeaderboardType.HEARTS).get(0).name());
        assertEquals(a.getName(), plugin.leaderboards().get(LeaderboardType.HEARTS).get(1).name());
        assertEquals("18", plugin.placeholders().resolve(b, "top_hearts_1_value"));
        assertEquals("18", plugin.placeholders().resolve(b, "hearts"));
    }

    @Test
    void reloadKeepsThePluginWorking() {
        PlayerMock player = join("Reloader");
        plugin.reloadAll(server.getConsoleSender());
        server.getScheduler().performTicks(5);
        assertEquals(10.0, data(player).getHearts());
        assertFalse(plugin.items().hearts().isEmpty());
        assertTrue(plugin.items().create("beacon:basic", 1).isPresent());
    }

    @Test
    void reviveBeaconRevivesTheTargetAfterTheCountdown() throws Exception {
        PlayerMock target = join("Fallen");
        UUID targetId = target.getUniqueId();
        setHearts(target, 1);
        resolve(plugin.admin().eliminate(targetId, server.getConsoleSender()));
        completeKick(target);
        flushDatabase();

        PlayerMock owner = join("Healer");
        ItemStack beaconItem = plugin.items().create("beacon:ultimate", 1).orElseThrow();
        owner.getInventory().setItemInMainHand(beaconItem);
        var world = owner.getWorld();
        var block = world.getBlockAt(0, 70, 0);
        block.setType(Material.AIR);
        var event = new org.bukkit.event.block.BlockPlaceEvent(block, block.getState(), world.getBlockAt(0, 69, 0),
                beaconItem, owner, true, EquipmentSlot.HAND);
        plugin.beacons().handlePlace(event, plugin.items().identify(beaconItem).orElseThrow());
        assertTrue(event.isCancelled(), "vanilla placement is replaced by LifeCore's");
        await("pending placement", () -> plugin.beacons().hasPending(owner.getUniqueId()));
        var eliminated = resolve(plugin.database().submit("list", s -> s.listEliminated(10)));
        assertEquals(1, eliminated.size());
        plugin.beacons().selectTarget(owner, eliminated.get(0));
        await("beacon active", () -> plugin.beacons().forTarget(targetId).isPresent());
        assertEquals(Material.BEACON, block.getType());

        server.getScheduler().performTicks(61 * 20);
        await("beacon completes and is removed", () -> plugin.beacons().active().isEmpty());
        flushDatabase();
        PlayerData revived = resolve(plugin.players().loadOffline(targetId)).orElseThrow();
        assertFalse(revived.isEliminated(), "the beacon revived the target");
        assertEquals(10.0, revived.getHearts(), "ultimate tier restores 10 hearts");
        assertEquals(Material.AIR, block.getType(), "the beacon block is removed");
        assertEquals(1, data(owner).getRevives());
    }

    @Test
    void eliminatedSpectatorsCannotUseGameplayCommands() throws Exception {
        PlayerMock player = join("Spectre");
        PlayerData data = data(player);
        data.eliminate(System.currentTimeMillis(), "ADMIN", "Console", 0);
        plugin.elimination().enforce(player, false);
        assertEquals(GameMode.SPECTATOR, player.getGameMode());
        assertTrue(plugin.elimination().isAllowedCommand("/msg friend hi"));
        assertFalse(plugin.elimination().isAllowedCommand("/home"));
        player.setGameMode(GameMode.SURVIVAL);
        assertEquals(GameMode.SPECTATOR, player.getGameMode(), "game mode stays locked");
    }
}
