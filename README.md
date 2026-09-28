# ❤ LifeCore — Lifesteal SMP

LifeCore is a complete Lifesteal SMP plugin for **Paper, Spigot and Purpur** (with Folia-aware scheduling).
Players steal hearts from the players they kill. Players who run out of hearts are eliminated and can be
revived by staff, by other players, or with defendable **Revive Beacons**. LifeCore also includes heart items,
sacrificial scrolls, heart withdrawal notes, GUIs, leaderboards, PlaceholderAPI placeholders, anti-exploit
protection, SQLite/MySQL storage and a developer API.

Install it, start the server, and a fully configured Lifesteal SMP is running. You don't have to write any code.

---

## Contents

1. [Requirements](#requirements)
2. [Installation](#installation)
3. [How it works](#how-it-works)
4. [Commands](#commands)
5. [Permissions](#permissions)
6. [Configuration files](#configuration-files)
7. [Hearts](#hearts)
8. [Kills & deaths](#kills--deaths)
9. [Elimination & temporary bans](#elimination--temporary-bans)
10. [Revival](#revival)
11. [Revive Beacons](#revive-beacons)
12. [Heart items, scrolls & heart notes](#heart-items-scrolls--heart-notes)
13. [Menus](#menus)
14. [Leaderboards](#leaderboards)
15. [PlaceholderAPI](#placeholderapi)
16. [Anti-exploit & security](#anti-exploit--security)
17. [Database (SQLite / MySQL / MariaDB)](#database)
18. [Integrations](#integrations)
19. [Resource pack](#resource-pack)
20. [Developer API](#developer-api)
21. [Configuration migration](#configuration-migration)
22. [Folia](#folia)
23. [Building from source](#building-from-source)
24. [Troubleshooting](#troubleshooting)
25. [FAQ](#faq)

---

## Requirements

| Requirement | Version |
|---|---|
| Server | Paper, Purpur or Spigot **1.21 – 1.21.11**, and **26.x** (Folia supported, see [Folia](#folia)) |
| Java | 21 or newer (Java 25 for 26.x servers, which runs the same jar) |
| Storage | SQLite is built in (no setup). MySQL 5.7+/8.x or MariaDB 10.4+ are optional. |
| Optional | PlaceholderAPI, LuckPerms, Vault (+ economy), CombatLogX, DeluxeCombat, PvPManager, DecentHolograms |

All integrations are **soft dependencies**: LifeCore starts and works without any of them.

## Installation

1. Stop the server.
2. Put `LifeCore-<version>.jar` into `plugins/`.
3. Start the server. LifeCore creates `plugins/LifeCore/` with all configuration files, the SQLite database
   (`data/lifecore.db`), the item-signing secret (`data/secret.key`) and the resource pack
   (`resourcepack/LifeCore-ResourcePack.zip`).
4. Optional: adjust the configuration and run `/lifesteal reload`.

> **Back up `data/secret.key`.** It signs every LifeCore item. If you lose it, existing heart items, notes and
> beacons become invalid. On a multi-server network that shares a MySQL database, copy the same
> `secret.key` to every server so items stay valid across servers.

## How it works

* Everyone starts with **10 hearts** (`hearts.starting`).
* Killing a player **steals 1 heart** (`kill.killer-gain` / `kill.victim-loss`).
* Dying in other ways costs hearts per cause (`death.causes`, e.g. lava 2, void 2, Ghast 3).
* Hearts are limited by a **maximum** (20 by default, higher with permission caps) and a **minimum**.
* A player who reaches **0 hearts is eliminated**. By default they get a **24 h temporary ban** (shorter for
  ranks), after which they are revived automatically with 3 hearts.
* Friends can revive them sooner by paying hearts (`/lifesteal revive`) or with a **Revive Beacon** they must defend.

---

## Commands

Main command: `/lifesteal` (aliases `/ls`, `/lifecore`). Without arguments it opens the menu (players) or help (console).
Every command has tab completion. It only suggests sub-commands you're allowed to use, plus player names,
eliminated players, item ids and sensible amounts. Heart amounts accept half hearts (`1.5`) when `hearts.half-hearts` is on.

### Player commands

| Command | Description | Permission |
|---|---|---|
| `/ls help [page]` | Commands available to you | `lifecore.help` |
| `/ls menu` | Player menu (GUI) | `lifecore.menu` |
| `/ls check [player] [gui]` | Hearts, stats, status, ban & revival info (chat or GUI) | `lifecore.check` (+ `lifecore.check.others`) |
| `/ls withdraw [hearts]` | Turn hearts into a signed heart note | `lifecore.withdraw` |
| `/ls redeem` | Redeem the heart note in your main hand | `lifecore.redeem` |
| `/ls revive [player]` | Revive an eliminated player (pays the configured cost). Without a name the revive menu opens | `lifecore.revive` |
| `/ls top [hearts\|kills\|deaths\|revives]` | Leaderboard in chat | `lifecore.top` |
| `/ls leaderboard [type]` | Leaderboard GUI | `lifecore.top` |
| `/ls resourcepack` | Download the LifeCore resource pack | `lifecore.resourcepack` |

Standalone aliases (configurable in `config.yml → commands.standalone-aliases`): `/withdraw`, `/redeem`.

### Admin commands

| Command | Description | Permission |
|---|---|---|
| `/ls admin [player]` | Admin GUI (optionally opens a player directly) | `lifecore.admin.menu` |
| `/ls setheart <player> <hearts>` | Set hearts (0 eliminates when elimination is on) | `lifecore.admin.sethearts` |
| `/ls addheart <player> <hearts>` | Add hearts (respects the player's cap) | `lifecore.admin.addhearts` |
| `/ls removeheart <player> <hearts>` | Remove hearts (can eliminate) | `lifecore.admin.removehearts` |
| `/ls setmaxhearts <player> <hearts\|reset>` | Per-player heart cap override | `lifecore.admin.setmax` |
| `/ls eliminate <player>` | Eliminate a player | `lifecore.admin.eliminate` |
| `/ls revive <player> [hearts]` | Free revive, optionally choosing the hearts restored | `lifecore.admin.revive` |
| `/ls reset <player>` | Starting hearts, clear elimination (and stats if configured) | `lifecore.admin.reset` |
| `/ls give <player> <item> [amount]` | Give `small_heart`, `sacrificial_scroll`, `beacon:basic`, … | `lifecore.admin.give` |
| `/ls give <player> note <hearts>` | Give a heart note of any value | `lifecore.admin.give` |
| `/ls beacon list\|menu\|cancel <id> [refund]` | Manage active revive beacons | `lifecore.admin.beacons` |
| `/ls reload` | Reload all configuration (not storage settings) | `lifecore.admin.reload` |
| `/ls info` | Version, platform, storage health, integrations | `lifecore.admin.info` |
| `/ls debug` | Toggle debug logging until the next reload | `lifecore.admin.debug` |

All admin commands work for **offline players** (anyone who ever joined) and from the **console**.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `lifecore.player` | everyone | Bundle of all player permissions below |
| `lifecore.command`, `.help`, `.menu`, `.check`, `.check.others`, `.withdraw`, `.redeem`, `.revive`, `.top`, `.resourcepack` | everyone | Player features |
| `lifecore.admin` | op | Bundle of all `lifecore.admin.*` permissions |
| `lifecore.notify` | op | Receive anti-exploit and security alerts |
| `lifecore.bypass.loss` | false | Never lose hearts |
| `lifecore.bypass.antiexploit` | false | Kill rewards are never blocked |
| `lifecore.bypass.elimination` | false | Cannot be eliminated; ignores eliminated-state restrictions |
| `lifecore.bypass.ban` | false | May join while elimination-banned (checked at login through LuckPerms) |
| `lifecore.bypass.combatlog` | false | Not killed for logging out in combat |
| `lifecore.bypass.cooldown` | false | Ignores the command rate limit |
| `lifecore.heartcap.vip` / `.mvp` | false | Named heart-cap tiers (`hearts.permission-caps.tiers`) |
| `lifecore.heartcap.<number>` | false | Numeric heart cap, e.g. `lifecore.heartcap.35` |
| `lifecore.gain.vip` | false | Heart-gain multiplier tier (`hearts.gain-multipliers`) |
| `lifecore.loss.reduced` | false | Heart-loss multiplier tier (`hearts.loss-multipliers`) |
| `lifecore.ban.vip` / `.mvp` | false | Shorter elimination bans (`elimination.ban.durations`) |

Items, scrolls, beacon tiers and recipes can each require their own permission (`permission:` keys).

---

## Configuration files

| File | Contents |
|---|---|
| `config.yml` | Hearts, kills, death causes, elimination & bans, revival, withdrawal, heart drops, anti-exploit, combat, leaderboards, security, resource pack, command aliases, integrations |
| `messages.yml` | Every message: chat, action bar, title/subtitle, boss bar, sounds; time units; heart-bar symbols |
| `items.yml` | Heart item tiers (small / large / legendary) and the heart note |
| `effects.yml` | Particle effects and sacrificial scroll tiers |
| `beacons.yml` | Revive beacon settings, hologram and tiers (basic / advanced / ultimate) |
| `worlds.yml` | Per-world rules |
| `sounds.yml` | Every sound (key, volume, pitch, on/off) |
| `menus.yml` | Layout of all ten GUIs |
| `storage.yml` | SQLite/MySQL/MariaDB, pool, autosave (restart required for changes) |

Invalid values never crash the plugin. They are replaced by safe defaults and every problem is listed in the
console (and summarised to the admin running `/ls reload`). A file with broken YAML syntax is left untouched,
and LifeCore uses the bundled defaults for it until you fix it.

### Text formatting

* Legacy colours `&a`, `&l`, …, hex `&#FF5555`, `<#FF5555>`, `{#FF5555}`
* Gradients `<gradient:#FF3B3B:#FF8A5B>text</gradient>` (any number of colours)
* `<center>` at the start of a chat line centres it
* `{placeholders}` shown in each file's comments, plus any PlaceholderAPI placeholder
* A message can be a string, a list, or a section with `chat`, `actionbar`, `title`, `subtitle`, `bossbar`, `sound`

---

## Hearts

```yaml
hearts:
  starting: 10.0
  minimum: 1.0          # lowest value a living player can have
  maximum: 20.0         # default cap
  hard-limit: 100.0     # absolute cap (Minecraft maximum: 512)
  half-hearts: true
  heal-on-gain: true
  enforce-cap-on-join: true
  permission-caps: ...   # named tiers + lifecore.heartcap.<n>
  gain-multipliers: ...  # highest applicable multiplier
  loss-multipliers: ...  # lowest applicable multiplier
```

Every heart change (kills, deaths, items, notes, commands, API) goes through one central `HeartManager`, which:
* rejects NaN/Infinity/negative values and rounds to half/whole hearts
* clamps between the minimum and the player's cap (permission caps and per-player overrides)
* fires the cancellable `PlayerHeartGainEvent` / `PlayerHeartLossEvent`
* triggers elimination when a loss reaches the elimination threshold
* updates the max-health attribute on the player's own thread

## Kills & deaths

When Player A kills Player B, LifeCore:
1. checks world rules (`worlds.yml`) and whether PvP heart transfer is enabled,
2. evaluates anti-exploit rules (same IP, alt accounts, repeated kills, rapid kills),
3. computes the victim's loss (per-cause, world overrides, loss multipliers, bypass),
4. computes the killer's gain (`FIXED` or `STOLEN`, gain multipliers, eliminated-killer rule),
5. fires the cancellable `PlayerLifestealEvent`,
6. applies the loss (possibly eliminating B) and the gain (capped; overflow can drop as heart items),
7. handles `reward-mode: ITEM` (hearts drop as items) and random heart drops,
8. records statistics and the kill for cooldown tracking,
9. sends messages (chat, action bar, titles), sounds and particles, and an optional broadcast,
10. saves both players immediately.

Each death is processed **exactly once**. Duplicate death events are ignored until the player respawns.

**Indirect kills:** a player knocked into lava/void or shot off a cliff credits the last attacker within
`kill.indirect-kills.window-seconds`.

**Natural deaths** use `death.causes` (per cause and per mob type, e.g. `BLAZE: 2`, `GHAST: 3`, falling back to
`ENTITY`), or `death.default-loss` when `heart-loss-by-death-cause` is false. Set a cause to `0` or list it in
`disabled-causes` to make it free.

### World rules (`worlds.yml`)

Each world can override: `enabled`, `safe` (no heart changes), `heart-loss`, `player-kills`, `heart-gain`,
`mob-loss`, `elimination`, `instant-elimination`, `heart-drops`, `items`, `beacons`, `killer-gain`,
`victim-loss` and `natural-loss-multiplier`. You can also list worlds under `disabled-worlds`, `safe-worlds`,
`no-heart-loss-worlds` and `instant-elimination-worlds`.

## Elimination & temporary bans

```yaml
elimination:
  enabled: true
  threshold: 0.0
  spectator: true
  kick: true
  ban:
    enabled: true
    allow-permanent: false
    durations:
      default: 24h
      vip: { permission: lifecore.ban.vip, duration: 12h }
      mvp: { permission: lifecore.ban.mvp, duration: 6h }
    on-expire: REVIVE          # or STAY_ELIMINATED
```

* Elimination stores the timestamp, the cause and killer, the heart count before elimination, and the ban expiry.
* Bans are stored in LifeCore's database and enforced at login, so they survive restarts. **Permanent bans are
  impossible unless `allow-permanent: true` is set and a duration is `permanent`.**
* The **shortest** duration the player qualifies for applies. For offline eliminations LuckPerms is used
  when it is installed.
* With `on-expire: REVIVE` the player is revived with `revive.hearts` on their next login.
* Without a ban, eliminated players can join as **spectators** (`spectator: true`). They can't use commands
  outside `allowed-commands`, can't teleport through the spectator menu, and their game mode is locked. If both
  bans and spectator mode are off, eliminated players can't join until they are revived.

## Revival

Sources: admins (`/ls revive <player> [hearts]`, admin GUI), players (`/ls revive <player>`, revive menu),
Revive Beacons, expired bans and the API. Revival:
* clears the elimination and ban, restores `revive.hearts` (or the beacon/admin amount),
* restores the configured game mode, optionally teleports to world spawn or bed, heals,
* works for offline players: the effects are applied on their next join,
* fires the cancellable `PlayerReviveEvent`, broadcasts, plays effects.

Player revives cost `revive.player-revive.cost-hearts` (default 2) and optionally money via Vault, have a
cooldown, keep the reviver above `minimum-remaining-hearts`, and ask for GUI confirmation.

## Revive Beacons

1. Craft or receive a beacon (`/ls give <player> beacon:basic`).
2. Place it. A menu lists eliminated players. Pick one, or close the menu to get the beacon back.
3. The ritual starts: a hologram shows the target, time remaining, durability and a progress bar.
4. Defend it. Enemy break attempts and explosions reduce durability. Pistons, mobs and fire can't move or remove it.
   The owner may break it to cancel (refund configurable).
5. With `require-owner-nearby`, the countdown pauses while the owner is outside the tier's `activation-radius`.
6. When the countdown ends, the target is revived (online or offline) and the beacon disappears.

Every tier can configure the item's appearance (material, name, lore, custom model data, item model, glow,
enchantments, flags), recipe, placed block, duration, durability, damage per hit, explosion damage, cooldown,
activation radius, revive hearts, permission, particles, sounds (start/tick/damaged/complete/fail), console
commands and broadcasts, and hologram lines.

Active beacons are stored in the database (the countdown is saved every `save-interval` seconds and on shutdown)
and **resume after a restart**. Holograms use DecentHolograms when it's installed; otherwise LifeCore uses
non-persistent `TextDisplay` entities, which a crash can never leave behind.

Every beacon has a unique id that is consumed in the database when it is used, so a duplicated beacon item can't
be placed twice.

## Heart items, scrolls & heart notes

**Heart items** (`items.yml → heart-items`): right-click to gain hearts. Each tier can set the hearts granted,
cooldown, permission, at-max behaviour (`DENY`, `CONSUME`, `ABSORPTION`, `COMMANDS`), console commands, sound,
particles, drop chance and behaviour, recipe and full item appearance. Recipes can use other LifeCore items as
ingredients (`lifecore:small_heart`, `lifecore:beacon:basic`). LifeCore checks that those ingredients are
genuine, signed items.

**Sacrificial scrolls** (`effects.yml → scrolls`): sacrifice hearts for temporary potion effects
(`"STRENGTH:2"` = Strength II). Each tier sets the heart cost, duration, effects, cooldown, permission,
whether it may eliminate, sound, particles, a custom message, commands, recipe and appearance.

**Heart notes** (`/ls withdraw`, `/ls redeem`): hearts are withdrawn into a note that stores its exact value,
a unique id, the owner and an HMAC signature in the item's PersistentDataContainer. Redeeming a note consumes its
id in the database before any hearts are granted, so each note can be redeemed only once. Notes that would exceed
your cap are partially redeemed, and a note for the remainder is returned (`redeem.partial-redeem`).

LifeCore items can't be eaten, placed (except beacons), used as dye, renamed, smelted, traded, or used in
vanilla recipes.

## Menus

`menus.yml` defines ten GUIs: player menu, revive menu, leaderboard, player lookup, beacon target selection,
confirmation, admin overview, admin player management, admin items and admin beacons. For each one you can
configure the title, rows, filler, slots, materials, names, lore, custom model data, glow, player heads,
per-item permissions and click actions:

```
[close]  [open] <menu>  [player] <command>  [console] <command>  [message] <text>
[sound] <sound id>  [withdraw] <hearts>  [redeem]  [refresh]  [top] <type>
```

## Leaderboards

Hearts, kills, deaths and revives. Leaderboards are refreshed asynchronously every `leaderboards.refresh-interval`
seconds. The database is merged with online players' live values, so it's accurate without waiting for a save.
`/ls top` (chat), `/ls leaderboard` (GUI), placeholders `%lifecore_top_<type>_<position>_name|value%`.

## PlaceholderAPI

| Placeholder | Value |
|---|---|
| `%lifecore_hearts%` | Current hearts (`10.5`) |
| `%lifecore_hearts_int%` / `%lifecore_hearts_raw%` | Whole hearts / raw number |
| `%lifecore_max_hearts%` / `%lifecore_min_hearts%` | Effective cap / minimum |
| `%lifecore_health%` | Current health in hearts |
| `%lifecore_kills%`, `%lifecore_deaths%`, `%lifecore_kdr%` | Combat statistics |
| `%lifecore_killstreak%`, `%lifecore_best_killstreak%` | Kill streaks |
| `%lifecore_revives%`, `%lifecore_times_revived%`, `%lifecore_eliminations%` | Revival statistics |
| `%lifecore_hearts_gained%`, `%lifecore_hearts_lost%` | Lifetime totals |
| `%lifecore_status%` / `%lifecore_status_raw%` | Alive / Eliminated / Banned |
| `%lifecore_eliminated%`, `%lifecore_banned%` | Yes/No |
| `%lifecore_ban_time%` | Remaining ban time |
| `%lifecore_revive_time%` | Remaining time of a beacon reviving this player |
| `%lifecore_hearts_bar%` | ❤❤❤❥♡ bar (symbols in messages.yml) |
| `%lifecore_last_death_cause%`, `%lifecore_last_killer%` | Last death |
| `%lifecore_in_combat%`, `%lifecore_combat_time%` | Combat tag |
| `%lifecore_top_<hearts\|kills\|deaths\|revives>_<n>_<name\|value\|uuid>%` | Leaderboards |

PlaceholderAPI placeholders also work inside every LifeCore message, menu item and hologram
(`integrations.parse-placeholders-in-messages`).

## Anti-exploit & security

| Protection | How |
|---|---|
| Same-IP farming | Kills between players on the same address give no hearts |
| Alt-account farming | Accounts that shared an address within `alt-window-hours` are treated as alts |
| Repeated kills | No reward for killing the same player within `repeated-kill-delay` seconds |
| Rapid kills | Max rewarded kills per time window |
| Death-event duplication | Each death is processed once; the guard resets on respawn |
| Combat logging | Tagged players who log out are killed and the attacker gets credit (or use your combat plugin) |
| Forged / edited items | Every item carries an HMAC-SHA256 signature over its type, id, value, unique id, owner and time. Items that fail verification are rejected and confiscated, and staff are alerted |
| Note / beacon duplication | Unique ids are atomically consumed in the database. Copies are worthless |
| Creative copying | LifeCore items can't be used in creative mode (except by admins) |
| Heart cap bypasses | All changes are clamped; caps are re-checked on join and on LuckPerms permission changes |
| Invalid numbers | NaN, Infinity, negatives, scientific notation and out-of-range values are rejected; counters saturate instead of overflowing |
| Command abuse | Per-player command rate limit; strict validation of names, UUIDs and amounts |

IP addresses are never stored. Only salted SHA-256 hashes are kept, and only in memory; they're discarded on restart.

---

## Database

SQLite is the default and needs no setup. All database work runs on one dedicated thread, never on the server
thread. Player data is loaded during the async login phase, cached while the player is online, saved
immediately after important changes (`autosave.save-on-change`), autosaved periodically in batches, flushed on
quit and flushed synchronously on shutdown.

If the database is unreachable, players who can't be loaded are refused with a friendly message
(`deny-login-on-load-failure`) instead of being given default hearts that would overwrite their real data.
Failed saves stay in memory and are retried.

### MySQL / MariaDB setup

```sql
CREATE DATABASE lifecore CHARACTER SET utf8mb4;
CREATE USER 'lifecore'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON lifecore.* TO 'lifecore'@'%';
```

```yaml
# storage.yml
type: MYSQL           # or MARIADB
table-prefix: "lifecore_"
mysql:
  host: "db.example.com"
  port: 3306
  database: "lifecore"
  username: "lifecore"
  password: "change-me"
```

Restart the server after changing `storage.yml`. Tables are created automatically (`<prefix>players`,
`<prefix>item_ledger`, `<prefix>beacons`, `<prefix>meta`). The MariaDB driver is used when it is on the server's
classpath; otherwise the MySQL driver that ships with the server is used.

## Integrations

| Plugin | What LifeCore does |
|---|---|
| PlaceholderAPI | Registers `%lifecore_*%`, parses placeholders in all LifeCore text |
| LuckPerms | Offline permission checks (ban durations, bypass at login); live heart-cap refresh when permissions change |
| Vault | Optional money cost for player revives (`revive.player-revive.money-cost`) |
| CombatLogX, DeluxeCombat, PvPManager | Their combat tags block configured actions (`combat.blocked-actions`) |
| DecentHolograms | Used for beacon holograms when present (`integrations.holograms: AUTO`) |

Each integration can be switched off in `config.yml → integrations`. If an integration's API changes or it
fails, LifeCore logs one warning and carries on without it.

## Resource pack

LifeCore ships an **original** resource pack with textures and models for the three heart tiers, three scrolls,
the heart note and three beacon tiers. It's exported to `plugins/LifeCore/resourcepack/LifeCore-ResourcePack.zip`.

* **1.21–1.21.3:** items use `custom-model-data` (7100xx), mapped by overrides in `assets/minecraft/models/item/*.json`.
* **1.21.4+:** the same custom model data is mapped by `assets/minecraft/items/*.json`. Alternatively set
  `item-model: "lifecore:small_heart"` on any item to use the item model definitions in `assets/lifecore/items/`.

**Installing:**
1. Upload the zip somewhere players can download it (e.g. your website or a file host) and set
   `resource-pack.url`. Or enable `resource-pack.host` to let LifeCore serve it itself (set `public-address`
   and open the port).
2. Set `resource-pack.send-on-join: true` (and `required: true` to force it). The SHA-1 is calculated
   automatically and logged at startup.
3. Or merge the zip into your server's existing pack.

To regenerate or tweak the art, edit and run `python3 tools/generate_resourcepack.py` (standard library only),
then rebuild.

---

## Developer API

The API lives in `com.example.lifecore.api` and `com.example.lifecore.event`. Add LifeCore as a
`compileOnly` dependency and `depend`/`softdepend: [LifeCore]`.

```java
import com.example.lifecore.api.LifeCoreAPI;

UUID id = player.getUniqueId();
double hearts = LifeCoreAPI.getHearts(id);                 // -1 if not loaded (offline)
double max = LifeCoreAPI.getMaxHearts(id);
boolean eliminated = LifeCoreAPI.isEliminated(id);

LifeCoreAPI.addHearts(id, 2).thenAccept(result -> {        // works for offline players too
    result.ifPresent(r -> getLogger().info(r.previous() + " -> " + r.current()));
});
LifeCoreAPI.removeHearts(id, 1);
LifeCoreAPI.setHearts(id, 15);
LifeCoreAPI.eliminate(id);
LifeCoreAPI.revive(id);                                    // or revive(id, hearts)
LifeCoreAPI.loadPlayerData(id).thenAccept(data -> ...);    // any player who ever joined
LifeCoreAPI.getPlayerData(id);                             // Optional<LifePlayerData> for online players
LifeCoreAPI.getLeaderboard(LeaderboardType.KILLS);
```

Futures complete on the server thread after the change has been applied (and queued for saving).

### Events

| Event | Cancellable | Notes |
|---|---|---|
| `PlayerHeartGainEvent` | ✔ | amount mutable, `HeartChangeReason` |
| `PlayerHeartLossEvent` | ✔ | amount mutable |
| `PlayerLifestealEvent` | ✔ | killer/victim, gain & loss mutable, anti-exploit block reason |
| `PlayerEliminationEvent` | ✔ | cause, killer, ban duration mutable |
| `PlayerReviveEvent` | ✔ | source, reviver, hearts mutable |
| `HeartItemConsumeEvent` | ✔ | heart items and scrolls (`LifeCoreItemType`) |
| `HeartWithdrawEvent` | ✔ | |
| `HeartRedeemEvent` | ✔ | note id |
| `ReviveBeaconPlaceEvent` | ✔ | fired after the target is chosen |
| `ReviveBeaconCompleteEvent` | ✔ | cancelling aborts the revival |

Events that can target offline players (heart, elimination, revive) expose the UUID and name, and
`getPlayer()` returns null for offline players.

---

## Configuration migration

Each file has a `config-version`. When an update ships a newer version, LifeCore:
1. writes a timestamped backup to `plugins/LifeCore/backups/`,
2. applies structural migrations (e.g. v1's flat `elimination.ban` / `elimination.ban-duration` keys become the
   `elimination.ban` section, `anti-exploit.same-ip-check` becomes `anti-exploit.same-ip`),
3. adds every new key, with its comment, from the bundled defaults without changing your values,
4. logs a summary of what changed.

Collections you manage yourself, such as item tiers, beacon tiers, world entries and death causes, are never
refilled, so a tier you deleted stays deleted. Files with a newer `config-version` than the plugin knows (after a
downgrade) are left as they are.

## Folia

LifeCore uses a scheduler abstraction with separate global, entity, location and async scheduling, and declares
`folia-supported: true`. On Folia:
* player work (attributes, kicks, game modes, inventories, messages) runs on the player's region scheduler,
* beacon blocks, holograms and particles run on the region that owns the beacon,
* the beacon countdown, leaderboards and admin actions on offline players run on the global region scheduler,
* teleports use `teleportAsync`.

The Folia code paths compile against the Folia scheduler API and follow its threading rules. The automated tests
run on a Paper-compatible mock server, so **check your specific Folia build on a staging server before production.**

## Building from source

```bash
./gradlew build          # compiles, runs all tests, produces build/libs/LifeCore-<version>.jar
```

Requires JDK 21. Dependencies come from Maven Central, the PaperMC repository and the PlaceholderAPI repository.

To rebrand, change `pluginName`, `pluginMain` and `group` in `gradle.properties` and move the
`com.example.lifecore` package.

### Tests

`./gradlew test` runs 119 automated tests:
* **Unit tests:** heart math and limits, gain/loss/elimination arithmetic, player-data state machine and
  sanitisation, durations, ban-duration resolution, anti-exploit rules (same IP, alts, repeated and rapid kills,
  duplicate deaths, rate limits), item signing and tamper detection, config migration (backups, renames,
  preserved values, broken YAML), coverage of default config keys and message keys, SQLite storage (upserts,
  leaderboards, item ledger, beacons, persistence), leaderboard merging, death causes, text formatting.
* **Integration tests on a MockBukkit server:** login loading, PvP heart transfer, repeated-kill and same-IP/alt
  blocking, natural deaths, duplicate death events, elimination and temporary bans, ban expiry revival, offline
  admin revival, withdraw/redeem with duplicate protection, forged item rejection, heart items and caps, scrolls,
  commands and tab completion, invalid input, persistence across reconnects, the public API, leaderboards,
  reload, and the complete revive beacon flow (placement → target selection → countdown → offline revival).

### Project structure

```
com.example.lifecore
├── LifeCorePlugin          wiring & lifecycle
├── api                     public API, enums, results
├── command(.subcommand)    /lifesteal and all sub-commands, dynamic aliases
├── configuration           loading, validation (ConfigReader), migration, typed settings
├── database                single-threaded async database executor
├── event                   custom Bukkit events
├── hook                    PlaceholderAPI, LuckPerms, combat plugins, Vault, holograms
├── item                    signing, templates, registry, recipes, heart/scroll/beacon/note items
├── listener                event listeners
├── manager                 hearts, players, elimination, revive, beacons, lifesteal, anti-exploit,
│                           combat, leaderboards, messages, sounds, particles, resource pack
├── menu(.player/.admin)    GUI framework and menus
├── model                   player data, snapshots, records
├── placeholder             placeholder resolution
├── storage                 SQLite / MySQL implementations
├── task                    autosave & maintenance
└── util                    text, time, scheduler (Bukkit/Folia), version compatibility
```

---

## Troubleshooting

| Problem | Solution |
|---|---|
| "Your data could not be loaded" on join | The database was unreachable. Check `storage.yml` and the console. With SQLite, make sure the disk isn't full |
| Items say "not a genuine LifeCore item" | `data/secret.key` changed or differs between servers. Restore the original key |
| Hearts don't change in a world | Check `worlds.yml` (`enabled`, `safe`, `player-kills`, `heart-loss`) |
| Kill gives no hearts | The console / `lifecore.notify` alerts show which anti-exploit rule blocked it (same IP, alt, repeated, rapid) |
| Players aren't unbanned | Bans are LifeCore's own (not `/pardon`). Use `/ls revive <player>` |
| Holograms missing | Holograms only appear in loaded chunks. DecentHolograms is used when installed; set `integrations.holograms: NATIVE` to force built-in holograms |
| Sounds or particles missing | The console lists unknown sound/particle names at startup/reload |
| Custom textures don't show | The pack isn't installed, or `custom-model-data` values changed. See [Resource pack](#resource-pack) |
| Need details | `general.debug: true` (or `/ls debug`) prints detailed diagnostics |

## FAQ

**Can eliminated players still play?** Only as spectators (if `elimination.spectator` is on and they aren't banned).

**Are bans permanent?** Never by default. A permanent ban requires `allow-permanent: true` and a `permanent` duration.

**What happens at maximum hearts?** Kills can drop the extra hearts as items (`kill.at-max-hearts: DROP_ITEM`).
Heart items follow their `at-max-hearts` setting.

**Can I disable heart gain or loss?** Yes. Use `kill.killer-gain: 0`, `kill.victim-loss: 0`, per-cause values, or
per-world `heart-gain` / `heart-loss` / `safe`.

**Does it work with my combat plugin?** Yes. CombatLogX, DeluxeCombat and PvPManager are detected automatically. Set
`combat.punish-combat-logging: false` if your combat plugin already kills combat loggers.

**Can I use my own item textures?** Set `custom-model-data` or `item-model` on any item, beacon or scroll.

**Can I rename the plugin?** Yes. See [Building from source](#building-from-source).
