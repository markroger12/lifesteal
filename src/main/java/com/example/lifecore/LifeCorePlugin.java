package com.example.lifecore;

import com.example.lifecore.api.LifeCoreAPI;
import com.example.lifecore.api.LifeCoreServiceImpl;
import com.example.lifecore.command.DynamicCommandRegistrar;
import com.example.lifecore.command.LifeCoreCommand;
import com.example.lifecore.configuration.ConfigManager;
import com.example.lifecore.configuration.ConfigReader;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.configuration.settings.StorageSettings;
import com.example.lifecore.configuration.settings.WorldRulesRegistry;
import com.example.lifecore.database.Database;
import com.example.lifecore.hook.HookManager;
import com.example.lifecore.hook.hologram.HologramProvider;
import com.example.lifecore.item.ItemKeys;
import com.example.lifecore.item.ItemRegistry;
import com.example.lifecore.item.ItemSigner;
import com.example.lifecore.item.ItemUseSupport;
import com.example.lifecore.item.RecipeManager;
import com.example.lifecore.item.heart.HeartDropService;
import com.example.lifecore.item.heart.HeartItemService;
import com.example.lifecore.item.note.HeartNoteService;
import com.example.lifecore.item.scroll.ScrollService;
import com.example.lifecore.listener.BeaconListener;
import com.example.lifecore.listener.CombatListener;
import com.example.lifecore.listener.ConnectionListener;
import com.example.lifecore.listener.CraftingListener;
import com.example.lifecore.listener.DeathListener;
import com.example.lifecore.listener.EliminationListener;
import com.example.lifecore.listener.ItemListener;
import com.example.lifecore.listener.MenuListener;
import com.example.lifecore.manager.CooldownManager;
import com.example.lifecore.manager.MessageService;
import com.example.lifecore.manager.ParticleService;
import com.example.lifecore.manager.ResourcePackService;
import com.example.lifecore.manager.SoundService;
import com.example.lifecore.manager.antiexploit.AntiExploitManager;
import com.example.lifecore.manager.beacon.BeaconManager;
import com.example.lifecore.manager.combat.CombatManager;
import com.example.lifecore.manager.elimination.EliminationManager;
import com.example.lifecore.manager.heart.HeartCapResolver;
import com.example.lifecore.manager.heart.HeartManager;
import com.example.lifecore.manager.leaderboard.LeaderboardManager;
import com.example.lifecore.manager.lifesteal.LifestealService;
import com.example.lifecore.manager.player.AdminActions;
import com.example.lifecore.manager.player.PlayerDataManager;
import com.example.lifecore.manager.revive.ReviveManager;
import com.example.lifecore.menu.ChatInputManager;
import com.example.lifecore.menu.MenuManager;
import com.example.lifecore.model.PlayerData;
import com.example.lifecore.placeholder.PlaceholderResolver;
import com.example.lifecore.storage.StorageException;
import com.example.lifecore.storage.StorageFactory;
import com.example.lifecore.storage.StorageProvider;
import com.example.lifecore.task.AutoSaveTask;
import com.example.lifecore.task.MaintenanceTask;
import com.example.lifecore.util.LifeLogger;
import com.example.lifecore.util.compat.Compat;
import com.example.lifecore.util.scheduler.ScheduledTask;
import com.example.lifecore.util.scheduler.SchedulerFactory;
import com.example.lifecore.util.scheduler.TaskScheduler;
import com.example.lifecore.util.text.NumberFormatter;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * LifeCore - a complete Lifesteal SMP plugin.
 * <p>
 * This class wires the services together and owns their lifecycle. Every feature lives in its
 * own manager; nothing here contains gameplay logic.
 */
public class LifeCorePlugin extends JavaPlugin {

    private LifeLogger log;
    private TaskScheduler scheduler;
    private ConfigManager configs;
    private volatile LifeCoreSettings settings;
    private volatile WorldRulesRegistry worlds;
    private StorageSettings storageSettings;
    private Database database;
    private ItemKeys itemKeys;
    private MessageService messages;
    private SoundService sounds;
    private ParticleService particles;
    private PlayerDataManager players;
    private HeartCapResolver caps;
    private HeartManager hearts;
    private EliminationManager elimination;
    private ReviveManager revive;
    private LifestealService lifesteal;
    private AntiExploitManager antiExploit;
    private CombatManager combat;
    private CooldownManager cooldowns;
    private LeaderboardManager leaderboards;
    private ItemRegistry items;
    private ItemUseSupport itemUse;
    private RecipeManager recipes;
    private HeartItemService heartItems;
    private ScrollService scrolls;
    private HeartNoteService notes;
    private HeartDropService heartDrops;
    private BeaconManager beacons;
    private HologramProvider holograms;
    private MenuManager menus;
    private ChatInputManager chatInput;
    private PlaceholderResolver placeholders;
    private AdminActions admin;
    private HookManager hooks;
    private ResourcePackService resourcePack;
    private DynamicCommandRegistrar aliasRegistrar;
    private final List<ScheduledTask> tasks = new ArrayList<>();
    private boolean started;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        this.log = new LifeLogger(getLogger());
        this.scheduler = SchedulerFactory.create(this);
        this.itemKeys = new ItemKeys(this);
        try {
            enable();
        } catch (Exception ex) {
            log.error("LifeCore failed to start and will be disabled: " + ex.getMessage(), ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        started = true;
        log.info("LifeCore " + getDescription().getVersion() + " enabled in " + (System.currentTimeMillis() - start)
                + " ms (" + (scheduler.isFolia() ? "Folia" : Bukkit.getName()) + ", storage: " + database.storage().describe() + ").");
    }

    private void enable() throws IOException, StorageException {
        configs = new ConfigManager(getDataFolder(), this::getResource, log);
        ConfigManager.LoadResult loaded = configs.loadAll();
        loaded.brokenFiles().forEach(file -> log.error("[config] Using defaults for broken file " + file));
        List<String> problems = new ArrayList<>();
        applySettings(problems);
        StorageSettings storage = readStorageSettings(problems);
        this.storageSettings = storage;

        ItemSigner signer = ItemSigner.loadOrCreate(new File(getDataFolder(), "data/secret.key"));

        StorageProvider provider = StorageFactory.create(storage, getDataFolder(), log);
        provider.init();
        this.database = new Database(provider, log);

        this.sounds = new SoundService(log, scheduler);
        this.particles = new ParticleService(log);
        this.messages = new MessageService(log, scheduler, sounds);
        loadPresentation();

        this.cooldowns = new CooldownManager();
        this.antiExploit = new AntiExploitManager(this::settings, System::currentTimeMillis);
        this.combat = new CombatManager(this::settings, log, System::currentTimeMillis);
        this.players = new PlayerDataManager(database, scheduler, log, () -> settings().hearts.starting());
        this.caps = new HeartCapResolver(this::settings);
        this.hearts = new HeartManager(this);
        this.elimination = new EliminationManager(this);
        this.revive = new ReviveManager(this);
        this.lifesteal = new LifestealService(this);
        this.leaderboards = new LeaderboardManager(this);
        this.placeholders = new PlaceholderResolver(this);
        this.admin = new AdminActions(this);

        this.items = new ItemRegistry(this, itemKeys, signer);
        this.itemUse = new ItemUseSupport(this);
        this.recipes = new RecipeManager(this);
        this.heartItems = new HeartItemService(this);
        this.scrolls = new ScrollService(this);
        this.notes = new HeartNoteService(this);
        this.heartDrops = new HeartDropService(this);
        this.beacons = new BeaconManager(this);
        this.menus = new MenuManager(this);
        this.chatInput = new ChatInputManager(this);
        this.hooks = new HookManager(this);
        this.resourcePack = new ResourcePackService(this);
        loadContent(problems);
        reportProblems(problems, null);

        hooks.enableAll();
        this.holograms = hooks.createHologramProvider();

        registerListeners();
        registerCommands();
        LifeCoreAPI.register(new LifeCoreServiceImpl(this));

        startTasks();
        beacons.start();
        beacons.restore();
        leaderboards.start();
        resourcePack.start();
        adoptOnlinePlayers();
    }

    // ------------------------------------------------------------------ configuration

    private void applySettings(List<String> problems) {
        ConfigReader configReader = new ConfigReader(configs.get("config.yml"), "config.yml");
        LifeCoreSettings newSettings = new LifeCoreSettings(configReader);
        ConfigReader worldReader = new ConfigReader(configs.get("worlds.yml"), "worlds.yml");
        WorldRulesRegistry newWorlds = new WorldRulesRegistry(worldReader);
        problems.addAll(configReader.problems());
        problems.addAll(worldReader.problems());
        this.settings = newSettings;
        this.worlds = newWorlds;
        log.setDebug(newSettings.debug);
    }

    private StorageSettings readStorageSettings(List<String> problems) {
        ConfigReader reader = new ConfigReader(configs.get("storage.yml"), "storage.yml");
        StorageSettings storage = StorageSettings.read(reader);
        problems.addAll(reader.problems());
        return storage;
    }

    private void loadPresentation() {
        sounds.load(configs.get("sounds.yml"));
        particles.load(configs.get("effects.yml"));
        messages.load(configs.get("messages.yml"), configs.defaults("messages.yml"), new NumberFormatter(settings.numberLocale));
    }

    private void loadContent(List<String> problems) {
        problems.addAll(items.load(configs.get("items.yml"), configs.get("effects.yml"), configs.get("beacons.yml")));
        problems.addAll(recipes.registerAll());
        beacons.load(configs.get("beacons.yml"));
        problems.addAll(menus.load(configs.get("menus.yml"), configs.defaults("menus.yml")));
    }

    private void reportProblems(List<String> problems, CommandSender sender) {
        if (problems.isEmpty()) {
            return;
        }
        log.warn("[config] " + problems.size() + " configuration problem(s) found (invalid values were replaced by safe defaults):");
        for (String problem : problems) {
            log.warn("[config]   - " + problem);
        }
        if (sender != null && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            messages.send(sender, "reload.warnings", Placeholders.of("count", problems.size()));
            for (int i = 0; i < Math.min(8, problems.size()); i++) {
                messages.send(sender, "reload.warning-entry", Placeholders.of("problem", problems.get(i)));
            }
        }
    }

    /**
     * Reloads every configuration file (except storage connection settings) without restarting
     * the plugin. If anything fails the previous configuration stays active.
     */
    public void reloadAll(CommandSender sender) {
        long start = System.currentTimeMillis();
        try {
            StorageSettings previousStorage = storageSettings;
            ConfigManager.LoadResult loaded = configs.loadAll();
            List<String> problems = new ArrayList<>();
            for (String broken : loaded.brokenFiles()) {
                problems.add(broken + " - using bundled defaults");
            }
            applySettings(problems);
            StorageSettings newStorage = readStorageSettings(problems);
            if (!newStorage.equals(previousStorage)) {
                problems.add("storage.yml changed - database settings are only applied after a restart");
            }
            loadPresentation();
            menus.closeAll();
            loadContent(problems);
            caps.invalidateAll();
            hooks.reload();
            beacons.start();
            leaderboards.start();
            resourcePack.start();
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runAtEntity(player, () -> {
                    PlayerData data = players.get(player);
                    if (data != null && !data.isEliminated() && settings.hearts.enforceCapOnJoin()) {
                        hearts.enforceCap(player);
                    } else {
                        hearts.applyAttribute(player);
                    }
                    if (!recipes.keys().isEmpty()) {
                        player.discoverRecipes(recipes.keys());
                    }
                });
            }
            reportProblems(problems, sender);
            messages.send(sender, "reload.success", Placeholders.of("time", System.currentTimeMillis() - start,
                    "warnings", problems.size()));
            log.info("[config] Reloaded by " + sender.getName() + " in " + (System.currentTimeMillis() - start) + " ms.");
        } catch (Exception ex) {
            log.error("Reload failed - the previous configuration is still active.", ex);
            messages.send(sender, "reload.failed", Placeholders.of("error", String.valueOf(ex.getMessage())));
        }
    }

    // ------------------------------------------------------------------ registration

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new EliminationListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new CraftingListener(this), this);
        pm.registerEvents(new BeaconListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
    }

    private void registerCommands() {
        LifeCoreCommand root = new LifeCoreCommand(this);
        PluginCommand command = getCommand("lifesteal");
        if (command == null) {
            throw new IllegalStateException("The 'lifesteal' command is missing from plugin.yml");
        }
        command.setExecutor(root);
        command.setTabCompleter(root);
        aliasRegistrar = new DynamicCommandRegistrar(this);
        aliasRegistrar.register(root, settings.standaloneAliases);
    }

    private void startTasks() {
        int autosave = storageSettings.autosaveIntervalSeconds();
        if (autosave > 0) {
            tasks.add(scheduler.runAsyncTimer(new AutoSaveTask(this), autosave * 20L, autosave * 20L));
        }
        tasks.add(scheduler.runAsyncTimer(new MaintenanceTask(this), 1200L, 1200L));
    }

    /** Loads data for players that are already online (plugin enabled at runtime). */
    private void adoptOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.loadAndCache(player.getUniqueId(), player.getName()).whenComplete((data, error) -> {
                if (error != null) {
                    log.warn("Could not load data for online player " + player.getName() + ": " + error.getMessage());
                    return;
                }
                scheduler.runAtEntity(player, () -> {
                    if (player.getAddress() != null) {
                        antiExploit.ips().record(player.getUniqueId(), player.getAddress().getAddress());
                    }
                    hearts.applyAttribute(player);
                    if (data.isEliminated()) {
                        elimination.enforce(player, false);
                    }
                });
            });
        }
    }

    @Override
    public void onDisable() {
        if (!started) {
            LifeCoreAPI.register(null);
            if (database != null) {
                database.shutdown(10);
            }
            return;
        }
        LifeCoreAPI.register(null);
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        leaderboards.stop();
        if (!scheduler.isFolia()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (com.example.lifecore.menu.Menu.openMenu(player) != null) {
                    player.closeInventory();
                }
            }
        }
        beacons.shutdown();
        if (holograms != null) {
            holograms.shutdown();
        }
        recipes.unregisterAll();
        if (aliasRegistrar != null) {
            aliasRegistrar.unregisterAll();
        }
        hooks.disableAll();
        resourcePack.stop();
        players.saveAllBlocking(10);
        database.shutdown(20);
        scheduler.cancelAll();
        log.info("LifeCore disabled - all data saved.");
    }

    // ------------------------------------------------------------------ accessors

    public LifeLogger log() {
        return log;
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    public LifeCoreSettings settings() {
        return settings;
    }

    public WorldRulesRegistry worlds() {
        return worlds;
    }

    public StorageSettings storageSettings() {
        return storageSettings;
    }

    public ConfigManager configs() {
        return configs;
    }

    public YamlConfiguration config(String file) {
        return configs.get(file);
    }

    public Database database() {
        return database;
    }

    public ItemKeys itemKeys() {
        return itemKeys;
    }

    public MessageService messages() {
        return messages;
    }

    public SoundService sounds() {
        return sounds;
    }

    public ParticleService particles() {
        return particles;
    }

    public PlayerDataManager players() {
        return players;
    }

    public HeartCapResolver caps() {
        return caps;
    }

    public HeartManager hearts() {
        return hearts;
    }

    public EliminationManager elimination() {
        return elimination;
    }

    public ReviveManager revive() {
        return revive;
    }

    public LifestealService lifesteal() {
        return lifesteal;
    }

    public AntiExploitManager antiExploit() {
        return antiExploit;
    }

    public CombatManager combat() {
        return combat;
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    public LeaderboardManager leaderboards() {
        return leaderboards;
    }

    public ItemRegistry items() {
        return items;
    }

    public ItemUseSupport itemUse() {
        return itemUse;
    }

    public RecipeManager recipes() {
        return recipes;
    }

    public HeartItemService heartItems() {
        return heartItems;
    }

    public ScrollService scrolls() {
        return scrolls;
    }

    public HeartNoteService notes() {
        return notes;
    }

    public HeartDropService heartDrops() {
        return heartDrops;
    }

    public BeaconManager beacons() {
        return beacons;
    }

    public HologramProvider holograms() {
        return holograms;
    }

    public MenuManager menus() {
        return menus;
    }

    public ChatInputManager chatInput() {
        return chatInput;
    }

    public PlaceholderResolver placeholders() {
        return placeholders;
    }

    public AdminActions admin() {
        return admin;
    }

    public HookManager hooks() {
        return hooks;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    public Attribute maxHealthAttribute() {
        return Compat.maxHealthAttribute();
    }
}
