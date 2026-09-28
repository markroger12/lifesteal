package com.example.lifecore.item;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.api.LifeCoreItemType;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.item.beacon.BeaconTier;
import com.example.lifecore.item.heart.HeartItemDefinition;
import com.example.lifecore.item.note.HeartNoteSettings;
import com.example.lifecore.item.scroll.ScrollDefinition;
import com.example.lifecore.util.compat.Compat;
import com.example.lifecore.util.text.Placeholders;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Loads item definitions and creates / identifies signed LifeCore items.
 */
public final class ItemRegistry {

    public static final int ITEM_VERSION = 1;
    public static final String NOTE_ID = "note";
    public static final String BEACON_PREFIX = "beacon:";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final LifeCorePlugin plugin;
    private final ItemKeys keys;
    private final ItemSigner signer;
    private volatile Map<String, HeartItemDefinition> hearts = Map.of();
    private volatile Map<String, ScrollDefinition> scrolls = Map.of();
    private volatile Map<String, BeaconTier> beacons = Map.of();
    private volatile HeartNoteSettings note;
    private volatile String effectLoreFormat = "&8» &d{effect} {level}";

    public ItemRegistry(LifeCorePlugin plugin, ItemKeys keys, ItemSigner signer) {
        this.plugin = plugin;
        this.keys = keys;
        this.signer = signer;
    }

    public ItemKeys keys() {
        return keys;
    }

    public ItemSigner signer() {
        return signer;
    }

    // ------------------------------------------------------------------ loading

    /**
     * Parses items.yml, effects.yml and beacons.yml.
     *
     * @return configuration problems (invalid entries are skipped or repaired)
     */
    public List<String> load(YamlConfiguration items, YamlConfiguration effects, YamlConfiguration beaconConfig) {
        List<String> problems = new ArrayList<>();
        Map<String, HeartItemDefinition> parsedHearts = new LinkedHashMap<>();
        ConfigurationSection heartSection = items.getConfigurationSection("heart-items");
        if (heartSection != null) {
            for (String id : heartSection.getKeys(false)) {
                ConfigurationSection s = heartSection.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) {
                    continue;
                }
                if (!ID.matcher(id).matches()) {
                    problems.add("items.yml -> heart-items." + id + ": ids may only contain a-z, 0-9 and _");
                    continue;
                }
                String path = "items.yml -> heart-items." + id;
                double value = s.getDouble("hearts", 1.0);
                if (!Double.isFinite(value) || value <= 0 || value > LifeCoreSettings.ABSOLUTE_MAX_HEARTS) {
                    problems.add(path + ".hearts: must be between 0 and 512, entry skipped");
                    continue;
                }
                ConfigurationSection drop = s.getConfigurationSection("drop");
                HeartItemDefinition.DropSpec dropSpec = drop == null || !drop.getBoolean("enabled", false)
                        ? HeartItemDefinition.DropSpec.DISABLED
                        : new HeartItemDefinition.DropSpec(true, clamp(drop.getDouble("chance", 0), 0, 100),
                        Math.max(1, Math.min(64, drop.getInt("amount", 1))),
                        parseEnum(LifeCoreSettings.DropBehavior.class, drop.getString("behavior", "GROUND"),
                                LifeCoreSettings.DropBehavior.GROUND, path + ".drop.behavior", problems));
                parsedHearts.put(id, new HeartItemDefinition(id, value,
                        Math.max(0, (long) (s.getDouble("cooldown", 0) * 1000L)),
                        s.getString("permission", ""),
                        parseEnum(HeartItemDefinition.AtMaxBehavior.class, s.getString("at-max-hearts", "DENY"),
                                HeartItemDefinition.AtMaxBehavior.DENY, path + ".at-max-hearts", problems),
                        Math.max(1, s.getInt("absorption-seconds", 120)),
                        List.copyOf(s.getStringList("at-max-commands")),
                        List.copyOf(s.getStringList("commands")),
                        s.getString("sound", "heart-consume"),
                        s.getString("particle", "heart-consume"),
                        ItemTemplate.parse(s.getConfigurationSection("item"), Material.RED_DYE, p -> problems.add(path + ".item: " + p)),
                        dropSpec,
                        RecipeSpec.parse(s.getConfigurationSection("recipe"))));
            }
        }

        Map<String, ScrollDefinition> parsedScrolls = new LinkedHashMap<>();
        ConfigurationSection scrollSection = effects.getConfigurationSection("scrolls");
        if (scrollSection != null) {
            for (String id : scrollSection.getKeys(false)) {
                ConfigurationSection s = scrollSection.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) {
                    continue;
                }
                String path = "effects.yml -> scrolls." + id;
                if (!ID.matcher(id).matches()) {
                    problems.add(path + ": ids may only contain a-z, 0-9 and _");
                    continue;
                }
                if (parsedHearts.containsKey(id)) {
                    problems.add(path + ": id already used by a heart item, entry skipped");
                    continue;
                }
                double cost = s.getDouble("heart-cost", 1.0);
                if (!Double.isFinite(cost) || cost < 0 || cost > LifeCoreSettings.ABSOLUTE_MAX_HEARTS) {
                    problems.add(path + ".heart-cost: invalid value, entry skipped");
                    continue;
                }
                List<ScrollDefinition.EffectSpec> effectSpecs = new ArrayList<>();
                for (String raw : s.getStringList("effects")) {
                    String[] parts = raw.split(":");
                    PotionEffectType type = Compat.potionEffect(parts[0]);
                    if (type == null) {
                        problems.add(path + ".effects: unknown potion effect '" + parts[0] + "'");
                        continue;
                    }
                    int level = 1;
                    if (parts.length > 1) {
                        try {
                            level = Math.max(1, Math.min(255, Integer.parseInt(parts[1].trim())));
                        } catch (NumberFormatException ex) {
                            problems.add(path + ".effects: invalid level in '" + raw + "'");
                        }
                    }
                    effectSpecs.add(new ScrollDefinition.EffectSpec(type, level, parts[0].trim()));
                }
                parsedScrolls.put(id, new ScrollDefinition(id, cost, Math.max(1, Math.min(86400, s.getInt("duration", 60))),
                        List.copyOf(effectSpecs), Math.max(0, (long) (s.getDouble("cooldown", 0) * 1000L)),
                        s.getString("permission", ""), s.getBoolean("allow-elimination", false),
                        s.getString("sound", "scroll-consume"), s.getString("particle", "scroll-consume"),
                        s.getString("messages.use", ""), List.copyOf(s.getStringList("commands")),
                        ItemTemplate.parse(s.getConfigurationSection("item"), Material.PAPER, p -> problems.add(path + ".item: " + p)),
                        RecipeSpec.parse(s.getConfigurationSection("recipe"))));
            }
        }
        this.effectLoreFormat = effects.getString("effect-lore-format", "&8» &d{effect} {level}");

        Map<String, BeaconTier> parsedBeacons = new LinkedHashMap<>();
        ConfigurationSection tierSection = beaconConfig.getConfigurationSection("tiers");
        if (tierSection != null) {
            for (String id : tierSection.getKeys(false)) {
                ConfigurationSection s = tierSection.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) {
                    continue;
                }
                String path = "beacons.yml -> tiers." + id;
                if (!ID.matcher(id).matches()) {
                    problems.add(path + ": ids may only contain a-z, 0-9 and _");
                    continue;
                }
                Material block = Material.matchMaterial(s.getString("block", "BEACON"));
                if (block == null || !block.isBlock() || block.isAir()) {
                    problems.add(path + ".block: not a placeable block, using BEACON");
                    block = Material.BEACON;
                }
                double durability = clamp(s.getDouble("durability", 100), 1, 1_000_000);
                parsedBeacons.put(id, new BeaconTier(id, s.getString("display-name", id), block,
                        Math.max(1, Math.min(86400, s.getInt("duration", 300))),
                        durability,
                        clamp(s.getDouble("damage-per-hit", 20), 0, durability),
                        clamp(s.getDouble("explosion-damage", 50), 0, durability),
                        Math.max(0, (long) (s.getDouble("cooldown", 0) * 1000L)),
                        clamp(s.getDouble("activation-radius", 16), 0, 256),
                        s.getDouble("revive-hearts", -1),
                        s.getString("permission", ""),
                        s.getString("particle", ""),
                        s.getString("sounds.start", "beacon-activate"),
                        s.getString("sounds.tick", "beacon-tick"),
                        s.getString("sounds.damaged", "beacon-damaged"),
                        s.getString("sounds.complete", "beacon-complete"),
                        s.getString("sounds.fail", "beacon-fail"),
                        List.copyOf(s.getStringList("commands.start")),
                        List.copyOf(s.getStringList("commands.complete")),
                        List.copyOf(s.getStringList("commands.fail")),
                        s.getBoolean("broadcast.start", true),
                        s.getBoolean("broadcast.complete", true),
                        s.getBoolean("broadcast.fail", true),
                        List.copyOf(s.getStringList("hologram.lines")),
                        ItemTemplate.parse(s.getConfigurationSection("item"), Material.BEACON, p -> problems.add(path + ".item: " + p)),
                        RecipeSpec.parse(s.getConfigurationSection("recipe"))));
            }
        }

        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(items.getString("heart-note.date-format", "yyyy-MM-dd HH:mm"))
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException ex) {
            problems.add("items.yml -> heart-note.date-format: invalid pattern, using yyyy-MM-dd HH:mm");
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        }
        HeartNoteSettings noteSettings = new HeartNoteSettings(
                ItemTemplate.parse(items.getConfigurationSection("heart-note.item"), Material.PAPER,
                        p -> problems.add("items.yml -> heart-note.item: " + p)),
                formatter,
                items.getString("heart-note.sound-withdraw", "withdraw"),
                items.getString("heart-note.sound-redeem", "redeem"),
                items.getString("heart-note.particle-withdraw", "withdraw"),
                items.getString("heart-note.particle-redeem", "redeem"));

        this.hearts = Collections.unmodifiableMap(parsedHearts);
        this.scrolls = Collections.unmodifiableMap(parsedScrolls);
        this.beacons = Collections.unmodifiableMap(parsedBeacons);
        this.note = noteSettings;
        return problems;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E def, String path, List<String> problems) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            problems.add(path + ": unknown value '" + value + "', using " + def.name());
            return def;
        }
    }

    // ------------------------------------------------------------------ lookups

    public Optional<HeartItemDefinition> heart(String id) {
        return Optional.ofNullable(id == null ? null : hearts.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<ScrollDefinition> scroll(String id) {
        return Optional.ofNullable(id == null ? null : scrolls.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<BeaconTier> beacon(String id) {
        return Optional.ofNullable(id == null ? null : beacons.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<HeartItemDefinition> hearts() {
        return hearts.values();
    }

    public Collection<ScrollDefinition> scrolls() {
        return scrolls.values();
    }

    public Collection<BeaconTier> beaconTiers() {
        return beacons.values();
    }

    public HeartNoteSettings noteSettings() {
        return note;
    }

    /** @return every id accepted by {@link #create(String, int)} (for tab completion). */
    public List<String> giveableIds() {
        List<String> ids = new ArrayList<>(hearts.keySet());
        ids.addAll(scrolls.keySet());
        for (String tier : beacons.keySet()) {
            ids.add(BEACON_PREFIX + tier);
        }
        return ids;
    }

    /** @return the material of a custom ingredient reference (without the lifecore: prefix). */
    public Optional<Material> materialOf(String reference) {
        String ref = reference.toLowerCase(Locale.ROOT);
        if (ref.startsWith(BEACON_PREFIX)) {
            return beacon(ref.substring(BEACON_PREFIX.length())).map(t -> t.template().material());
        }
        Optional<HeartItemDefinition> heart = heart(ref);
        if (heart.isPresent()) {
            return Optional.of(heart.get().template().material());
        }
        return scroll(ref).map(s -> s.template().material());
    }

    // ------------------------------------------------------------------ creation

    /** Creates a giveable item by reference ({@code small_heart}, {@code sacrificial_scroll}, {@code beacon:basic}). */
    public Optional<ItemStack> create(String reference, int amount) {
        String ref = reference.toLowerCase(Locale.ROOT);
        if (ref.startsWith("lifecore:")) {
            ref = ref.substring("lifecore:".length());
        }
        if (ref.startsWith(BEACON_PREFIX)) {
            return beacon(ref.substring(BEACON_PREFIX.length())).map(this::createBeacon);
        }
        Optional<HeartItemDefinition> heart = heart(ref);
        if (heart.isPresent()) {
            return Optional.of(createHeartItem(heart.get(), amount));
        }
        return scroll(ref).map(s -> createScroll(s, amount));
    }

    public ItemStack createHeartItem(HeartItemDefinition definition, int amount) {
        Placeholders ph = Placeholders.of("hearts", plugin.messages().hearts(definition.hearts()), "id", definition.id());
        ItemStack stack = definition.template().build(amount, raw -> plugin.messages().formatItemText(null, raw, ph), Map.of());
        return sign(stack, LifeCoreItemType.HEART, definition.id(), 0, null, null, 0);
    }

    public ItemStack createScroll(ScrollDefinition definition, int amount) {
        Placeholders ph = new Placeholders()
                .add("cost", plugin.messages().hearts(definition.heartCost()))
                .add("duration", definition.durationSeconds())
                .add("cooldown", plugin.messages().duration(definition.cooldownMillis()))
                .add("id", definition.id());
        List<String> effectLines = new ArrayList<>();
        for (ScrollDefinition.EffectSpec effect : definition.effects()) {
            Placeholders ep = Placeholders.of("effect", prettyEffect(effect), "level", roman(effect.level()));
            effectLines.add(plugin.messages().formatItemText(null, effectLoreFormat, ep));
        }
        ItemStack stack = definition.template().build(amount, raw -> plugin.messages().formatItemText(null, raw, ph),
                Map.of("{effects}", effectLines));
        return sign(stack, LifeCoreItemType.SCROLL, definition.id(), 0, null, null, 0);
    }

    public ItemStack createBeacon(BeaconTier tier) {
        Placeholders ph = new Placeholders()
                .add("duration", plugin.messages().duration(tier.durationSeconds() * 1000L))
                .add("durability", plugin.messages().hearts(tier.durability()))
                .add("tier", tier.displayName())
                .add("radius", plugin.messages().hearts(tier.activationRadius()));
        ItemStack stack = tier.template().build(1, raw -> plugin.messages().formatItemText(null, raw, ph), Map.of());
        return sign(stack, LifeCoreItemType.BEACON, tier.id(), 0, UUID.randomUUID(), null, System.currentTimeMillis());
    }

    /** A freshly created heart note and its unique id. */
    public record CreatedNote(ItemStack item, UUID uniqueId, long created) {
    }

    public CreatedNote createNote(double heartValue, UUID owner, String ownerName) {
        long created = System.currentTimeMillis();
        UUID uid = UUID.randomUUID();
        HeartNoteSettings settings = note;
        Placeholders ph = new Placeholders()
                .add("hearts", plugin.messages().hearts(heartValue))
                .add("owner", ownerName)
                .add("date", settings.dateFormat().format(Instant.ofEpochMilli(created)));
        ItemStack stack = settings.template().build(1, raw -> plugin.messages().formatItemText(null, raw, ph), Map.of());
        return new CreatedNote(sign(stack, LifeCoreItemType.NOTE, NOTE_ID, heartValue, uid, owner, created), uid, created);
    }

    private ItemStack sign(ItemStack stack, LifeCoreItemType type, String id, double value, @Nullable UUID uid,
                           @Nullable UUID owner, long created) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, type.id());
        pdc.set(keys.id, PersistentDataType.STRING, id);
        pdc.set(keys.version, PersistentDataType.INTEGER, ITEM_VERSION);
        if (value != 0) {
            pdc.set(keys.value, PersistentDataType.DOUBLE, value);
        }
        if (uid != null) {
            pdc.set(keys.uniqueId, PersistentDataType.STRING, uid.toString());
        }
        if (owner != null) {
            pdc.set(keys.owner, PersistentDataType.STRING, owner.toString());
        }
        if (created != 0) {
            pdc.set(keys.created, PersistentDataType.LONG, created);
        }
        String payload = ItemSigner.payload(ITEM_VERSION, type.id(), id, value, uid == null ? null : uid.toString(),
                owner == null ? null : owner.toString(), created);
        pdc.set(keys.signature, PersistentDataType.STRING, signer.sign(payload));
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------ identification

    public boolean isLifeCoreItem(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keys.type, PersistentDataType.STRING);
    }

    /**
     * Reads and verifies a LifeCore item. Never trusts client data: every field is type-checked
     * and the signature is recomputed from the stored values.
     */
    public Optional<ItemIdentity> identify(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String typeId = safeString(pdc, keys.type);
        if (typeId == null) {
            return Optional.empty();
        }
        LifeCoreItemType type = LifeCoreItemType.fromId(typeId).orElse(null);
        String id = safeString(pdc, keys.id);
        Integer version = safeInt(pdc, keys.version);
        Double value = safeDouble(pdc, keys.value);
        String uidText = safeString(pdc, keys.uniqueId);
        String ownerText = safeString(pdc, keys.owner);
        Long created = safeLong(pdc, keys.created);
        String signature = safeString(pdc, keys.signature);
        UUID uid = parseUuid(uidText);
        UUID owner = parseUuid(ownerText);
        double numeric = value == null ? 0 : value;
        boolean valid = type != null && id != null && version != null && signature != null
                && Double.isFinite(numeric) && numeric >= 0
                && (uidText == null || uid != null) && (ownerText == null || owner != null);
        if (valid && (type == LifeCoreItemType.NOTE || type == LifeCoreItemType.BEACON) && uid == null) {
            valid = false;
        }
        if (valid && plugin.settings().security.verifySignatures()) {
            String payload = ItemSigner.payload(version, typeId, id, numeric, uidText, ownerText, created == null ? 0 : created);
            valid = signer.verify(payload, signature);
        }
        return Optional.of(new ItemIdentity(type, id == null ? "" : id, numeric, uid, owner, created == null ? 0 : created, valid));
    }

    private static @Nullable String safeString(PersistentDataContainer pdc, org.bukkit.NamespacedKey key) {
        try {
            return pdc.has(key, PersistentDataType.STRING) ? pdc.get(key, PersistentDataType.STRING) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable Integer safeInt(PersistentDataContainer pdc, org.bukkit.NamespacedKey key) {
        try {
            return pdc.has(key, PersistentDataType.INTEGER) ? pdc.get(key, PersistentDataType.INTEGER) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable Double safeDouble(PersistentDataContainer pdc, org.bukkit.NamespacedKey key) {
        try {
            return pdc.has(key, PersistentDataType.DOUBLE) ? pdc.get(key, PersistentDataType.DOUBLE) : null;
        } catch (IllegalArgumentException ex) {
            return Double.NaN;
        }
    }

    private static @Nullable Long safeLong(PersistentDataContainer pdc, org.bukkit.NamespacedKey key) {
        try {
            return pdc.has(key, PersistentDataType.LONG) ? pdc.get(key, PersistentDataType.LONG) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable UUID parseUuid(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ display helpers

    public static String prettyEffect(ScrollDefinition.EffectSpec effect) {
        String key = effect.type() instanceof Keyed keyed ? keyed.getKey().getKey() : effect.name();
        String[] words = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    public static String roman(int level) {
        return level >= 0 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }
}
